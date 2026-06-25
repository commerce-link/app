package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ItemType;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The order item no longer persists the {@link ProductCategory} enum. It freezes a sort
 * {@code sequenceNumber} and an {@link ItemType} at creation, and falls back to the legacy persisted
 * {@code category} attribute for rows written before the cut (no migration — DynamoDB is schemaless).
 */
class OrderItemCategorizationTest {

    private static OrderItem orderItem(ProductCategory category) {
        return new OrderItem(null, category, "n", 1, 0, null, false);
    }

    @Test
    void freezesSequenceNumberFromCategoryAtCreation() {
        // given / then
        // same frozen sort values as the legacy ordinal (GoldenSweepCategoryOrdinalSortKeysTest).
        assertThat(orderItem(ProductCategory.CPU).getSequenceNumber()).isEqualTo(0);
        assertThat(orderItem(ProductCategory.Other).getSequenceNumber()).isEqualTo(10);
        assertThat(orderItem(ProductCategory.Services).getSequenceNumber()).isEqualTo(11);
        assertThat(orderItem(ProductCategory.Laptops).getSequenceNumber()).isEqualTo(12);
    }

    @Test
    void freezesItemTypeFromCategoryAtCreation() {
        // given / then
        assertThat(orderItem(ProductCategory.Services).getItemType()).isEqualTo(ItemType.SERVICE);
        assertThat(orderItem(ProductCategory.Services).isService()).isTrue();
        assertThat(orderItem(ProductCategory.Services).isProduct()).isFalse();
        assertThat(orderItem(ProductCategory.CPU).getItemType()).isEqualTo(ItemType.PRODUCT);
        assertThat(orderItem(ProductCategory.CPU).isService()).isFalse();
        assertThat(orderItem(ProductCategory.CPU).isProduct()).isTrue();
    }

    @Test
    void persistedSequenceNumberFieldWinsOverLegacyCategory() {
        // given
        // simulate a freshly written row: frozen field present.
        OrderItem item = new OrderItem();
        item.setSequenceNumber(12);
        item.setLegacyCategoryKey("CPU");

        // then
        assertThat(item.getSequenceNumber()).isEqualTo(12);
    }

    @Test
    void legacyRowFallsBackToCategoryAttributeForSequenceAndType() {
        // given
        // simulate a row written BEFORE the cut: only the legacy "category" attribute is set.
        OrderItem legacyService = new OrderItem();
        legacyService.setLegacyCategoryKey("Services");
        OrderItem legacyProduct = new OrderItem();
        legacyProduct.setLegacyCategoryKey("Laptops");

        // then
        assertThat(legacyService.getSequenceNumber()).isEqualTo(11);
        assertThat(legacyService.isService()).isTrue();
        assertThat(legacyProduct.getSequenceNumber()).isEqualTo(12);
        assertThat(legacyProduct.isService()).isFalse();
    }

    @Test
    void itemWithNoCategorizationDegradesToOtherProduct() {
        // given
        OrderItem blank = new OrderItem();

        // then
        // matches the old default (category defaulted to Other, a product).
        assertThat(blank.getSequenceNumber()).isEqualTo(ProductCategory.Other.ordinal());
        assertThat(blank.isService()).isFalse();
        assertThat(blank.isProduct()).isTrue();
    }
}
