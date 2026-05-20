package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaxonomyParserTest {

    @Test
    void csv_round_trip_preserves_weight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", ProductCategory.Laptops, 1, 1300);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        String[] rows = new String(csv).split("\r?\n");

        String[] dataRow = rows[1].split(",", -1);
        Taxonomy parsed = TaxonomyParser.fromCsvRow(dataRow);

        assertEquals(1300, parsed.weightInGrams());
    }

    @Test
    void csv_round_trip_preserves_null_weight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", ProductCategory.Laptops, 1, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        String[] rows = new String(csv).split("\r?\n");

        String[] dataRow = rows[1].split(",", -1);
        Taxonomy parsed = TaxonomyParser.fromCsvRow(dataRow);

        assertNull(parsed.weightInGrams());
    }

    @Test
    void backward_compat_reads_legacy_six_column_row() {
        String[] legacyRow = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(legacyRow);

        assertEquals("1234567890123", parsed.ean());
        assertEquals("MFN-1", parsed.mfn());
        assertNull(parsed.weightInGrams());
    }

    @Test
    void empty_weight_cell_yields_null() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", ""
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.weightInGrams());
    }

    @Test
    void malformed_weight_cell_yields_null() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", "abc"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.weightInGrams());
    }
}
