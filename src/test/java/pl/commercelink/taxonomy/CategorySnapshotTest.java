package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure helper that derives the FROZEN per-item categorization snapshot (sort sequence + product/service
 * type) from a {@link ProductCategory} or its string key, so order/basket items can persist primitives
 * instead of the enum and still reproduce the legacy sort order and Services branching.
 */
class CategorySnapshotTest {

    @Test
    void sequenceOfMatchesEnumOrdinalForRepresentativeCategories() {
        // given / then
        // exact frozen sort values, identical to ProductCategory.ordinal() (see GoldenSweepCategoryOrdinalSortKeysTest).
        assertThat(CategorySnapshot.sequenceOf(ProductCategory.CPU)).isEqualTo(0);
        assertThat(CategorySnapshot.sequenceOf(ProductCategory.Other)).isEqualTo(10);
        assertThat(CategorySnapshot.sequenceOf(ProductCategory.Services)).isEqualTo(11);
        assertThat(CategorySnapshot.sequenceOf(ProductCategory.Laptops)).isEqualTo(12);
        assertThat(CategorySnapshot.sequenceOf(ProductCategory.Footrests))
                .isEqualTo(ProductCategory.Footrests.ordinal());
    }

    @Test
    void sequenceOfNullCategoryFallsBackToOther() {
        // given / then
        // a missing category degrades to Other's slot, matching the old default category = Other.
        assertThat(CategorySnapshot.sequenceOf(null)).isEqualTo(ProductCategory.Other.ordinal());
    }

    @Test
    void sequenceOfKeyResolvesNameToOrdinal() {
        // given / then
        assertThat(CategorySnapshot.sequenceOfKey("CPU")).isEqualTo(0);
        assertThat(CategorySnapshot.sequenceOfKey("Services")).isEqualTo(11);
        assertThat(CategorySnapshot.sequenceOfKey("Laptops")).isEqualTo(12);
    }

    @Test
    void sequenceOfKeyFallsBackToOtherForNullOrUnknown() {
        // given / then
        assertThat(CategorySnapshot.sequenceOfKey(null)).isEqualTo(ProductCategory.Other.ordinal());
        assertThat(CategorySnapshot.sequenceOfKey("NotARealCategory")).isEqualTo(ProductCategory.Other.ordinal());
    }

    @Test
    void typeOfMapsServicesToServiceAndEverythingElseToProduct() {
        // given / then
        assertThat(CategorySnapshot.typeOf(ProductCategory.Services)).isEqualTo(ItemType.SERVICE);
        assertThat(CategorySnapshot.typeOf(ProductCategory.CPU)).isEqualTo(ItemType.PRODUCT);
        assertThat(CategorySnapshot.typeOf(ProductCategory.Other)).isEqualTo(ItemType.PRODUCT);
        // null degrades to PRODUCT (only the Services group is a service).
        assertThat(CategorySnapshot.typeOf(null)).isEqualTo(ItemType.PRODUCT);
    }

    @Test
    void typeOfKeyMapsServicesKeyToServiceAndOthersToProduct() {
        // given / then
        assertThat(CategorySnapshot.typeOfKey("Services")).isEqualTo(ItemType.SERVICE);
        assertThat(CategorySnapshot.typeOfKey("CPU")).isEqualTo(ItemType.PRODUCT);
        // null / unknown keys are products, never services.
        assertThat(CategorySnapshot.typeOfKey(null)).isEqualTo(ItemType.PRODUCT);
        assertThat(CategorySnapshot.typeOfKey("NotARealCategory")).isEqualTo(ItemType.PRODUCT);
    }
}
