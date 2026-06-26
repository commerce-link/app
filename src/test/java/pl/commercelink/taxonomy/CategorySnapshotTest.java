package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin delegate over {@link CategoryCatalog} that derives the FROZEN per-item categorization snapshot
 * (sort sequence + product/service type) from a category string key, so order/basket items persist
 * primitives instead of the enum and still reproduce the legacy sort order and Services branching.
 */
class CategorySnapshotTest {

    @Test
    void sequenceOfKeyResolvesNameToOrdinal() {
        // given / then
        // exact frozen sort values, identical to ProductCategory.ordinal() (see GoldenSweepCategoryOrdinalSortKeysTest).
        assertThat(CategorySnapshot.sequenceOfKey("CPU")).isEqualTo(0);
        assertThat(CategorySnapshot.sequenceOfKey("Other")).isEqualTo(10);
        assertThat(CategorySnapshot.sequenceOfKey("Services")).isEqualTo(11);
        assertThat(CategorySnapshot.sequenceOfKey("Laptops")).isEqualTo(12);
        assertThat(CategorySnapshot.sequenceOfKey("Footrests")).isEqualTo(ProductCategory.Footrests.ordinal());
    }

    @Test
    void sequenceOfKeyFallsBackToOtherForNullOrUnknown() {
        // given / then
        assertThat(CategorySnapshot.sequenceOfKey(null)).isEqualTo(ProductCategory.Other.ordinal());
        assertThat(CategorySnapshot.sequenceOfKey("NotARealCategory")).isEqualTo(ProductCategory.Other.ordinal());
    }

    @Test
    void typeOfKeyMapsServicesKeyToServiceAndOthersToProduct() {
        // given / then
        assertThat(CategorySnapshot.typeOfKey("Services")).isEqualTo(ItemType.SERVICE);
        assertThat(CategorySnapshot.typeOfKey("CPU")).isEqualTo(ItemType.PRODUCT);
        assertThat(CategorySnapshot.typeOfKey("Other")).isEqualTo(ItemType.PRODUCT);
        // null / unknown keys are products, never services.
        assertThat(CategorySnapshot.typeOfKey(null)).isEqualTo(ItemType.PRODUCT);
        assertThat(CategorySnapshot.typeOfKey("NotARealCategory")).isEqualTo(ItemType.PRODUCT);
    }
}
