package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GOLDEN characterization of the PERSISTED category round-trip on {@link Product} and
 * {@link CategoryDefinition}, plus the {@link DynamoDbEnumListConverter} contract exercised via
 * {@link ProductGroupListConverter}. FREEZES current behavior: where an assertion would disagree
 * with reality, the assertion is corrected to match the code, never vice versa. TEST-ONLY, no
 * production code touched.
 *
 * Frozen contracts:
 *  - Product category: setCategory/getCategory and the (categoryId, category) constructor preserve
 *    the exact ProductCategory constant (plain field, no converter on the getter).
 *  - CategoryDefinition category: setCategory/getCategory preserve the exact ProductCategory constant.
 *  - DynamoDbEnumListConverter (ProductGroupListConverter): convert maps each enum to its name(),
 *    unconvert maps each token through Enum.valueOf WITHOUT try/catch, so the round-trip is the
 *    identity and an unknown token throws IllegalArgumentException (NO graceful fallback).
 *
 * Pure logic over real enum/entity objects (no Spring context, no AWS clients). MockitoExtension is
 * wired per project convention even though this surface needs no collaborators.
 */
@ExtendWith(MockitoExtension.class)
class GoldenPersistedCategoryTest {

    /** FROZEN: Product.setCategory/getCategory preserves the exact constant for every category. */
    @Test
    void productCategorySetGetIsIdentityForEveryCategory() {
        for (ProductCategory category : ProductCategory.values()) {
            // given
            Product product = new Product();

            // when
            product.setCategory(category);

            // then
            assertSame(category, product.getCategory(),
                    "Product set->get must return the same constant for " + category.name());
        }
    }

    /**
     * FROZEN: the (categoryId, category) constructor stores the category verbatim and getCategory
     * returns the same constant (the getter is a plain field read, no converter).
     */
    @Test
    void productConstructorPreservesCategory() {
        // given
        ProductCategory category = ProductCategory.GPU;

        // when
        Product product = new Product("cat-1", category);

        // then
        assertSame(category, product.getCategory());
        assertEquals("cat-1", product.getCategoryId());
    }

    /** FROZEN: CategoryDefinition.setCategory/getCategory preserves the exact constant. */
    @Test
    void categoryDefinitionCategorySetGetIsIdentityForEveryCategory() {
        for (ProductCategory category : ProductCategory.values()) {
            // given
            CategoryDefinition definition = new CategoryDefinition();

            // when
            definition.setCategory(category);

            // then
            assertSame(category, definition.getCategory(),
                    "CategoryDefinition set->get must return the same constant for " + category.name());
        }
    }

    /** FROZEN: convert maps each ProductGroup to its name() in order. */
    @Test
    void converterConvertMapsEnumsToNames() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<ProductGroup> groups = List.of(ProductGroup.Computers, ProductGroup.Furniture, ProductGroup.Services);

        // when
        List<String> tokens = converter.convert(groups);

        // then
        assertIterableEquals(List.of("Computers", "Furniture", "Services"), tokens);
    }

    /**
     * FROZEN: unconvert maps each token through Enum.valueOf, reconstructing the exact constants in
     * order (no try/catch, no normalization).
     */
    @Test
    void converterUnconvertMapsNamesToEnums() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<String> tokens = List.of("Computers", "Furniture", "Services");

        // when
        List<ProductGroup> groups = converter.unconvert(tokens);

        // then
        assertIterableEquals(List.of(ProductGroup.Computers, ProductGroup.Furniture, ProductGroup.Services), groups);
    }

    /** FROZEN: convert->unconvert is the identity for every ProductGroup constant. */
    @Test
    void converterRoundTripIsIdentityForEveryProductGroup() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<ProductGroup> groups = List.of(ProductGroup.values());

        // when
        List<ProductGroup> roundTripped = converter.unconvert(converter.convert(groups));

        // then
        assertIterableEquals(groups, roundTripped);
    }

    /**
     * FROZEN: unconvert uses Enum.valueOf WITHOUT try/catch, so an unknown token throws
     * IllegalArgumentException -- there is NO graceful fallback / skip.
     */
    @Test
    void converterUnconvertThrowsOnUnknownToken() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<String> tokens = List.of("Computers", "NotARealGroup");

        // when / then
        assertThrows(IllegalArgumentException.class, () -> converter.unconvert(tokens));
    }

    /**
     * FROZEN: valueOf is exact-case (no case folding). A wrong-case variant of a real constant is
     * still unknown and throws on unconvert.
     */
    @Test
    void converterUnconvertThrowsOnWrongCaseToken() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<String> tokens = List.of("computers");

        // when / then
        assertThrows(IllegalArgumentException.class, () -> converter.unconvert(tokens));
    }
}
