# Taxonomy -> Icecat category migration (offline, one-shot)

Standalone Python script. **Not** built or run by the app — it does not touch any
Java source and has no effect on the Maven build. It exists to be run once by a
human operator on deployment day.

## What it does

Rewrites the taxonomy CSV on S3 (`datalake/taxonomy/<YYYY-MM-DD>.csv`) from the old
8-column format to the new 9-column format that adds `category_id`:

```
old: ean;mfn;brand;name;category;data_accuracy_score;net_weight_g;gross_weight_g
new: ean;mfn;brand;name;category;category_id;data_accuracy_score;net_weight_g;gross_weight_g
```

For each row it looks up the product in the PIM DynamoDB table (by `ean`, then by
`mfn`) and fills in the Icecat category name + id from there. `Services` rows are
passed through unchanged with an empty `category_id` (never looked up in PIM). Rows
with no PIM match are left with an **empty** `category` and `category_id` — this is
intentional: the app's LLM category-match loop self-heals these after deploy, so an
unmatched row is not a failure, just deferred work.

## Where this fits in the runbook

Run this **after** the PIM service has been deployed with the `V007` backfill
migration (`V007_BackfillIcecatCategoryFromDataJson`) — that's what populates
`icecatCategoryId` / `productCategory` on existing PIM entries — and **before**
deploying the app version that expects the 9-column taxonomy format.

```
1. Deploy PIM (includes V007 backfill)  --  wait for it to finish
2. Run this script in --dry-run (default) and review the stats + preview file
3. Run this script with --no-dry-run / --apply
4. Deploy the app
```

## Usage

Dry run (default — makes no changes, just prints stats and writes a local preview):

```bash
python3 migrate_taxonomy.py --pim-table PIM
```

Dry run against LocalStack, for a rehearsal:

```bash
python3 migrate_taxonomy.py \
  --pim-table PIM \
  --aws-endpoint http://localhost:4566 \
  --region eu-central-1
```

Real run (uploads the backup, then the migrated file):

```bash
python3 migrate_taxonomy.py --pim-table PIM --no-dry-run
# or: --apply, same thing
```

Run `python3 migrate_taxonomy.py --help` for the full flag list.

## Safety properties

- **Dry-run by default.** Nothing is written to S3 unless you pass `--no-dry-run`
  (or its alias `--apply`).
- **Mandatory backup before overwrite.** Before writing anything new, the script
  uploads an untouched copy of the source file to
  `taxonomy-backup/<original-filename>-pre-icecat.csv` in the same bucket. If that
  backup upload fails, the script aborts and does not touch the live file. In
  dry-run, the backup step is only logged ("would back up..."), not performed.
- **Idempotency guard.** If the newest taxonomy file is already in the 9-column
  format, the script refuses to run ("already migrated") instead of silently
  re-processing it.
- **New file, not an overwrite of the source.** The migrated file is written as
  `taxonomy/<today's date>.csv`, a new key that sorts after the source file
  (`taxonomy/<YYYY-MM-DD>.csv` naming), so the app picks it up as the newest file.
  The original source file is left in place untouched.

## Rollback

If the migrated file needs to be reverted after upload:

```bash
aws s3 cp s3://datalake/taxonomy-backup/<original-filename>-pre-icecat.csv \
          s3://datalake/taxonomy/<a-new-later-date>.csv
```

Upload the backup under a **new key with today's (or a later) date** so it sorts
above the bad file — do not delete the migrated file, keep both for the audit
trail. If the app has already started reading the new 9-column format, restoring
the old 8-column file will make old-format parsing kick in again; check the app
release notes for that migration boundary before rolling back.

## Assumptions about the PIM DynamoDB schema — CONFIRM BEFORE RUNNING

The script's defaults were reverse-engineered from
`pl.commercelink.pim.PIMIndexEntry` / `ProductIdentifier` in the `pim` repo
(branch `taxonomy-category-enrichment`, as of 2026-07-23) and are **not** simple
flat attributes — they reflect the real nested DynamoDB item shape:

| Flag | Default | What it is |
|---|---|---|
| `--pim-identifiers-attr` | `productIdentifiers` | List attribute of `{value, type}` maps holding all EAN/MFN identifiers for a PIM entry |
| `--pim-identifier-value-attr` | `value` | Attribute name for the identifier's value inside each list element |
| `--pim-identifier-type-attr` | `type` | Attribute name for the identifier's type inside each list element |
| `--pim-ean-type-value` | `GTIN` | Type value that marks an identifier as an EAN (matches the Java enum `ProductIdentifierType.GTIN`) |
| `--pim-mfn-type-value` | `MfnCode` | Type value that marks an identifier as an MFN code (`ProductIdentifierType.MfnCode`) |
| `--pim-category-attr` | `productCategory` | Flat attribute holding the Icecat category name |
| `--pim-category-id-attr` | `icecatCategoryId` | Flat attribute holding the Icecat category id (entries missing/blank here are skipped entirely when building the lookup maps) |

There is **no flat top-level `ean` / `mfn` attribute** on a PIM item — identifiers
live in the nested `productIdentifiers` list. If the deployed production schema has
drifted from this by deployment day (e.g. a later migration flattens identifiers,
renames `icecatCategoryId`, etc.), override the relevant `--pim-*` flag rather than
editing the script.

The script also re-implements `UnifiedProductIdentifiers.unifyEan` / `unifyMfn`
(from the `commons` repo) in Python to normalize the taxonomy CSV's `ean`/`mfn`
values the same way PIM normalizes them at write time (strip one leading zero from
ean; trim/remove-spaces/uppercase for mfn), so formatting differences don't cause
false misses. If that normalization logic changes in Java, update `unify_ean` /
`unify_mfn` in `migrate_taxonomy.py` to match.

## Requirements

- Python 3
- `boto3` (`pip install boto3`)
- AWS credentials with `s3:ListBucket`/`GetObject`/`PutObject` on the datalake
  bucket and `dynamodb:Scan` on the PIM table (via the normal boto3 credential
  chain — no credentials are hardcoded in the script).
