package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCreateLinksTest {

    @Test
    void warehouseLinksOfAStoreAdminLiveUnderTheDashboard() {
        // when
        DeliveryCreateLinks links = DeliveryCreateLinks.of(false, "store-1", "Acme", null, null);

        // then
        assertThat(links.dropship()).isFalse();
        assertThat(links.items()).isEqualTo("/dashboard/deliveries/create/Acme");
        assertThat(links.purchase()).isEqualTo("/dashboard/deliveries/create/Acme/purchase");
        assertThat(links.validate()).isEqualTo("/dashboard/deliveries/create/Acme/purchase/validate");
        assertThat(links.confirm()).isEqualTo("/dashboard/deliveries/create/Acme/purchase/confirm");
        assertThat(links.manual()).isEqualTo("/dashboard/deliveries/create/Acme/manual");
        assertThat(links.save()).isEqualTo("/dashboard/deliveries/create/Acme/manual/save");
        assertThat(links.back()).isEqualTo("/dashboard/deliveries/create/Acme/back");
        assertThat(links.fulfilment()).isEqualTo("/dashboard/deliveries/create/Acme/fulfilment");
        assertThat(links.backHref()).isEqualTo("/dashboard/deliveries/preview");
        assertThat(links.deliveryDetails("d-1")).isEqualTo("/dashboard/deliveries/details?deliveryId=d-1");
    }

    @Test
    void superAdminLinksAreStoreScoped() {
        // when
        DeliveryCreateLinks links = DeliveryCreateLinks.of(true, "store-9", "Acme", "o-1", "order");

        // then
        assertThat(links.items()).isEqualTo("/dashboard/store/store-9/deliveries/create/Acme?order=o-1&from=order");
        assertThat(links.save()).isEqualTo("/dashboard/store/store-9/deliveries/create/Acme/manual/save");
        assertThat(links.order()).isEqualTo("/dashboard/store/store-9/orders/o-1");
        assertThat(links.backHref()).isEqualTo("/dashboard/store/store-9/orders/o-1");
        assertThat(links.preview()).isEqualTo("/dashboard/store/store-9/deliveries/preview");
    }

    @Test
    void dropshipFromThePreviewGoesBackToThePreviewAndUnknownFromIsIgnored() {
        // when
        DeliveryCreateLinks fromPreview = DeliveryCreateLinks.of(false, "s", "Acme", "o-1", null);
        DeliveryCreateLinks unknown = DeliveryCreateLinks.of(false, "s", "Acme", "o-1", "https://evil.example");
        DeliveryCreateLinks warehouseFromOrder = DeliveryCreateLinks.of(false, "s", "Acme", null, "order");

        // then
        assertThat(fromPreview.dropship()).isTrue();
        assertThat(fromPreview.items()).isEqualTo("/dashboard/deliveries/create/Acme?order=o-1");
        assertThat(fromPreview.backHref()).isEqualTo("/dashboard/deliveries/preview");
        assertThat(unknown.fromOrder()).isFalse();
        assertThat(unknown.items()).doesNotContain("evil");
        assertThat(warehouseFromOrder.fromOrder()).isFalse();
        assertThat(DeliveryCreateLinks.of(false, "s", "Acme", "  ", null).dropship()).isFalse();
    }

    @Test
    void encodesASupplierWithSpacesSlashesAndPolishLetters() {
        // when
        DeliveryCreateLinks links = DeliveryCreateLinks.of(false, "s", "Hurt Łódź/2 #1", "o 1&x", null);

        // then
        assertThat(links.purchase()).isEqualTo("/dashboard/deliveries/create/Hurt%20%C5%81%C3%B3d%C5%BA%2F2%20%231/purchase");
        assertThat(links.items()).endsWith("?order=o%201%26x");
    }
}
