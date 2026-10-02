package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryBackLinkTest {

    @Test
    void theFilteredListIsKept() {
        // when / then
        assertThat(DeliveryBackLink.sanitize("/dashboard/deliveries?scope=all&q=MH-2026&page=2"))
                .isEqualTo("/dashboard/deliveries?scope=all&q=MH-2026&page=2");
        assertThat(DeliveryBackLink.sanitize("/dashboard/deliveries")).isEqualTo("/dashboard/deliveries");
    }

    @Test
    void foreignOrOverlongAddressesFallBackToTheList() {
        // when / then
        assertThat(DeliveryBackLink.sanitize(null)).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("https://evil.example/dashboard/deliveries")).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("//evil.example")).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("/dashboard/deliveries?next=//evil.example")).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("/dashboard/orders?scope=all")).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("/dashboard/deliveriesX")).isEqualTo(DeliveryBackLink.LIST);
        assertThat(DeliveryBackLink.sanitize("/dashboard/deliveries?q=" + "x".repeat(290))).isEqualTo(DeliveryBackLink.LIST);
    }

    @Test
    void theSuperAdminGoesBackToTheDeliveryQueue() {
        // when / then
        assertThat(DeliveryBackLink.labelKey(true)).isEqualTo("nav.deliveries.queue");
        assertThat(DeliveryBackLink.labelKey(false)).isEqualTo("nav.deliveries");
    }
}
