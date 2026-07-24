#!/usr/bin/env python3
"""Unit + flow tests for migrate_taxonomy.py.

Runs with the standard library only (no boto3/moto/pytest required):

    python3 -m unittest

boto3 and botocore are stubbed in sys.modules before the script under test is
imported, and the S3/DynamoDB clients are replaced with in-memory fakes. This
exercises the pure transform logic and the full main() flow (dry-run, apply with
backup-before-overwrite ordering, already-migrated guard, paginated PIM scan)
without touching AWS.
"""

import importlib.util
import io
import os
import sys
import tempfile
import types
import unittest


def _install_boto3_stubs():
    boto3_stub = types.ModuleType("boto3")
    boto3_stub.client = lambda *a, **k: None

    botocore_stub = types.ModuleType("botocore")
    exceptions_stub = types.ModuleType("botocore.exceptions")

    class ClientError(Exception):
        pass

    exceptions_stub.ClientError = ClientError
    botocore_stub.exceptions = exceptions_stub

    sys.modules.setdefault("boto3", boto3_stub)
    sys.modules.setdefault("botocore", botocore_stub)
    sys.modules.setdefault("botocore.exceptions", exceptions_stub)
    return boto3_stub, ClientError


BOTO3_STUB, ClientError = _install_boto3_stubs()


