package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryKeyHelpersTest {

    @Test
    void hasCategoryKeyMatchesEnumCategoryCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.hasCategoryKey("Services"))
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(laptop.hasCategoryKey("Services"))
                .isEqualTo(laptop.getCategory() == ProductCategory.Services)
                .isFalse();
    }

    @Test
    void isServiceGroupIsFalseForCategoryKeyOutsideTheEnum() {
        // given
        OrderItem unknown = new OrderItem("order-1", "Smartwatches", "Watch 5", 1, 900.0, "SKU-1", false);

        // when / then
        assertThat(unknown.isServiceGroup()).isFalse();
        assertThat(unknown.isService()).isFalse();
    }

    @Test
    void copiedOrderItemKeepsCategoryKeyOutsideTheEnum() {
        // given
        OrderItem source = new OrderItem("order-1", "Smartwatches", "Watch 5", 2, 900.0, "SKU-1", false);

        // when
        OrderItem copy = new OrderItem("order-2", source, 1);

        // then
        assertThat(copy.getCategoryKey()).isEqualTo("Smartwatches");
    }

    @Test
    void updatedOrderItemKeepsCategoryKeyOutsideTheEnum() {
        // given
        OrderItem item = new OrderItem("order-1", "Laptops", "Old", 1, 100.0, "SKU-1", false);
        OrderItem other = new OrderItem("order-1", "Smartwatches", "Watch 5", 1, 900.0, "SKU-2", false);

        // when
        item.updateAllFields(other);

        // then
        assertThat(item.getCategoryKey()).isEqualTo("Smartwatches");
    }

    @Test
    void basketItemHelpersMatchEnumChecks() {
        // given
        BasketItem service = basketItemWithCategory(ProductCategory.Services);
        BasketItem laptop = basketItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.hasCategoryKey("Services")).isTrue();
        assertThat(service.isService()).isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isProduct()).isTrue();
    }

    @Test
    void basketItemWithNullCategoryIsProductWithoutException() {
        // given
        BasketItem item = new BasketItem();

        // when / then
        assertThat(item.getCategoryKey()).isNull();
        assertThat(item.isProduct()).isTrue();
        assertThat(item.isService()).isFalse();
        assertThat(item.hasCategoryKey("Services")).isFalse();
    }

    @Test
    void isServiceMatchesEnumCategoryCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isService())
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(laptop.isService())
                .isEqualTo(laptop.getCategory() == ProductCategory.Services)
                .isFalse();
    }

    @Test
    void isProductIsExactInverseOfIsServiceCategory() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isProduct())
                .isEqualTo(service.getCategory() != ProductCategory.Services)
                .isFalse();
        assertThat(laptop.isProduct())
                .isEqualTo(laptop.getCategory() != ProductCategory.Services)
                .isTrue();
    }

    @Test
    void isServiceGroupMatchesEnumGroupCheck() {
        // given
        OrderItem service = orderItemWithCategory(ProductCategory.Services);
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isServiceGroup())
                .isEqualTo(service.getCategory().getProductGroup() == ProductGroup.Services)
                .isTrue();
        assertThat(laptop.isServiceGroup())
                .isEqualTo(laptop.getCategory().getProductGroup() == ProductGroup.Services)
                .isFalse();
    }

    @Test
    void serviceCategoryAndServiceGroupStayDistinctPredicates() {
        // given
        OrderItem laptop = orderItemWithCategory(ProductCategory.Laptops);
        OrderItem service = orderItemWithCategory(ProductCategory.Services);

        // when / then
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isServiceGroup()).isFalse();
        assertThat(service.isService()).isTrue();
        assertThat(service.isServiceGroup()).isTrue();
    }

    @Test
    void basketItemServicePredicatesMatchEnumChecks() {
        // given
        BasketItem service = basketItemWithCategory(ProductCategory.Services);
        BasketItem laptop = basketItemWithCategory(ProductCategory.Laptops);

        // when / then
        assertThat(service.isService())
                .isEqualTo(service.getCategory() == ProductCategory.Services)
                .isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isProduct())
                .isEqualTo(laptop.getCategory() != ProductCategory.Services)
                .isTrue();
    }

    private OrderItem orderItemWithCategory(ProductCategory category) {
        OrderItem item = new OrderItem();
        item.setCategoryKey(category.name());
        return item;
    }

    private BasketItem basketItemWithCategory(ProductCategory category) {
        BasketItem item = new BasketItem();
        item.setCategoryKey(category.name());
        return item;
    }
}
