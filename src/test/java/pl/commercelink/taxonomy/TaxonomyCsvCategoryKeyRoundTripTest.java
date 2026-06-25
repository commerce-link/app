package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyCsvCategoryKeyRoundTripTest {

    @Test
    void unknownTokenInNewColumnSurvivesAsLiteralCategoryKeyNotCollapsedToOther() {
        // given - new category_key column (index 8) carries a non-enum token; legacy category column stays Other
        String[] row = {"123", "MFN", "Brand", "Name", "Other", "5", "", "", "Cables356k"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertThat(parsed.categoryKey()).isEqualTo("Cables356k");
        assertThat(parsed.category()).isEqualTo(ProductCategory.Other);
    }

    @Test
    void categoryKeyIsWrittenIntoNewColumnWhileLegacyCategoryColumnStaysEnumName() {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                ProductCategory.Other, 1, null, null, "Cables356k");

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();
        Taxonomy parsed = TaxonomyParser.fromCsvRow(rows.get(0));

        // then
        assertThat(rows.get(0)[4]).isEqualTo("Other");
        assertThat(rows.get(0)[8]).isEqualTo("Cables356k");
        assertThat(parsed.categoryKey()).isEqualTo("Cables356k");
    }

    @Test
    void oldFileWithoutNewColumnFallsBackToEnumName() {
        // given - legacy 8-column row, no category_key column (version-skew / pre-existing file)
        String[] row = {"123", "MFN", "Brand", "Name", "Laptops", "5", "", ""};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertThat(parsed.categoryKey()).isEqualTo("Laptops");
        assertThat(parsed.category()).isEqualTo(ProductCategory.Laptops);
    }

    @Test
    void knownCategoryColumnAndNewColumnStayByteIdenticalToEnumName() {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                ProductCategory.Laptops, 1, null, null);

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        String[] row = loader.readHeadersAndRows(';').getSecond().get(0);

        // then
        assertThat(row[4]).isEqualTo("Laptops");
        assertThat(row[8]).isEqualTo("Laptops");
    }
}
