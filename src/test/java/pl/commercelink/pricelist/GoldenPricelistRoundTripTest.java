package pl.commercelink.pricelist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.taxonomy.ProductCategory;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization of the PRICELIST CSV category round-trip. FREEZES current behavior; if an
 * assertion disagreed with reality, the assertion was corrected to match the code, never vice versa.
 *
 * Round-trip contract (TEST-ONLY, no production code touched):
 *  - WRITE side: {@link AvailabilityAndPrice#asStringArray()} serializes the category as
 *    {@code category.toString()} (AvailabilityAndPrice.java:107). For a Java enum without an
 *    overridden toString(), {@code toString() == name()}, so the serialized token equals the
 *    constant name for all 52 {@link ProductCategory} constants.
 *  - READ side: {@code PricelistRepository#mapFieldsToObject} deserializes via
 *    {@code "OS".equals(token) ? "Software" : CategoryCatalog.isKnown(token) ? token : throw}.
 *
 * Therefore the round-trip {@code read(asStringArray()[6])} is the identity for every constant.
 * The "OS" alias is a one-way READ-only mapping (no constant serializes to "OS"); unknown tokens
 * throw {@link IllegalArgumentException} via the {@code CategoryCatalog.isKnown} guard.
 *
 * Pure logic over real enum objects (no Spring context, no AWS clients). MockitoExtension is wired
 * per project convention even though this surface needs no collaborators.
 */
@ExtendWith(MockitoExtension.class)
class GoldenPricelistRoundTripTest {

    private static final int CATEGORY_FIELD_INDEX = 6;

    /** FROZEN: ProductCategory has exactly 52 constants. */
    @Test
    void productCategoryHasFiftyTwoConstants() {
        // when
        int count = ProductCategory.values().length;

        // then
        assertEquals(52, count);
    }

    /**
     * FROZEN: toString() == name() for every constant (no overridden toString), which is what makes
     * the AvailabilityAndPrice write side stable and round-trippable.
     */
    @Test
    void everyCategoryToStringEqualsName() {
        for (ProductCategory category : ProductCategory.values()) {
            // when
            String serialized = category.toString();

            // then
            assertEquals(category.name(), serialized,
                    "toString() must equal name() for round-trip stability of " + category.name());
        }
    }

    /**
     * FROZEN: the exact write token = AvailabilityAndPrice.asStringArray()[6], fed back through the
     * repository's valueOf read path, is the identity for all 52 constants. This is the real CSV
     * round-trip mirror: serialize via the SUT, deserialize via the SUT's valueOf contract.
     */
    @Test
    void writeThenReadIsIdentityForEveryCategory() {
        for (ProductCategory category : ProductCategory.values()) {
            // given
            AvailabilityAndPrice row = new AvailabilityAndPrice(
                    "pim", "ean", "mfn", "brand", "label", "name", category.name(), 100L, 5L, 1, 90L);

            // when
            String serializedCategory = row.asStringArray()[CATEGORY_FIELD_INDEX];
            String deserialized = readCategory(serializedCategory);

            // then
            assertEquals(category.name(), serializedCategory);
            assertEquals(category.name(), deserialized,
                    "round-trip must return the same key for " + category.name());
        }
    }

    /**
     * FROZEN: the READ-side "OS" alias resolves to Software. This is a one-way mapping -- the WRITE
     * side never emits "OS" (Software serializes to "Software"), so it only matters on deserialize.
     */
    @Test
    void osAliasReadsAsSoftware() {
        // given
        String osToken = "OS";

        // when
        String deserialized = readCategory(osToken);

        // then
        assertEquals("Software", deserialized);
        assertEquals("Software", ProductCategory.Software.toString());
        assertTrue(!osToken.equals(ProductCategory.Software.name()),
                "OS is an alias, not the Software constant name");
    }

    /**
     * FROZEN: an unknown category token (anything that is neither a constant name nor the "OS" alias)
     * throws IllegalArgumentException through ProductCategory.valueOf on the read path.
     */
    @Test
    void unknownCategoryTokenThrowsOnRead() {
        // given
        String unknownToken = "NotARealCategory";

        // when / then
        assertThrows(IllegalArgumentException.class, () -> readCategory(unknownToken));
    }

    /**
     * FROZEN: valueOf is exact-case (no case folding). A wrong-case variant of a real constant is
     * still unknown and throws -- documents that the round-trip relies on exact toString()/name()
     * casing, not on the enum's case-insensitive eq() helper.
     */
    @Test
    void wrongCaseCategoryTokenThrowsOnRead() {
        // given
        String wrongCaseToken = "cpu";

        // when / then
        assertThrows(IllegalArgumentException.class, () -> readCategory(wrongCaseToken));
    }

    /**
     * Drives the REAL READ path: a one-row pricelist CSV whose category cell is {@code token} is fed
     * through PricelistRepository.find() (which calls the private mapFieldsToObject:111 deserialization
     * branch). Returns the deserialized category of the single row. No production code touched.
     */
    private static String readCategory(String token) {
        FileStorage fileStorage = mock(FileStorage.class);
        PricelistRepository repository = new PricelistRepository(fileStorage, "bucket");
        String key = "catalogId/pricelistId.csv";
        // header order mirrors AvailabilityAndPrice.HEADERS; category is column index 6.
        String csv = "PimId;EAN;Mfn;Brand;Label;Name;Category;Price;Qty;Estimated Delivery Days;Lowest 30 Days Price\n"
                + "pim;ean;mfn;brand;label;name;" + token + ";100;5;1;90";
        when(fileStorage.canRead("bucket", key)).thenReturn(true);
        when(fileStorage.get("bucket", key))
                .thenReturn(new InputStreamReader(new ByteArrayInputStream(csv.getBytes())));
        return repository.find("catalogId", "pricelistId").getAvailabilityAndPrices().get(0).getCategory();
    }
}
