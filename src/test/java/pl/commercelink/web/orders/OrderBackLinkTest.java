package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBackLinkTest {

    @Test
    void acceptsOnlyAddressesOfTheOrderList() {
        // when / then: only a path on the order list survives, anything else falls back to the list
        assertThat(OrderBackLink.sanitize("/dashboard/orders?view=Completed&page=3")).isEqualTo("/dashboard/orders?view=Completed&page=3");
        assertThat(OrderBackLink.sanitize("/dashboard/orders")).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.sanitize(null)).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.sanitize("https://evil.example/dashboard/orders")).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.sanitize("/dashboard/orders/abc/delete")).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.sanitize("/dashboard/ordersX")).isEqualTo("/dashboard/orders");
    }

    @Test
    void rejectsForeignPathsDoubleSlashesAndOverlongValues() {
        // when / then
        assertThat(OrderBackLink.sanitize("/dashboard/warehouse")).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?q=a//b")).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?q=" + "x".repeat(300))).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?status=New&page=2")).isEqualTo("/dashboard/orders?status=New&page=2");
    }
}
