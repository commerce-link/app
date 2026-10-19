package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryHelpersTest {

    /** Maps (category, service flag) to isService() of each class that carries the flag. */
    static Stream<Arguments> serviceCarriers() {
        BiFunction<String, Boolean, Boolean> orderItem = (category, service) -> {
            OrderItem item = orderItemWithCategory(category);
            item.setService(service);
            return item.isService();
        };
        BiFunction<String, Boolean, Boolean> basketItem = (category, service) -> {
            BasketItem item = basketItemWithCategory(category);
            item.setService(service);
            return item.isService();
        };
        BiFunction<String, Boolean, Boolean> fulfilmentSource = (category, service) -> {
            FulfilmentSource source = new FulfilmentSource();
            source.setCategory(category);
            source.setService(service);
            return source.isService();
        };
        return Stream.of(
                Arguments.of("OrderItem", orderItem),
                Arguments.of("BasketItem", basketItem),
                Arguments.of("FulfilmentSource", fulfilmentSource));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceCarriers")
    void serviceFlagAloneMarksItemAsService(String type, BiFunction<String, Boolean, Boolean> isService) {
        // when / then
        assertThat(isService.apply("Laptops", true)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceCarriers")
    void legacyServicesCategoryStringAloneDoesNotMarkItemAsService(String type, BiFunction<String, Boolean, Boolean> isService) {
        // when / then
        assertThat(isService.apply("Services", false)).isFalse();
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

    private static OrderItem orderItemWithCategory(String category) {
        OrderItem item = new OrderItem();
        item.setCategory(category);
        return item;
    }

    private static BasketItem basketItemWithCategory(String category) {
        BasketItem item = new BasketItem();
        item.setCategory(category);
        return item;
    }
}
