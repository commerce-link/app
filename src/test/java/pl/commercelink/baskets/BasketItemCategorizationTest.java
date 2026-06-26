package pl.commercelink.baskets;

import org.junit.jupiter.api.Test;
import pl.commercelink.taxonomy.ProductCategory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The basket item no longer holds the {@link ProductCategory} enum; it carries a decoupled
 * {@code categoryKey} string (the offer dropdown edits it). Product/service is derived from the key.
 */
class BasketItemCategorizationTest {

    private static BasketItem basketItem(ProductCategory category) {
        return new BasketItem("id", "name", "M", category == null ? null : category.name(), 10.0, 0, 1, null, 1, false);
    }

    @Test
    void storesCategoryAsStringKey() {
        // given / then
        assertThat(basketItem(ProductCategory.CPU).getCategoryKey()).isEqualTo("CPU");
        assertThat(basketItem(ProductCategory.Services).getCategoryKey()).isEqualTo("Services");
        assertThat(basketItem(null).getCategoryKey()).isNull();
    }

    @Test
    void productServiceIsDerivedFromTheKey() {
        // given / then
        assertThat(basketItem(ProductCategory.Services).isService()).isTrue();
        assertThat(basketItem(ProductCategory.Services).isProduct()).isFalse();
        assertThat(basketItem(ProductCategory.CPU).isService()).isFalse();
        assertThat(basketItem(ProductCategory.CPU).isProduct()).isTrue();
        // Other is a product, and a null/absent key is treated as a product (never a service).
        assertThat(basketItem(ProductCategory.Other).isProduct()).isTrue();
        assertThat(basketItem(null).isProduct()).isTrue();
    }

    @Test
    void offerDropdownSetsTheCategoryKeyDirectly() {
        // given
        BasketItem item = new BasketItem();

        // when
        item.setCategoryKey("Services");

        // then
        assertThat(item.getCategoryKey()).isEqualTo("Services");
        assertThat(item.isService()).isTrue();
    }

    @Test
    void blankCategoryKeyFromTheDropdownIsTreatedAsAbsent() {
        // given
        // the offer dropdown's empty option posts "" — preserve the old enum behaviour where that meant "no category".
        BasketItem item = basketItem(ProductCategory.CPU);

        // when
        item.setCategoryKey("");

        // then
        assertThat(item.getCategoryKey()).isNull();
        assertThat(item.isComplete()).isFalse();
    }

    @Test
    void isCompleteRequiresACategoryKey() {
        // given
        BasketItem withoutKey = new BasketItem("id", "name", "M", (String) null, 10.0, 0, 1, null, 1, false);
        BasketItem withKey = basketItem(ProductCategory.CPU);

        // then
        assertThat(withoutKey.isComplete()).isFalse();
        assertThat(withKey.isComplete()).isTrue();
    }
}
