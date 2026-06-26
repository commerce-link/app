package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GOLDEN characterization of the Phase-B retype of {@link WarehouseItem}'s persisted category from the
 * {@code ProductCategory} enum to a plain {@code String categoryKey}. FREEZES the no-migration contract:
 * the Java vocabulary is {@code categoryKey}, but the PERSISTED DynamoDB attribute stays "category" and
 * is now a plain string (no enum converter), so existing rows — whose "category" value was already the
 * enum {@code name()} — read back byte-identically as the key. TEST-ONLY, no live AWS.
 */
class GoldenWarehouseItemCategoryAttributeTest {

    /** FROZEN: set->get categoryKey returns the same value (plain field, no converter). */
    @Test
    void categoryKeySetGetRoundTrips() {
        // given
        WarehouseItem item = new WarehouseItem();

        // when
        item.setCategoryKey("GPU");

        // then
        assertEquals("GPU", item.getCategoryKey());
    }

    /**
     * FROZEN (NO MIGRATION): the retyped {@code categoryKey} field persists under the legacy DynamoDB
     * attribute "category", and is a PLAIN string (the {@code @DynamoDBTypeConvertedEnum} is gone), so an
     * existing "category" row whose value was the enum name reads back unchanged as the key.
     */
    @Test
    void categoryKeyPersistsUnderLegacyCategoryAttributeAsPlainString() throws NoSuchFieldException {
        // given
        Field categoryKey = WarehouseItem.class.getDeclaredField("categoryKey");

        // when
        DynamoDBAttribute attribute = categoryKey.getAnnotation(DynamoDBAttribute.class);
        DynamoDBTypeConvertedEnum converter = categoryKey.getAnnotation(DynamoDBTypeConvertedEnum.class);

        // then
        assertNotNull(attribute, "categoryKey must stay a persisted attribute");
        assertEquals("category", attribute.attributeName(),
                "categoryKey must persist under the legacy DynamoDB attribute \"category\" (zero migration)");
        assertNull(converter, "categoryKey must be a plain string, not a converted enum");
    }

    /** FROZEN: the constructor's 3rd positional argument (the old category slot) sets categoryKey. */
    @Test
    void constructorPopulatesCategoryKey() {
        // given / when
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", "Memory", "Name", "ean", "mfn", 10.0, 1);

        // then
        assertEquals("Memory", item.getCategoryKey());
    }

    /** FROZEN: a fresh/blank item defaults to the Other key, matching the legacy enum default. */
    @Test
    void defaultCategoryKeyIsOther() {
        // given / when
        WarehouseItem item = new WarehouseItem();

        // then
        assertEquals("Other", item.getCategoryKey());
    }
}
