package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.FulfilmentStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ItemBackTest {

    @Test
    void listAddressWithQueryIsKept() {
        // when
        ItemBack back = ItemBack.resolve("/dashboard/warehouse?statuses=all&q=ssd&page=2", FulfilmentStatus.Delivered);

        // then
        assertThat(back.href()).isEqualTo("/dashboard/warehouse?statuses=all&q=ssd&page=2");
        assertThat(back.labelKey()).isEqualTo("nav.warehouse");
    }

    @Test
    void archiveAddressIsKeptWithTheArchiveLabel() {
        // when
        ItemBack back = ItemBack.resolve("/dashboard/warehouse/items/destroyed?period=month", FulfilmentStatus.Destroyed);

        // then
        assertThat(back.href()).isEqualTo("/dashboard/warehouse/items/destroyed?period=month");
        assertThat(back.labelKey()).isEqualTo("warehouse.destroyed.title");
    }

    @Test
    void foreignOrTrickyBackFallsBackToTheDefault() {
        // when / then
        for (String evil : new String[]{"https://evil.example", "//evil.example", "/dashboard/orders",
                "/dashboard/warehouse/../orders", "/dashboard/warehouse//x", "/dashboard/warehouse/items/abc", " /dashboard/warehouse"}) {
            assertThat(ItemBack.resolve(evil, FulfilmentStatus.Delivered).href()).as(evil).isEqualTo("/dashboard/warehouse");
            assertThat(ItemBack.safe(evil)).as(evil).isNull();
        }
    }

    @Test
    void missingBackDependsOnTheStatus() {
        // when / then
        assertThat(ItemBack.resolve(null, FulfilmentStatus.Destroyed))
                .isEqualTo(new ItemBack("/dashboard/warehouse/items/destroyed", "warehouse.destroyed.title"));
        assertThat(ItemBack.resolve(null, FulfilmentStatus.New))
                .isEqualTo(new ItemBack("/dashboard/warehouse", "nav.warehouse"));
    }

    @Test
    void itemHrefCarriesAnEncodedSafeBackOnly() {
        // when / then
        assertThat(ItemBack.itemHref("w-1", "/dashboard/warehouse?q=a+b&statuses=all"))
                .isEqualTo("/dashboard/warehouse/items/w-1?back=%2Fdashboard%2Fwarehouse%3Fq%3Da%2Bb%26statuses%3Dall");
        // a raw space never comes from a link the server built, so it is refused like any other odd address
        assertThat(ItemBack.itemHref("w-1", "/dashboard/warehouse?q=a b")).isEqualTo("/dashboard/warehouse/items/w-1");
        assertThat(ItemBack.itemHref("w-1", "https://evil.example")).isEqualTo("/dashboard/warehouse/items/w-1");
        assertThat(ItemBack.itemHref("w-1", null)).isEqualTo("/dashboard/warehouse/items/w-1");
    }
}
