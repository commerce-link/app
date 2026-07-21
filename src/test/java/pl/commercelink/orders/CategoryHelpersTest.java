package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryHelpersTest {

    @Test
    void serviceFlagAloneMarksOrderItemAsService() {
        // given
        OrderItem item = orderItemWithCategory("Laptops");
        item.setService(true);

        // when / then
        assertThat(item.isService()).isTrue();
    }

    @Test
    void legacyServicesCategoryStringAloneDoesNotMarkOrderItemAsService() {
        // given
        OrderItem item = orderItemWithCategory("Services");

        // when / then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void serviceFlagAloneMarksBasketItemAsService() {
        // given
        BasketItem item = basketItemWithCategory("Laptops");
        item.setService(true);

        // when / then
        assertThat(item.isService()).isTrue();
    }

    @Test
    void legacyServicesCategoryStringAloneDoesNotMarkBasketItemAsService() {
        // given
        BasketItem item = basketItemWithCategory("Services");

        // when / then
        assertThat(item.isService()).isFalse();
    }

    @Test
    void serviceFlagAloneMarksFulfilmentSourceAsService() {
        // given
        FulfilmentSource source = new FulfilmentSource();
        source.setCategory("Laptops");
        source.setService(true);

        // when / then
        assertThat(source.isService()).isTrue();
    }

    @Test
    void legacyServicesCategoryStringAloneDoesNotMarkFulfilmentSourceAsService() {
        // given
        FulfilmentSource source = new FulfilmentSource();
        source.setCategory("Services");

        // when / then
        assertThat(source.isService()).isFalse();
    }

    @Test
    void isProductIsTheExactInverseOfTheServiceFlag() {
        // given
        OrderItem product = orderItemWithCategory("Laptops");
        OrderItem service = orderItemWithCategory("Usługi dodatkowe");
        service.setService(true);

        // when / then
        assertThat(product.isProduct()).isTrue();
        assertThat(service.isProduct()).isFalse();
    }

    @Test
    void isProductIsTheExactInverseOfTheServiceFlagForBasketItem() {
        // given
        BasketItem product = basketItemWithCategory("Laptops");
        BasketItem service = basketItemWithCategory("Usługi dodatkowe");
        service.setService(true);

        // when / then
        assertThat(product.isProduct()).isTrue();
        assertThat(service.isProduct()).isFalse();
    }

    @Test
    void itemWithNullCategoryIsNotServiceWithoutException() {
        // given
        BasketItem item = new BasketItem();

        // when / then
        assertThat(item.getCategory()).isNull();
        assertThat(item.isService()).isFalse();
    }

    @Test
    void copiedOrderItemKeepsServiceFlagAndCategory() {
        // given
        OrderItem source = new OrderItem("order-1", "Montaż", "Montaż PC", 2, 900.0, "SKU-1", false);
        source.setService(true);

        // when
        OrderItem copy = new OrderItem("order-2", source, 1);

        // then
        assertThat(copy.isService()).isTrue();
        assertThat(copy.getCategory()).isEqualTo("Montaż");
    }

    @Test
    void updatedOrderItemTakesServiceFlagAndCategoryFromTheOtherItem() {
        // given
        OrderItem item = new OrderItem("order-1", "Laptops", "Old", 1, 100.0, "SKU-1", false);
        OrderItem other = new OrderItem("order-1", "Montaż", "Montaż PC", 1, 900.0, "SKU-2", false);
        other.setService(true);

        // when
        item.updateAllFields(other);

        // then
        assertThat(item.isService()).isTrue();
        assertThat(item.getCategory()).isEqualTo("Montaż");
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
