package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GOLDEN characterization of the T5 rename label -> subcategory on {@link Product}.
 * FREEZES the no-migration contract: the Java vocabulary is `subcategory`, but the PERSISTED
 * DynamoDB attribute stays "label", so existing rows read back without any data migration.
 * TEST-ONLY, no live AWS.
 *
 * Frozen contracts:
 *  - setSubcategory/getSubcategory is the identity round-trip (plain field, no converter).
 *  - the `subcategory` field is annotated @DynamoDBAttribute("label"): the attribute name is the
 *    exact lever DynamoDBMapper uses to (un)marshal, so a write via setSubcategory lands in
 *    attribute "label" and an existing "label" row reads back through getSubcategory.
 *  - the full constructor's 6th positional argument (the old `label` slot) populates subcategory.
 */
class GoldenProductSubcategoryAttributeTest {

    /** FROZEN: set->get subcategory returns the same value. */
    @Test
    void subcategorySetGetRoundTrips() {
        // given
        Product product = new Product();

        // when
        product.setSubcategory("Gaming Laptops");

        // then
        assertEquals("Gaming Laptops", product.getSubcategory());
    }

    /**
     * FROZEN (NO MIGRATION): the renamed `subcategory` field persists under the legacy DynamoDB
     * attribute "label", so old rows read as subcategory and new writes land in "label".
     */
    @Test
    void subcategoryPersistsUnderLegacyLabelAttribute() throws NoSuchFieldException {
        // given
        Field subcategory = Product.class.getDeclaredField("subcategory");

        // when
        DynamoDBAttribute annotation = subcategory.getAnnotation(DynamoDBAttribute.class);

        // then
        assertEquals("label", annotation.attributeName(),
                "subcategory must persist under the legacy DynamoDB attribute \"label\" (zero migration)");
    }

    /** FROZEN: the full constructor's 6th positional argument (old `label` slot) sets subcategory. */
    @Test
    void fullConstructorPopulatesSubcategory() {
        // given / when
        Product product = new Product("cat-1", "pim-1", "ean-1", "mfn-1", "Brand", "Gaming Laptops", "Name",
                ProductCategory.Laptops.name(), "default");

        // then
        assertEquals("Gaming Laptops", product.getSubcategory());
    }
}
