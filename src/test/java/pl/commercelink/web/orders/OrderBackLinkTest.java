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
    void archiveIsAWayBackToo() {
        // when / then
        assertThat(OrderBackLink.sanitize("/dashboard/orders/archive")).isEqualTo("/dashboard/orders/archive");
        assertThat(OrderBackLink.sanitize("/dashboard/orders/archive?q=kowalski&status=Completed"))
                .isEqualTo("/dashboard/orders/archive?q=kowalski&status=Completed");
        assertThat(OrderBackLink.sanitize("/dashboard/orders/archive//evil")).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.sanitize("/dashboard/orders/archivex")).isEqualTo("/dashboard/orders");
        assertThat(OrderBackLink.isArchive("/dashboard/orders/archive?q=x")).isTrue();
        assertThat(OrderBackLink.isArchive("/dashboard/orders?q=x")).isFalse();
    }

    @Test
    void rejectsForeignPathsDoubleSlashesAndOverlongValues() {
        // when / then
        assertThat(OrderBackLink.sanitize("/dashboard/warehouse")).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?q=a//b")).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?q=" + "x".repeat(1100))).isEqualTo(OrderBackLink.LIST);
        assertThat(OrderBackLink.sanitize("/dashboard/orders?status=New&page=2")).isEqualTo("/dashboard/orders?status=New&page=2");
    }

    @Test
    void keepsALongArchiveAddressWithSearchAndCursor() {
        // given
        String href = "/dashboard/orders/archive?status=Completed&q=" + "%C5%82".repeat(95) + "&period=year&after=" + "x".repeat(120) + "&offset=50";

        // when / then
        assertThat(href.length()).isGreaterThan(500);
        assertThat(OrderBackLink.sanitize(href)).isEqualTo(href);
        assertThat(OrderBackLink.sanitize("/dashboard/orders/archive?q=" + "x".repeat(1000))).isEqualTo(OrderBackLink.LIST);
    }
}
