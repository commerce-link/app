package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.receipts.ReceiptFixtures.STORE_EMAIL;
import static pl.commercelink.receipts.ReceiptFixtures.b2cOrder;
import static pl.commercelink.receipts.ReceiptFixtures.posOrder;

class BuyerEmailTest {

    @Test
    void treatsTheStoresOwnEmailOnAPosOrderAsNoEmail() {
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("  Sklep@Example.com ");

        assertThat(BuyerEmail.of(order, STORE_EMAIL)).isNull();
    }

    @Test
    void keepsTheCustomersEmailOnAPosOrder() {
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("klient@example.com");

        assertThat(BuyerEmail.of(order, STORE_EMAIL)).isEqualTo("klient@example.com");
    }

    @Test
    void neverDropsTheEmailOfAnOrderFromAnotherChannel() {
        Order order = b2cOrder(100);
        order.getBillingDetails().setEmail(STORE_EMAIL);

        assertThat(BuyerEmail.of(order, STORE_EMAIL)).isEqualTo(STORE_EMAIL);
    }

    @Test
    void blankEmailIsNoEmail() {
        Order order = b2cOrder(100);
        order.getBillingDetails().setEmail("  ");

        assertThat(BuyerEmail.of(order, (String) null)).isNull();
    }
}
