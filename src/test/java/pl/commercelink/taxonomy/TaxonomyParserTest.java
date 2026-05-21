package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaxonomyParserTest {

    @Test
    void csvRoundTripPreservesWeight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", ProductCategory.Laptops, 1, 1300);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertEquals(1300, parsed.weightInGrams());
    }

    @Test
    void csvRoundTripPreservesNullWeight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", ProductCategory.Laptops, 1, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertNull(parsed.weightInGrams());
    }

    @Test
    void backwardCompatReadsLegacySixColumnRow() {
        String[] legacyRow = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(legacyRow);

        assertEquals("1234567890123", parsed.ean());
        assertEquals("MFN-1", parsed.mfn());
        assertNull(parsed.weightInGrams());
    }

    @Test
    void emptyWeightCellYieldsNull() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", ""
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.weightInGrams());
    }

    @Test
    void malformedWeightCellYieldsNull() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", "abc"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.weightInGrams());
    }
}
