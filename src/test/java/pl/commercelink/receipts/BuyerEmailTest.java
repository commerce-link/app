package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class BuyerEmailTest {

    @Test
    void treatsTheStoresOwnEmailOnAPosOrderAsNoEmail() {
        // given
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("  Sklep@Example.com ");

        // when
        String email = BuyerEmail.of(order, STORE_EMAIL);

        // then
        assertThat(email).isNull();
    }

    @Test
    void keepsTheCustomersEmailOnAPosOrder() {
        // given
        Order order = posOrder(100);
        order.getBillingDetails().setEmail(" klient@example.com ");

        // when
        String email = BuyerEmail.of(order, STORE_EMAIL);

        // then
        assertThat(email).isEqualTo("klient@example.com");
    }

    @Test
    void neverDropsTheEmailOfAnOrderFromAnotherChannel() {
        // given
        Order order = b2cOrder(100);
        order.getBillingDetails().setEmail(STORE_EMAIL);

        // when
        String email = BuyerEmail.of(order, STORE_EMAIL);

        // then
        assertThat(email).isEqualTo(STORE_EMAIL);
    }

    @Test
    void blankEmailIsNoEmail() {
        // given
        Order order = b2cOrder(100);
        order.getBillingDetails().setEmail("  ");

        // when
        String email = BuyerEmail.of(order, (String) null);

        // then
        assertThat(email).isNull();
    }

    @Test
    void orderWithoutBillingDetailsHasNoEmail() {
        // given
        Order order = new Order(STORE_ID);
        order.setSource(new OrderSource("operator", OrderSourceType.PointOfSale));

        // when
        String email = BuyerEmail.of(order, STORE_EMAIL);

        // then
        assertThat(email).isNull();
    }

    @Test
    void storeWithoutBillingDetailsKeepsThePosOrdersEmail() {
        // given
        Order order = posOrder(100);
        Store store = new Store();

        // when
        String email = BuyerEmail.of(order, store);

        // then
        assertThat(email).isEqualTo(STORE_EMAIL);
    }

    @Test
    void storeOverloadReadsTheStoresBillingEmail() {
        // given
        Order order = posOrder(100);
        Store store = withStoreEmail(new Store());

        // when
        String email = BuyerEmail.of(order, store);

        // then
        assertThat(email).isNull();
    }
}
