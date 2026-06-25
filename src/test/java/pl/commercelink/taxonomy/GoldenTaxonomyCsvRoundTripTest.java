package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GOLDEN characterization of the 8-TAXONOMY CSV round-trip surface.
 *
 * <p>This test FREEZES current behavior of {@link TaxonomyParser}. If an assertion disagreed with
 * reality, the assertion was corrected to match the code, never vice versa (characterization).
 *
 * Semantics frozen:
 *  - serialize ({@code TaxonomyParser.toCsv} -> {@code toStringArray} uses {@code category().name()})
 *    followed by deserialize ({@code CSVLoader} -> {@code TaxonomyParser.fromCsvRow} -> {@code parseCategory}
 *    -> {@code ProductCategory.valueOf}) is an IDENTITY on category for all 52 {@link ProductCategory}
 *    constants: {@code parse(toStringArray(taxonomyOf(cat))).category() == cat}.
 *  - an unknown category token on the deserialize path does NOT throw; {@code parseCategory}'s try/catch
 *    swallows the {@link IllegalArgumentException} and returns {@link ProductCategory#Other}.
 *
 * CONTRAST with the bare-{@code valueOf} call-sites frozen in {@code GoldenValueOfSitesTest}
 * (PricelistRepository / CsvOfferImporter / controllers): the SAME unknown token throws there but is
 * mapped to {@code Other} here -- divergent unknown-input semantics on two parse paths.
 *
 * MUTATION GUARD: if {@code TaxonomyParser#parseCategory} loses its try/catch and becomes a bare
 * {@code ProductCategory.valueOf(value)}, {@link #unknownCategoryTokenIsMappedToOtherNotThrown()} fails
 * because {@code fromCsvRow} would propagate {@code IllegalArgumentException} instead of returning Other.
 */
@ExtendWith(MockitoExtension.class)
class GoldenTaxonomyCsvRoundTripTest {

    @ParameterizedTest
    @EnumSource(ProductCategory.class)
    void csvRoundTripPreservesEveryCategory(ProductCategory category) {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                "Test Product", category, 1, 1300, 1500);

        // when
        Taxonomy parsed = roundTrip(original);

        // then
        assertEquals(category, parsed.category());
    }

    @Test
    void csvRoundTripPreservesAllFiftyTwoCategoriesInOneTable() {
        // given
        ProductCategory[] categories = ProductCategory.values();

        // when
        for (ProductCategory category : categories) {
            Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "TestBrand",
                    "Test Product", category, 1, 1300, 1500);
            Taxonomy parsed = roundTrip(original);

            // then
            assertEquals(category, parsed.category());
        }
        assertEquals(52, categories.length);
    }

    @Test
    void unknownCategoryTokenIsMappedToOtherNotThrown() {
        // given
        String[] rowWithUnknownCategory = {"123", "MFN", "Brand", "Name", "NotARealCategory", "5"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(rowWithUnknownCategory);

        // then
        assertEquals(ProductCategory.Other, parsed.category());
    }

    private static Taxonomy roundTrip(Taxonomy original) {
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        List<String[]> rows = loader.readHeadersAndRows(';').getSecond();
        return TaxonomyParser.fromCsvRow(rows.get(0));
    }
}
