package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.taxonomy.Categorized;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryHelpersTest {

    @Test
    void hasCategoryMatchesExactKey() {
        // given
        OrderItem service = orderItemWithCategory(Categorized.SERVICES);
        OrderItem laptop = orderItemWithCategory("Laptops");

        // when / then
        assertThat(service.hasCategory("Services")).isTrue();
        assertThat(laptop.hasCategory("Services")).isFalse();
    }

    @Test
    void servicePredicatesAreFalseForCategoryOutsideTheLegacyEnum() {
        // given
        OrderItem unknown = new OrderItem("order-1", "Smartwatches", "Watch 5", 1, 900.0, "SKU-1", false);

        // when / then
        assertThat(unknown.isServiceGroup()).isFalse();
        assertThat(unknown.isService()).isFalse();
        assertThat(unknown.isProduct()).isTrue();
    }

    @Test
    void copiedOrderItemKeepsCategoryOutsideTheLegacyEnum() {
        // given
        OrderItem source = new OrderItem("order-1", "Smartwatches", "Watch 5", 2, 900.0, "SKU-1", false);

        // when
        OrderItem copy = new OrderItem("order-2", source, 1);

        // then
        assertThat(copy.getCategory()).isEqualTo("Smartwatches");
    }

    @Test
    void updatedOrderItemKeepsCategoryOutsideTheLegacyEnum() {
        // given
        OrderItem item = new OrderItem("order-1", "Laptops", "Old", 1, 100.0, "SKU-1", false);
        OrderItem other = new OrderItem("order-1", "Smartwatches", "Watch 5", 1, 900.0, "SKU-2", false);

        // when
        item.updateAllFields(other);

        // then
        assertThat(item.getCategory()).isEqualTo("Smartwatches");
    }

    @Test
    void itemWithNullCategoryIsProductWithoutException() {
        // given
        BasketItem item = new BasketItem();

        // when / then
        assertThat(item.getCategory()).isNull();
        assertThat(item.isProduct()).isTrue();
        assertThat(item.isService()).isFalse();
        assertThat(item.hasCategory("Services")).isFalse();
    }

    @Test
    void isProductIsExactInverseOfIsService() {
        // given
        OrderItem service = orderItemWithCategory(Categorized.SERVICES);
        OrderItem laptop = orderItemWithCategory("Laptops");

        // when / then
        assertThat(service.isService()).isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isProduct()).isTrue();
    }

    @Test
    void serviceCategoryAndServiceGroupStayDistinctPredicates() {
        // given
        OrderItem laptop = orderItemWithCategory("Laptops");
        OrderItem service = orderItemWithCategory(Categorized.SERVICES);

        // when / then
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isServiceGroup()).isFalse();
        assertThat(service.isService()).isTrue();
        assertThat(service.isServiceGroup()).isTrue();
    }

    @Test
    void basketItemServicePredicatesMatchOrderItemBehaviour() {
        // given
        BasketItem service = basketItemWithCategory(Categorized.SERVICES);
        BasketItem laptop = basketItemWithCategory("Laptops");

        // when / then
        assertThat(service.hasCategory("Services")).isTrue();
        assertThat(service.isService()).isTrue();
        assertThat(service.isProduct()).isFalse();
        assertThat(laptop.isService()).isFalse();
        assertThat(laptop.isProduct()).isTrue();
    }

    private OrderItem orderItemWithCategory(String category) {
        OrderItem item = new OrderItem();
        item.setCategory(category);
        return item;
    }

    private BasketItem basketItemWithCategory(String category) {
        BasketItem item = new BasketItem();
        item.setCategory(category);
        return item;
    }
}
