package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBuilderDiscountTest {

    private static final String STORE_ID = "store-1";

    @Test
    void builderCopiesDiscountCodeAndAmountFromBasket() {
        // given
        Basket basket = discountedBasket();

        // when
        Order order = new Order.Builder(store(), basket).build();

        // then
        assertThat(order.getDiscountCode()).isEqualTo("LATO-10");
        assertThat(order.getDiscountAmount()).isEqualTo(38.97);
    }

    @Test
    void builderLeavesDiscountEmptyWithoutCode() {
        // given
        Basket basket = basket(new BasketItem("pim-gpu", "GPU", "MFN-GPU", "Karty graficzne", 129.90, 0, 3, "catalog-1", 2, false));

        // when
        Order order = new Order.Builder(store(), basket).build();

        // then
        assertThat(order.getDiscountCode()).isNull();
        assertThat(order.getDiscountAmount()).isZero();
    }

    @Test
    void totalPriceIsAfterDiscount() {
        // given
        Basket basket = discountedBasket();

        // when
        Order order = new Order.Builder(store(), basket).build();

        // then
        assertThat(order.getTotalPrice()).isEqualTo(350.73);
    }

    @Test
    void splitDoesNotCopyDiscount() {
        // given
        Order order = new Order.Builder(store(), discountedBasket()).build();

        // when
        Order split = order.createSplit();

        // then
        assertThat(split.getDiscountCode()).isNull();
        assertThat(split.getDiscountAmount()).isZero();
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        return store;
    }

    private static Basket discountedBasket() {
        Basket basket = basket(new BasketItem("pim-gpu", "GPU", "MFN-GPU", "Karty graficzne", 116.91, 0, 3, "catalog-1", 2, false));
        basket.setDiscountCode("LATO-10");
        basket.setDiscountAmount(38.97);
        return basket;
    }

    private static Basket basket(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBillingDetails(new BillingDetails());
        basket.setShippingDetails(new ShippingDetails());
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }
}
