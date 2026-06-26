package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.taxonomy.CategoryCatalog;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GOLDEN characterization of the PERSISTED category on {@link Product}, {@link CategoryDefinition} and
 * {@link ProductCustomAttributeFilter}, plus the {@link ProductGroupListConverter} contract. Phase B
 * retyped these category fields from the {@code ProductCategory} enum to a plain {@code String}
 * categoryKey. FREEZES the no-migration contract: the Java vocabulary is the string key, the PERSISTED
 * DynamoDB attribute stays "category" and is now a plain string (no enum converter), so existing rows —
 * whose "category" value was already the enum {@code name()} — read back byte-identically as the key.
 * TEST-ONLY, no live AWS.
 */
@ExtendWith(MockitoExtension.class)
class GoldenPersistedCategoryTest {

    /** FROZEN: Product.setCategory/getCategory is the identity round-trip for every category key. */
    @Test
    void productCategorySetGetIsIdentityForEveryKey() {
        for (String key : CategoryCatalog.orderedKeys()) {
            // given
            Product product = new Product();

            // when
            product.setCategory(key);

            // then
            assertEquals(key, product.getCategory(), "Product set->get must return the same key for " + key);
        }
    }

    /** FROZEN: the (categoryId, category) constructor stores the key verbatim. */
    @Test
    void productConstructorPreservesCategory() {
        // given / when
        Product product = new Product("cat-1", "GPU");

        // then
        assertEquals("GPU", product.getCategory());
        assertEquals("cat-1", product.getCategoryId());
    }

    /** FROZEN (NO MIGRATION): Product.category persists under attribute "category" as a plain string. */
    @Test
    void productCategoryPersistsUnderCategoryAttributeAsPlainString() throws NoSuchFieldException {
        // given
        Field category = Product.class.getDeclaredField("category");

        // when
        DynamoDBAttribute attribute = category.getAnnotation(DynamoDBAttribute.class);
        DynamoDBTypeConvertedEnum converter = category.getAnnotation(DynamoDBTypeConvertedEnum.class);

        // then
        assertNotNull(attribute);
        assertEquals("category", attribute.attributeName());
        assertNull(converter, "Product.category must be a plain string, not a converted enum");
        assertEquals(String.class, category.getType());
    }

    /** FROZEN: CategoryDefinition.setCategory/getCategory is the identity round-trip for every key. */
    @Test
    void categoryDefinitionCategorySetGetIsIdentityForEveryKey() {
        for (String key : CategoryCatalog.orderedKeys()) {
            // given
            CategoryDefinition definition = new CategoryDefinition();

            // when
            definition.setCategory(key);

            // then
            assertEquals(key, definition.getCategory(), "CategoryDefinition set->get must return the same key for " + key);
        }
    }

    /** FROZEN (NO MIGRATION): CategoryDefinition.category persists under "category" as a plain string. */
    @Test
    void categoryDefinitionCategoryPersistsUnderCategoryAttributeAsPlainString() throws NoSuchFieldException {
        // given
        Field category = CategoryDefinition.class.getDeclaredField("category");

        // when
        DynamoDBAttribute attribute = category.getAnnotation(DynamoDBAttribute.class);
        DynamoDBTypeConvertedEnum converter = category.getAnnotation(DynamoDBTypeConvertedEnum.class);

        // then
        assertNotNull(attribute);
        assertEquals("category", attribute.attributeName());
        assertNull(converter, "CategoryDefinition.category must be a plain string, not a converted enum");
        assertEquals(String.class, category.getType());
    }

    /** FROZEN (NO MIGRATION): ProductCustomAttributeFilter.category persists under "category" as a plain string. */
    @Test
    void productCustomAttributeFilterCategoryPersistsUnderCategoryAttributeAsPlainString() throws NoSuchFieldException {
        // given
        Field category = ProductCustomAttributeFilter.class.getDeclaredField("category");

        // when
        DynamoDBAttribute attribute = category.getAnnotation(DynamoDBAttribute.class);
        DynamoDBTypeConvertedEnum converter = category.getAnnotation(DynamoDBTypeConvertedEnum.class);

        // then
        assertNotNull(attribute);
        assertEquals("category", attribute.attributeName());
        assertNull(converter, "ProductCustomAttributeFilter.category must be a plain string, not a converted enum");
        assertEquals(String.class, category.getType());
    }

    /** FROZEN: the string-list converter is a passthrough (no normalization, no reordering). */
    @Test
    void converterConvertIsIdentityForGroupKeyList() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<String> groupKeys = List.of("Computers", "Furniture", "Services");

        // when
        List<String> tokens = converter.convert(groupKeys);

        // then
        assertIterableEquals(groupKeys, tokens);
    }

    /** FROZEN: convert->unconvert round-trips the exact group-key list unchanged. */
    @Test
    void converterRoundTripIsIdentityForGroupKeys() {
        // given
        ProductGroupListConverter converter = new ProductGroupListConverter();
        List<String> groupKeys = List.of("PcComponents", "Peripherals", "Services");

        // when
        List<String> roundTripped = converter.unconvert(converter.convert(groupKeys));

        // then
        assertIterableEquals(groupKeys, roundTripped);
    }
}
