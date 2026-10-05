package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;

import static org.assertj.core.api.Assertions.assertThat;

/** Addresses of an order, including the one a printed card's QR code carries. */
class OrderLinksTest {

    @Test
    void scanAddressNamesTheStoreAndTheOrder() {
        assertThat(OrderLinks.scan("uma2dqukxr", "o-1")).isEqualTo("/dashboard/scan/orders/uma2dqukxr/o-1");
    }

    @Test
    void scanUrlIsAbsoluteOnTheAppDomain() {
        assertThat(OrderLinks.scanUrl("https://app.commercelink.pl", "s1", "o1"))
                .isEqualTo("https://app.commercelink.pl/dashboard/scan/orders/s1/o1");
    }

    @Test
    void scanUrlDropsATrailingSlashOfTheDomain() {
        assertThat(OrderLinks.scanUrl("https://app.commercelink.pl/", "s1", "o1"))
                .isEqualTo("https://app.commercelink.pl/dashboard/scan/orders/s1/o1");
    }

    @Test
    void detailsOfMatchesTheLinksOfAnOrder() {
        // given
        Order order = new Order("s1");
        order.setOrderId("o1");

        // then
        assertThat(OrderLinks.detailsOf("s1", "o1", false)).isEqualTo(OrderLinks.of(order, false).details())
                .isEqualTo("/dashboard/orders/o1");
        assertThat(OrderLinks.detailsOf("s1", "o1", true)).isEqualTo(OrderLinks.of(order, true).details())
                .isEqualTo("/dashboard/store/s1/orders/o1");
    }
}
