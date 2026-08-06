package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaxonomyParserTest {

    @Test
    void csvRoundTripPreservesCategoryString() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", "Laptops", 1, null, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertEquals("Laptops", parsed.category());
    }

    @Test
    void nonEnumCategorySurvivesCsvRoundTrip() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", "Smartwatches", 1, null, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertEquals("Smartwatches", parsed.category());
    }

    @Test
    void blankCategoryCellYieldsNullCategory() {
        String[] row = {"1234567890123", "MFN-1", "TestBrand", "Test Product", "", "1"};

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.category());
    }

    @Test
    void csvRoundTripPreservesWeight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", "Laptops", 1, 1300, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertEquals(1300, parsed.netWeightInGrams());
    }

    @Test
    void csvRoundTripPreservesNullWeight() {
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", "Laptops", 1, null, null);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertNull(parsed.netWeightInGrams());
    }

    @Test
    void backwardCompatReadsLegacySixColumnRow() {
        String[] legacyRow = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(legacyRow);

        assertEquals("1234567890123", parsed.ean());
        assertEquals("MFN-1", parsed.mfn());
        assertNull(parsed.netWeightInGrams());
    }

    @Test
    void emptyWeightCellYieldsNull() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", ""
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.netWeightInGrams());
    }

    @Test
    void malformedWeightCellYieldsNull() {
        String[] row = {
                "1234567890123", "MFN-1", "TestBrand", "Test Product", "Laptops", "1", "abc"
        };

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertNull(parsed.netWeightInGrams());
    }

    @Test
    void writesAndReadsBothWeightsRoundTrip() {
        Taxonomy original = new Taxonomy("8718951561588", "EP1223/00", "Philips", "Ekspres",
                "Other", 5, 8000, 12500);

        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();

        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        assertThat(parsed.netWeightInGrams()).isEqualTo(8000);
        assertThat(parsed.grossWeightInGrams()).isEqualTo(12500);
    }

    @Test
    void readsLegacyCsvWithoutWeightColumnsAsNull() {
        String[] legacyRow = {"123", "MFN", "Brand", "Name", "Other", "5"};

        Taxonomy parsed = TaxonomyParser.fromCsvRow(legacyRow);

        assertThat(parsed.netWeightInGrams()).isNull();
        assertThat(parsed.grossWeightInGrams()).isNull();
    }

    @Test
    void readsBlankWeightColumnsAsNull() {
        String[] row = {"123", "MFN", "Brand", "Name", "Other", "5", "", ""};

        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        assertThat(parsed.netWeightInGrams()).isNull();
        assertThat(parsed.grossWeightInGrams()).isNull();
    }

    @Test
    void nineColumnRowReadsCategoryAndCategoryId() {
        // given
        String[] row = {"5901234123457", "MFN-1", "BrandX", "Mysz", "Myszki", "195", "5", "100", "120"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals("Myszki", parsed.category());
        assertEquals("195", parsed.categoryId());
        assertEquals(5, parsed.dataAccuracyScore());
        assertEquals(100, parsed.netWeightInGrams());
        assertEquals(120, parsed.grossWeightInGrams());
    }

    @Test
    void categoryIdSurvivesCsvRoundTrip() {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand", "Router",
                "Routery", 10, null, null, null, "537");

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();
        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        // then
        assertEquals("Routery", parsed.category());
        assertEquals("537", parsed.categoryId());
    }

    @Test
    void strayUnescapedSeparatorInNameDoesNotShiftTrailingColumns() {
        // given: real taxonomy-merged-full.csv row where a mangled "&#39;" (missing the
        // leading &) leaves a literal ";" inside the product name, splitting it in two
        String[] row = {
                "8596049159455", "60312151000003", "Epico",
                "Epico Flexiglass Szklo ochronne do iPhone#39", "a 13 i 13 Pro i 14",
                "Ochraniacze na ekran i tyl telefonu", "1568", "3", "", ""
        };

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals("Epico Flexiglass Szklo ochronne do iPhone#39;a 13 i 13 Pro i 14", parsed.name());
        assertEquals("Ochraniacze na ekran i tyl telefonu", parsed.category());
        assertEquals("1568", parsed.categoryId());
        assertEquals(3, parsed.dataAccuracyScore());
        assertNull(parsed.netWeightInGrams());
        assertNull(parsed.grossWeightInGrams());
    }

    @Test
    void twoStraySeparatorsInNameStillLeaveTrailingColumnsIntact() {
        // given: same corruption pattern, but the name gets split into three pieces
        String[] row = {
                "23942987499", "98749-V", "Verbatim",
                "Verbatim Store #39", "n#39", " Go Metal Executive 32 GB srebrny",
                "Pamiec USB", "1554", "3", "", ""
        };

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertEquals("Verbatim Store #39;n#39; Go Metal Executive 32 GB srebrny", parsed.name());
        assertEquals("Pamiec USB", parsed.category());
        assertEquals("1554", parsed.categoryId());
        assertEquals(3, parsed.dataAccuracyScore());
    }

    @Test
    void legacyEightColumnRowLeavesCategoryIdNull() {
        // given
        String[] legacyRow = {"5901234123457", "MFN-1", "BrandX", "Mysz", "Myszki", "5", "100", "120"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(legacyRow);

        // then
        assertEquals("Myszki", parsed.category());
        assertNull(parsed.categoryId());
        assertEquals(5, parsed.dataAccuracyScore());
        assertEquals(100, parsed.netWeightInGrams());
    }
}
