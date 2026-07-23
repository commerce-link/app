#!/usr/bin/env python3
"""
Offline migration tool: taxonomy CSV (8-column) -> Icecat-enriched taxonomy CSV (9-column).

This is a STANDALONE deployment-day script. It is not part of the Java build and is
never invoked by the running application. It is meant to be run once by a human
operator, after the PIM V007 backfill has populated `icecatCategoryId` /
`productCategory` on PIM DynamoDB entries, and before the app is deployed with the
new taxonomy-loading code that expects a `category_id` column.

What it does
------------
1. Finds the newest `taxonomy/<YYYY-MM-DD>.csv` object in the datalake S3 bucket.
2. Downloads it and writes an untouched backup copy to
   `taxonomy-backup/<original-filename>-pre-icecat.csv` in the same bucket
   BEFORE any output is written (mandatory, even relevant in dry-run: the backup
   step is skipped in dry-run, but is logged as "would back up").
3. Scans the PIM DynamoDB table (paginated) and builds two lookup maps:
   ean -> (icecatCategoryId, productCategory)
   mfn -> (icecatCategoryId, productCategory)
   from entries that have a non-empty icecatCategoryId.
4. Rewrites every taxonomy row:
   - "Services" rows (case-insensitive) keep `category=Services`, `category_id=` empty,
     and are NOT looked up against PIM.
   - All other rows are matched first by ean, then by mfn. A match fills
     `category` (Icecat product category name) and `category_id` (Icecat category id).
     No match -> both fields are left empty; the app's LLM category-match loop is
     expected to self-heal these after deploy.
5. Writes the new 9-column CSV. In dry-run (the default) nothing is uploaded to S3;
   a local preview file is written instead. With --no-dry-run (or --apply) the result
   is uploaded as `taxonomy/<today's-date>.csv`, which sorts after the source file so
   the app's `findNewest`-style S3 listing picks it up.
6. Always prints summary statistics, including a rough one-off LLM cost estimate for
   the unmatched rows.

Safety
------
- --dry-run defaults to True. A real S3 write requires an explicit --no-dry-run
  (or --apply, an alias for the same thing).
- The backup step always runs before any overwrite in a real (non-dry-run) run, and
  the script fails closed (aborts) if the backup upload does not succeed.
- Running against a file that is already in the 9-column format is treated as an
  error ("already migrated") rather than silently re-processing it.

Assumptions about the PIM DynamoDB schema
------------------------------------------
These are all configurable via CLI flags (see --help), but the defaults encode a
specific assumption about the schema that MUST be confirmed by the deployment
operator against the actual production `PIM` table before this script is run for
real, in case the schema drifted between when this script was written and deploy
day:

- Identifiers are NOT flat `ean` / `mfn` top-level attributes. Per
  `pl.commercelink.pim.PIMIndexEntry` / `ProductIdentifier` (pim repo, branch
  `taxonomy-category-enrichment`, at the time this script was written), each PIM
  item stores a `productIdentifiers` list attribute, where each element is a map
  with a `value` attribute and a `type` attribute holding the Java enum name
  (`GTIN` for ean, `MfnCode` for mfn). Defaults:
    --pim-identifiers-attr        productIdentifiers
    --pim-identifier-value-attr   value
    --pim-identifier-type-attr    type
    --pim-ean-type-value          GTIN
    --pim-mfn-type-value          MfnCode
- Category fields are flat top-level attributes on the same item:
    --pim-category-attr           productCategory
    --pim-category-id-attr        icecatCategoryId
- ean/mfn values stored in PIM are normalized at write time via
  `UnifiedProductIdentifiers.unifyEan` / `unifyMfn` (strip a single leading zero
  from ean; trim/strip-spaces/uppercase for mfn). This script re-applies the same
  normalization to the taxonomy CSV's ean/mfn values before doing the lookup, so
  that formatting differences (leading zeros, casing, stray whitespace) don't
  cause false misses. If the source-of-truth normalization rules change, update
  `unify_ean` / `unify_mfn` below to match.

If any of the above no longer matches the real table on deploy day, override the
relevant --pim-* flag rather than editing this script.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import io
import sys
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple

try:
    import boto3
    from botocore.exceptions import ClientError
except ImportError:  # pragma: no cover - environment check, not exercised by tests
    print("ERROR: this script requires boto3 (`pip install boto3`).", file=sys.stderr)
    sys.exit(1)


OLD_COLUMNS = ["ean", "mfn", "brand", "name", "category",
               "data_accuracy_score", "net_weight_g", "gross_weight_g"]
NEW_COLUMNS = ["ean", "mfn", "brand", "name", "category", "category_id",
               "data_accuracy_score", "net_weight_g", "gross_weight_g"]

DEFAULT_SERVICES_VALUE = "Services"
CSV_DELIMITER = ";"

# Rough, order-of-magnitude cost estimate only (per the brief: "unmatched rows x 2
# Gemini queries, one-off"). Not meant to be an accurate billing forecast — just
# enough for the operator to sanity-check the blast radius of unmatched rows.
LLM_QUERIES_PER_UNMATCHED_ROW = 2


# --------------------------------------------------------------------------- #
# Identifier normalization
#
# Mirrors pl.commercelink.taxonomy.UnifiedProductIdentifiers (commons repo) so that
# taxonomy CSV values line up with what's actually stored on PIM entries.
# --------------------------------------------------------------------------- #

def unify_ean(ean: Optional[str]) -> Optional[str]:
    if ean is None:
        return None
    if ean.startswith("0"):
        # matches Java's ean.replaceFirst("0", "") when ean startsWith("0"):
        # strips exactly one leading zero.
        return ean.replace("0", "", 1)
    return ean


def unify_mfn(mfn: Optional[str]) -> Optional[str]:
    if mfn is None:
        return None
    return mfn.strip().replace(" ", "").upper()


# --------------------------------------------------------------------------- #
# Data classes
# --------------------------------------------------------------------------- #

@dataclass
class PimCategoryMatch:
    icecat_category_id: str
    product_category: str


@dataclass
class Stats:
    total_rows: int = 0
    matched_by_ean: int = 0
    matched_by_mfn: int = 0
    services_rows: int = 0
    unmatched_rows: int = 0

    def mapped_total(self) -> int:
        return self.matched_by_ean + self.matched_by_mfn


# --------------------------------------------------------------------------- #
# S3 helpers
# --------------------------------------------------------------------------- #

def find_latest_taxonomy_key(s3_client, bucket: str, prefix: str) -> str:
    """List `prefix*.csv` objects and return the lexicographically-greatest key.

    Keys are named `taxonomy/<YYYY-MM-DD>.csv`, so sorting by key name is
    equivalent to sorting by date.
    """
    paginator = s3_client.get_paginator("list_objects_v2")
    keys: List[str] = []
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        for obj in page.get("Contents", []):
            key = obj["Key"]
            if key.endswith(".csv"):
                keys.append(key)

    if not keys:
        raise RuntimeError(
            f"No taxonomy CSV files found under s3://{bucket}/{prefix} - cannot proceed."
        )

    return max(keys)


def download_text(s3_client, bucket: str, key: str) -> str:
    response = s3_client.get_object(Bucket=bucket, Key=key)
    return response["Body"].read().decode("utf-8-sig")


def upload_text(s3_client, bucket: str, key: str, content: str) -> None:
    s3_client.put_object(
        Bucket=bucket,
        Key=key,
        Body=content.encode("utf-8"),
        ContentType="text/csv",
    )


def backup_original(s3_client, bucket: str, backup_prefix: str, source_key: str,
                     content: str, dry_run: bool) -> str:
    """Write an untouched copy of the source file under the backup prefix.

    MUST be called (and must succeed) before any overwrite of the live taxonomy
    key. In dry-run mode this only logs what it would have done.
    """
    original_filename = source_key.rsplit("/", 1)[-1]
    stem = original_filename[:-4] if original_filename.endswith(".csv") else original_filename
    backup_key = f"{backup_prefix}{stem}-pre-icecat.csv"

    if dry_run:
        print(f"[dry-run] would back up s3://{bucket}/{source_key} to "
              f"s3://{bucket}/{backup_key} (skipped)")
        return backup_key

    print(f"Backing up s3://{bucket}/{source_key} to s3://{bucket}/{backup_key} ...")
    try:
        upload_text(s3_client, bucket, backup_key, content)
    except ClientError as e:
        raise RuntimeError(
            f"Backup upload failed ({e}); aborting before touching the live file. "
            f"No changes were made."
        ) from e
    print("Backup complete.")
    return backup_key


# --------------------------------------------------------------------------- #
# PIM DynamoDB scan
# --------------------------------------------------------------------------- #

def scan_pim_category_maps(
    dynamodb_client,
    table_name: str,
    identifiers_attr: str,
    identifier_value_attr: str,
    identifier_type_attr: str,
    ean_type_value: str,
    mfn_type_value: str,
    category_attr: str,
    category_id_attr: str,
) -> Tuple[Dict[str, PimCategoryMatch], Dict[str, PimCategoryMatch]]:
    """Scan the whole PIM table (paginated) and build ean/mfn -> category maps.

    Entries without a populated category_id attribute are skipped entirely -
    they contribute nothing to either map.
    """
    by_ean: Dict[str, PimCategoryMatch] = {}
    by_mfn: Dict[str, PimCategoryMatch] = {}

    projection = ", ".join(
        f"#{name}" for name in {identifiers_attr, category_attr, category_id_attr}
    )
    expr_attr_names = {f"#{name}": name for name in {identifiers_attr, category_attr, category_id_attr}}

    scan_kwargs = {
        "TableName": table_name,
        "ProjectionExpression": projection,
        "ExpressionAttributeNames": expr_attr_names,
    }

    scanned_count = 0
    skipped_no_category_id = 0

    while True:
        response = dynamodb_client.scan(**scan_kwargs)
        items = response.get("Items", [])
        scanned_count += len(items)

        for item in items:
            category_id = _get_s(item, category_id_attr)
            if not category_id:
                skipped_no_category_id += 1
                continue
            category_name = _get_s(item, category_attr) or ""
            match = PimCategoryMatch(icecat_category_id=category_id, product_category=category_name)

            for identifier in item.get(identifiers_attr, {}).get("L", []):
                identifier_map = identifier.get("M", {})
                value = _get_s(identifier_map, identifier_value_attr)
                id_type = _get_s(identifier_map, identifier_type_attr)
                if not value or not id_type:
                    continue
                if id_type == ean_type_value:
                    by_ean.setdefault(unify_ean(value), match)
                elif id_type == mfn_type_value:
                    by_mfn.setdefault(unify_mfn(value), match)

        last_evaluated_key = response.get("LastEvaluatedKey")
        if not last_evaluated_key:
            break
        scan_kwargs["ExclusiveStartKey"] = last_evaluated_key

    print(f"PIM scan complete: {scanned_count} items scanned, "
          f"{skipped_no_category_id} skipped (no {category_id_attr}), "
          f"{len(by_ean)} distinct ean keys, {len(by_mfn)} distinct mfn keys mapped.")

    return by_ean, by_mfn


def _get_s(attribute_map: dict, key: str) -> Optional[str]:
    """Read a plain-string ('S') DynamoDB attribute value, or None if absent/blank."""
    value = attribute_map.get(key, {}).get("S")
    if value is None or value == "":
        return None
    return value


# --------------------------------------------------------------------------- #
# CSV transform
# --------------------------------------------------------------------------- #

def detect_columns(header: List[str]) -> str:
    if header == NEW_COLUMNS:
        return "new"
    if header == OLD_COLUMNS:
        return "old"
    raise RuntimeError(
        "Unrecognized taxonomy CSV header. Expected either the old 8-column format "
        f"{OLD_COLUMNS} or the new 9-column format {NEW_COLUMNS}, got: {header}"
    )


def transform_rows(
    rows: List[List[str]],
    by_ean: Dict[str, PimCategoryMatch],
    by_mfn: Dict[str, PimCategoryMatch],
    services_value: str,
) -> Tuple[List[List[str]], Stats]:
    stats = Stats()
    output_rows: List[List[str]] = []

    for row in rows:
        if len(row) != len(OLD_COLUMNS):
            raise RuntimeError(
                f"Row has {len(row)} columns, expected {len(OLD_COLUMNS)} (old format): {row}"
            )
        stats.total_rows += 1
        ean, mfn, brand, name, category, data_accuracy_score, net_weight_g, gross_weight_g = row

        if category.strip().lower() == services_value.lower():
            stats.services_rows += 1
            output_rows.append([ean, mfn, brand, name, services_value, "",
                                 data_accuracy_score, net_weight_g, gross_weight_g])
            continue

        match: Optional[PimCategoryMatch] = None
        matched_via: Optional[str] = None

        if ean.strip():
            match = by_ean.get(unify_ean(ean.strip()))
            if match is not None:
                matched_via = "ean"

        if match is None and mfn.strip():
            match = by_mfn.get(unify_mfn(mfn.strip()))
            if match is not None:
                matched_via = "mfn"

        if match is not None:
            if matched_via == "ean":
                stats.matched_by_ean += 1
            else:
                stats.matched_by_mfn += 1
            output_rows.append([ean, mfn, brand, name, match.product_category,
                                 match.icecat_category_id, data_accuracy_score,
                                 net_weight_g, gross_weight_g])
        else:
            stats.unmatched_rows += 1
            output_rows.append([ean, mfn, brand, name, "", "",
                                 data_accuracy_score, net_weight_g, gross_weight_g])

    return output_rows, stats


def parse_csv(content: str) -> Tuple[List[str], List[List[str]]]:
    reader = csv.reader(io.StringIO(content), delimiter=CSV_DELIMITER)
    all_rows = list(reader)
    if not all_rows:
        raise RuntimeError("Taxonomy CSV is empty - nothing to migrate.")
    header, data_rows = all_rows[0], all_rows[1:]
    # Drop fully-blank trailing lines some editors/exports add.
    data_rows = [r for r in data_rows if any(cell.strip() for cell in r)]
    return header, data_rows


def render_csv(header: List[str], rows: List[List[str]]) -> str:
    buf = io.StringIO()
    writer = csv.writer(buf, delimiter=CSV_DELIMITER, lineterminator="\n")
    writer.writerow(header)
    writer.writerows(rows)
    return buf.getvalue()


def print_stats(stats: Stats) -> None:
    print()
    print("=== Migration statistics ===")
    print(f"Total rows:            {stats.total_rows}")
    print(f"Mapped from PIM:       {stats.mapped_total()} "
          f"(by ean: {stats.matched_by_ean}, by mfn: {stats.matched_by_mfn})")
    print(f"Services rows:         {stats.services_rows}")
    print(f"Unmatched (empty):     {stats.unmatched_rows}")
    estimated_queries = stats.unmatched_rows * LLM_QUERIES_PER_UNMATCHED_ROW
    print(f"Estimated one-off LLM cost for unmatched rows: "
          f"{stats.unmatched_rows} rows x {LLM_QUERIES_PER_UNMATCHED_ROW} Gemini queries "
          f"= {estimated_queries} queries (self-healed by the app's category-match loop "
          f"after deploy).")
    print("=============================")
    print()


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #

def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Migrate the taxonomy CSV (8-col) to the Icecat-enriched format "
                    "(9-col, with category_id), joined against the PIM DynamoDB table.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("--datalake-bucket", default="datalake",
                         help="S3 bucket holding the taxonomy CSV files.")
    parser.add_argument("--taxonomy-prefix", default="taxonomy/",
                         help="Key prefix under which taxonomy/<date>.csv files live.")
    parser.add_argument("--backup-prefix", default="taxonomy-backup/",
                         help="Key prefix to write the pre-migration backup under.")
    parser.add_argument("--pim-table", required=True,
                         help="Name of the PIM DynamoDB table to scan.")
    parser.add_argument("--aws-endpoint", default=None,
                         help="Override AWS endpoint URL (e.g. http://localhost:4566 for LocalStack). "
                              "Applies to both S3 and DynamoDB clients.")
    parser.add_argument("--region", default="eu-central-1",
                         help="AWS region for both S3 and DynamoDB clients.")

    dry_run_group = parser.add_mutually_exclusive_group()
    dry_run_group.add_argument("--dry-run", dest="dry_run", action="store_true", default=True,
                                help="Do not write anything to S3; only print statistics and "
                                     "(optionally) a local preview file. This is the default.")
    dry_run_group.add_argument("--no-dry-run", "--apply", dest="dry_run", action="store_false",
                                help="Perform the real migration: back up the source file, then "
                                     "upload the new taxonomy/<today>.csv. Required to write anything.")

    parser.add_argument("--local-output", default=None,
                         help="In dry-run mode, write the would-be output CSV to this local path "
                              "for inspection. Defaults to "
                              "'./taxonomy-migration-preview-<today>.csv' in the current directory.")

    parser.add_argument("--services-category-value", default=DEFAULT_SERVICES_VALUE,
                         help="Category string (case-insensitive match) that bypasses PIM lookup "
                              "and is passed through with an empty category_id.")

    # --- PIM schema assumptions - see module docstring for the reasoning. ---
    parser.add_argument("--pim-identifiers-attr", default="productIdentifiers",
                         help="[ASSUMPTION - confirm against prod schema] Name of the PIM item "
                              "attribute holding the list of {value, type} identifier maps.")
    parser.add_argument("--pim-identifier-value-attr", default="value",
                         help="[ASSUMPTION] Attribute name for the identifier's value within each "
                              "element of --pim-identifiers-attr.")
    parser.add_argument("--pim-identifier-type-attr", default="type",
                         help="[ASSUMPTION] Attribute name for the identifier's type within each "
                              "element of --pim-identifiers-attr.")
    parser.add_argument("--pim-ean-type-value", default="GTIN",
                         help="[ASSUMPTION] Value of the identifier type attribute that marks an EAN/GTIN.")
    parser.add_argument("--pim-mfn-type-value", default="MfnCode",
                         help="[ASSUMPTION] Value of the identifier type attribute that marks an MFN code.")
    parser.add_argument("--pim-category-attr", default="productCategory",
                         help="[ASSUMPTION] PIM item attribute holding the Icecat category name.")
    parser.add_argument("--pim-category-id-attr", default="icecatCategoryId",
                         help="[ASSUMPTION] PIM item attribute holding the Icecat category id. "
                              "Entries where this is missing/empty are skipped entirely.")

    return parser


def main(argv: Optional[List[str]] = None) -> int:
    args = build_arg_parser().parse_args(argv)

    print(f"Mode: {'DRY-RUN (no writes)' if args.dry_run else 'APPLY (will write to S3)'}")
    print(f"Datalake bucket: {args.datalake_bucket}   PIM table: {args.pim_table}   "
          f"Region: {args.region}   Endpoint: {args.aws_endpoint or '(default AWS)'}")

    session_kwargs = {"region_name": args.region}
    client_kwargs = {}
    if args.aws_endpoint:
        client_kwargs["endpoint_url"] = args.aws_endpoint

    s3_client = boto3.client("s3", **session_kwargs, **client_kwargs)
    dynamodb_client = boto3.client("dynamodb", **session_kwargs, **client_kwargs)

    # Step 1: find + download the newest taxonomy file.
    source_key = find_latest_taxonomy_key(s3_client, args.datalake_bucket, args.taxonomy_prefix)
    print(f"Latest taxonomy file: s3://{args.datalake_bucket}/{source_key}")
    original_content = download_text(s3_client, args.datalake_bucket, source_key)

    header, data_rows = parse_csv(original_content)
    file_format = detect_columns(header)
    if file_format == "new":
        raise RuntimeError(
            f"s3://{args.datalake_bucket}/{source_key} is already in the 9-column format "
            f"(header={header}). Refusing to migrate it again - already migrated."
        )
    print(f"Detected old 8-column format, {len(data_rows)} data rows.")

    # Step 2: backup BEFORE any overwrite (mandatory; only skipped/logged in dry-run).
    backup_original(s3_client, args.datalake_bucket, args.backup_prefix, source_key,
                     original_content, args.dry_run)

    # Step 3: build ean/mfn -> category maps from PIM.
    by_ean, by_mfn = scan_pim_category_maps(
        dynamodb_client,
        table_name=args.pim_table,
        identifiers_attr=args.pim_identifiers_attr,
        identifier_value_attr=args.pim_identifier_value_attr,
        identifier_type_attr=args.pim_identifier_type_attr,
        ean_type_value=args.pim_ean_type_value,
        mfn_type_value=args.pim_mfn_type_value,
        category_attr=args.pim_category_attr,
        category_id_attr=args.pim_category_id_attr,
    )

    # Step 4 + 5: transform rows and render the new CSV.
    new_rows, stats = transform_rows(data_rows, by_ean, by_mfn, args.services_category_value)
    new_content = render_csv(NEW_COLUMNS, new_rows)

    today = datetime.date.today().isoformat()
    target_key = f"{args.taxonomy_prefix}{today}.csv"

    if args.dry_run:
        local_output = args.local_output or f"./taxonomy-migration-preview-{today}.csv"
        with open(local_output, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"[dry-run] would upload new file to s3://{args.datalake_bucket}/{target_key}")
        print(f"[dry-run] preview written locally to {local_output}")
    else:
        if target_key == source_key:
            print(f"WARNING: target key {target_key} is the same as the source key "
                  f"(migration already run today?). Overwriting anyway - backup was taken above.")
        print(f"Uploading new file to s3://{args.datalake_bucket}/{target_key} ...")
        upload_text(s3_client, args.datalake_bucket, target_key, new_content)
        print("Upload complete.")

    print_stats(stats)

    if stats.total_rows == 0:
        print("WARNING: source file had zero data rows.", file=sys.stderr)

    return 0


if __name__ == "__main__":
    sys.exit(main())