def _load_module():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "migrate_taxonomy.py")
    spec = importlib.util.spec_from_file_location("migrate_taxonomy", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


migrate = _load_module()


# --------------------------------------------------------------------------- #
# In-memory AWS fakes
# --------------------------------------------------------------------------- #

class FakeBody:
    def __init__(self, data: bytes):
        self._data = data

    def read(self) -> bytes:
        return self._data


class FakePaginator:
    def __init__(self, store):
        self._store = store

    def paginate(self, Bucket, Prefix):
        contents = [{"Key": key} for key in self._store if key.startswith(Prefix)]
        yield {"Contents": contents}


class FakeS3Client:
    def __init__(self):
        self.store = {}
        self.put_calls = []
        self.fail_put_on_prefix = None

    def get_paginator(self, name):
        assert name == "list_objects_v2"
        return FakePaginator(self.store)

    def get_object(self, Bucket, Key):
        if Key not in self.store:
            raise KeyError(Key)
        return {"Body": FakeBody(self.store[Key])}

    def put_object(self, Bucket, Key, Body, ContentType=None):
        if self.fail_put_on_prefix and Key.startswith(self.fail_put_on_prefix):
            raise ClientError("simulated put failure")
        self.store[Key] = Body if isinstance(Body, bytes) else Body.encode("utf-8")
        self.put_calls.append(Key)


class FakeDynamoDbClient:
    def __init__(self, pages):
        self._pages = list(pages)
        self._index = 0

    def scan(self, **kwargs):
        page = self._pages[self._index]
        self._index += 1
        return page


def ddb_item(category_id=None, category=None, identifiers=None):
    item = {}
    if category_id is not None:
        item["icecatCategoryId"] = {"S": category_id}
    if category is not None:
        item["productCategory"] = {"S": category}
    ids = []
    for value, id_type in (identifiers or []):
        ids.append({"M": {"value": {"S": value}, "type": {"S": id_type}}})
    item["productIdentifiers"] = {"L": ids}
    return item


def scan_maps(client):
    return migrate.scan_pim_category_maps(
        client, "PIM",
        identifiers_attr="productIdentifiers",
        identifier_value_attr="value",
        identifier_type_attr="type",
        ean_type_value="GTIN",
        mfn_type_value="MfnCode",
        category_attr="productCategory",
        category_id_attr="icecatCategoryId",
    )


OLD_HEADER = ";".join(migrate.OLD_COLUMNS)
NEW_HEADER = ";".join(migrate.NEW_COLUMNS)


# --------------------------------------------------------------------------- #
# Pure functions
# --------------------------------------------------------------------------- #

class TestIdentifierNormalization(unittest.TestCase):
    def test_unify_ean_strips_exactly_one_leading_zero(self):
        self.assertEqual(migrate.unify_ean("05901234567890"), "5901234567890")

    def test_unify_ean_without_leading_zero_is_unchanged(self):
        self.assertEqual(migrate.unify_ean("5901234567890"), "5901234567890")

    def test_unify_ean_none_is_none(self):
        self.assertIsNone(migrate.unify_ean(None))

    def test_unify_mfn_trims_spaces_and_uppercases(self):
        self.assertEqual(migrate.unify_mfn("  rtx 40 90 "), "RTX4090")

    def test_unify_mfn_none_is_none(self):
        self.assertIsNone(migrate.unify_mfn(None))


class TestDetectColumns(unittest.TestCase):
    def test_new_header_detected_as_new(self):
        self.assertEqual(migrate.detect_columns(migrate.NEW_COLUMNS), "new")

    def test_old_header_detected_as_old(self):
        self.assertEqual(migrate.detect_columns(migrate.OLD_COLUMNS), "old")

    def test_unrecognized_header_raises(self):
        with self.assertRaises(RuntimeError):
            migrate.detect_columns(["ean", "mfn"])


class TestParseCsv(unittest.TestCase):
    def test_parses_header_and_rows(self):
        header, rows = migrate.parse_csv(OLD_HEADER + "\n5901;MFN;B;N;CPU;5;10;20\n")
        self.assertEqual(header, migrate.OLD_COLUMNS)
        self.assertEqual(len(rows), 1)

    def test_empty_content_raises(self):
        with self.assertRaises(RuntimeError):
            migrate.parse_csv("")

    def test_blank_trailing_rows_are_dropped(self):
        content = OLD_HEADER + "\n5901;MFN;B;N;CPU;5;10;20\n;;;;;;;\n"
        _, rows = migrate.parse_csv(content)
        self.assertEqual(len(rows), 1)


class TestTransformRows(unittest.TestCase):
    def _row(self, ean="5901234567890", mfn="MFN1", category="CPU"):
        return [ean, mfn, "Brand", "Name", category, "5", "10", "20"]

    def test_services_row_passthrough_case_insensitive_with_empty_id(self):
        rows, stats = migrate.transform_rows([self._row(category="services")], {}, {}, "Services")
        self.assertEqual(rows[0][4], "Services")
        self.assertEqual(rows[0][5], "")
        self.assertEqual(stats.services_rows, 1)

    def test_match_by_ean_fills_name_and_id(self):
        by_ean = {"5901234567890": migrate.PimCategoryMatch("1613", "Karty graficzne")}
        rows, stats = migrate.transform_rows([self._row()], by_ean, {}, "Services")
        self.assertEqual(rows[0][4], "Karty graficzne")
        self.assertEqual(rows[0][5], "1613")
        self.assertEqual(stats.matched_by_ean, 1)

    def test_match_by_mfn_when_no_ean_match(self):
        by_mfn = {"MFN1": migrate.PimCategoryMatch("42", "Procesory")}
        rows, stats = migrate.transform_rows([self._row()], {}, by_mfn, "Services")
        self.assertEqual(rows[0][5], "42")
        self.assertEqual(stats.matched_by_mfn, 1)

    def test_ean_match_takes_precedence_over_mfn(self):
        by_ean = {"5901234567890": migrate.PimCategoryMatch("1", "FromEan")}
        by_mfn = {"MFN1": migrate.PimCategoryMatch("2", "FromMfn")}
        rows, stats = migrate.transform_rows([self._row()], by_ean, by_mfn, "Services")
        self.assertEqual(rows[0][4], "FromEan")
        self.assertEqual(stats.matched_by_ean, 1)
        self.assertEqual(stats.matched_by_mfn, 0)

    def test_unmatched_row_leaves_category_and_id_empty(self):
        rows, stats = migrate.transform_rows([self._row()], {}, {}, "Services")
        self.assertEqual(rows[0][4], "")
        self.assertEqual(rows[0][5], "")
        self.assertEqual(stats.unmatched_rows, 1)

    def test_output_row_has_nine_columns(self):
        rows, _ = migrate.transform_rows([self._row()], {}, {}, "Services")
        self.assertEqual(len(rows[0]), len(migrate.NEW_COLUMNS))

    def test_wrong_column_count_raises(self):
        with self.assertRaises(RuntimeError):
            migrate.transform_rows([["only", "three", "cols"]], {}, {}, "Services")


class TestScanPimCategoryMaps(unittest.TestCase):
    def test_maps_gtin_to_ean_and_mfncode_to_mfn(self):
        client = FakeDynamoDbClient([
            {"Items": [ddb_item("1613", "Karty graficzne",
                                 [("05901234567890", "GTIN"), (" rtx 4090 ", "MfnCode")])]},
        ])
        by_ean, by_mfn = scan_maps(client)
        self.assertIn("5901234567890", by_ean)
        self.assertIn("RTX4090", by_mfn)
        self.assertEqual(by_ean["5901234567890"].icecat_category_id, "1613")

    def test_entries_without_category_id_are_skipped(self):
        client = FakeDynamoDbClient([
            {"Items": [ddb_item(None, "NoId", [("111", "GTIN")])]},
        ])
        by_ean, by_mfn = scan_maps(client)
        self.assertEqual(by_ean, {})
        self.assertEqual(by_mfn, {})

    def test_pagination_and_first_write_wins(self):
        client = FakeDynamoDbClient([
            {"Items": [ddb_item("1", "First", [("999", "GTIN")])],
             "LastEvaluatedKey": {"pimId": {"S": "x"}}},
            {"Items": [ddb_item("2", "Second", [("999", "GTIN")])]},
        ])
        by_ean, _ = scan_maps(client)
        self.assertEqual(by_ean["999"].product_category, "First")


class TestBackupOriginal(unittest.TestCase):
    def test_dry_run_does_not_upload(self):
        s3 = FakeS3Client()
        key = migrate.backup_original(s3, "datalake", "taxonomy-backup/",
                                      "taxonomy/2026-07-20.csv", "content", dry_run=True)
        self.assertEqual(key, "taxonomy-backup/2026-07-20-pre-icecat.csv")
        self.assertEqual(s3.put_calls, [])

    def test_real_run_uploads_backup(self):
        s3 = FakeS3Client()
        migrate.backup_original(s3, "datalake", "taxonomy-backup/",
                                "taxonomy/2026-07-20.csv", "content", dry_run=False)
        self.assertIn("taxonomy-backup/2026-07-20-pre-icecat.csv", s3.store)

    def test_backup_failure_aborts_fail_closed(self):
        s3 = FakeS3Client()
        s3.fail_put_on_prefix = "taxonomy-backup/"
        with self.assertRaises(RuntimeError):
            migrate.backup_original(s3, "datalake", "taxonomy-backup/",
                                    "taxonomy/2026-07-20.csv", "content", dry_run=False)


class TestRenderCsv(unittest.TestCase):
    def test_render_round_trips_through_parse(self):
        rows = [["5901", "MFN", "B", "N", "CPU", "1", "5", "10", "20"]]
        content = migrate.render_csv(migrate.NEW_COLUMNS, rows)
        header, parsed = migrate.parse_csv(content)
        self.assertEqual(header, migrate.NEW_COLUMNS)
        self.assertEqual(parsed, rows)


# --------------------------------------------------------------------------- #
# main() flow
# --------------------------------------------------------------------------- #

class TestMainFlow(unittest.TestCase):
    def setUp(self):
        self.s3 = FakeS3Client()
        self.source_key = "taxonomy/2026-07-20.csv"
        source = (OLD_HEADER + "\n"
                  "5901234567890;RTX4090;NVIDIA;GeForce;OldKey;5;100;200\n"
                  "0000000000000;SVC1;Store;Setup;services;0;;\n"
                  "9999999999999;UNKNOWN;Acme;Widget;Misc;3;;\n")
        self.s3.store[self.source_key] = source.encode("utf-8")
        self.ddb = FakeDynamoDbClient([
            {"Items": [ddb_item("1613", "Karty graficzne",
                                 [("5901234567890", "GTIN"), ("RTX4090", "MfnCode")])]},
        ])
        BOTO3_STUB.client = self._client_factory

    def tearDown(self):
        BOTO3_STUB.client = lambda *a, **k: None

    def _client_factory(self, service, **kwargs):
        if service == "s3":
            return self.s3
        if service == "dynamodb":
            return self.ddb
        raise AssertionError("unexpected service " + service)

    def _target_key(self):
        return "taxonomy/" + migrate.datetime.date.today().isoformat() + ".csv"

    def test_dry_run_writes_nothing_to_s3_and_emits_preview(self):
        with tempfile.TemporaryDirectory() as tmp:
            preview = os.path.join(tmp, "preview.csv")
            rc = migrate.main(["--pim-table", "PIM", "--dry-run", "--local-output", preview])
            self.assertEqual(rc, 0)
            self.assertEqual(self.s3.put_calls, [])
            with open(preview, encoding="utf-8") as f:
                header, rows = migrate.parse_csv(f.read())
            self.assertEqual(header, migrate.NEW_COLUMNS)
            self.assertEqual(rows[0][4], "Karty graficzne")
            self.assertEqual(rows[0][5], "1613")

    def test_apply_backs_up_before_overwriting_and_uploads_nine_column_file(self):
        rc = migrate.main(["--pim-table", "PIM", "--no-dry-run"])
        self.assertEqual(rc, 0)
        backup_key = "taxonomy-backup/2026-07-20-pre-icecat.csv"
        target_key = self._target_key()
        self.assertIn(backup_key, self.s3.put_calls)
        self.assertIn(target_key, self.s3.put_calls)
        self.assertLess(self.s3.put_calls.index(backup_key),
                        self.s3.put_calls.index(target_key))
        header, rows = migrate.parse_csv(self.s3.store[target_key].decode("utf-8"))
        self.assertEqual(header, migrate.NEW_COLUMNS)
        self.assertEqual(rows[0][5], "1613")
        self.assertEqual(rows[1][4], "Services")
        self.assertEqual(rows[2][4], "")

    def test_already_migrated_file_aborts(self):
        self.s3.store[self.source_key] = (NEW_HEADER + "\n").encode("utf-8")
        with self.assertRaises(RuntimeError):
            migrate.main(["--pim-table", "PIM", "--no-dry-run"])

    def _use_all_non_services_source_with_empty_pim(self):
        source = (OLD_HEADER + "\n"
                  "5901234567890;RTX4090;NVIDIA;GeForce;GPU;5;100;200\n"
                  "9999999999999;UNKNOWN;Acme;Widget;Misc;3;;\n")
        self.s3.store[self.source_key] = source.encode("utf-8")
        # PIM scan yields nothing -> both maps empty (V007 didn't run / schema drift).
        self.ddb = FakeDynamoDbClient([{"Items": []}])

    def test_apply_aborts_when_pim_scan_empty_and_uploads_nothing(self):
        self._use_all_non_services_source_with_empty_pim()
        with self.assertRaises(RuntimeError):
            migrate.main(["--pim-table", "PIM", "--no-dry-run"])
        target_key = self._target_key()
        self.assertNotIn(target_key, self.s3.put_calls)

    def test_apply_with_force_proceeds_when_pim_scan_empty(self):
        self._use_all_non_services_source_with_empty_pim()
        rc = migrate.main(["--pim-table", "PIM", "--no-dry-run", "--force"])
        self.assertEqual(rc, 0)
        target_key = self._target_key()
        self.assertIn(target_key, self.s3.put_calls)
        header, rows = migrate.parse_csv(self.s3.store[target_key].decode("utf-8"))
        self.assertEqual(header, migrate.NEW_COLUMNS)
        self.assertEqual(rows[0][4], "")
        self.assertEqual(rows[0][5], "")


if __name__ == "__main__":
    unittest.main()
