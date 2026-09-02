package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentTrackingTemplateTest {

    private static final Path ORDER_DETAILS = Path.of("src/main/resources/templates/orderDetails.html");
    private static final Path STORE_SHIPPING = Path.of("src/main/resources/templates/store-shipping.html");
    private static final Path MESSAGES_PL = Path.of("src/main/resources/messages_pl.properties");
    private static final Path MESSAGES_EN = Path.of("src/main/resources/messages_en.properties");

    private static final List<String> KEYS = List.of(
            "order.shipment.tracking.status",
            "order.shipment.tracking.status.PENDING",
            "order.shipment.tracking.status.ACTIVE",
            "order.shipment.tracking.status.FAILED",
            "store.shipping.webhook.title",
            "store.shipping.webhook.lead",
            "store.shipping.webhook.url",
            "store.shipping.webhook.token",
            "store.shipping.webhook.states",
            "store.shipping.webhook.help");

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void orderShipmentsTableShowsTrackingSubscriptionStatus() throws Exception {
        // when
        String html = read(ORDER_DETAILS);

        // then
        assertThat(html).contains("#{order.shipment.tracking.status}");
        assertThat(html).contains("shipment.trackingSubscriptionStatus");
        assertThat(html).contains("shipment.trackingSubscriptionError");
    }

    @Test
    void shippingConfigurationShowsWebhookUrlOnlyForSelectedProvider() throws Exception {
        // when
        String html = read(STORE_SHIPPING);

        // then
        assertThat(html).contains("${shippingWebhookUrl}");
        assertThat(html).contains("th:if=\"${shippingWebhookUrl != null}\"");
        assertThat(html).contains("#{store.shipping.webhook.title}");
    }

    @Test
    void everyNewKeyIsPresentInBothMessageBundles() throws Exception {
        // when
        String pl = read(MESSAGES_PL);
        String en = read(MESSAGES_EN);

        // then
        for (String key : KEYS) {
            assertThat(pl).as(key + " in messages_pl").contains("\n" + key + "=");
            assertThat(en).as(key + " in messages_en").contains("\n" + key + "=");
        }
    }

    @Test
    void shippingScreenHasHelpPanelWithFurgonetkaGuidance() throws Exception {
        // when
        String html = read(STORE_SHIPPING);
        String pl = read(MESSAGES_PL);
        String en = read(MESSAGES_EN);

        // then
        assertThat(html).contains("~{fragments/screen-intro :: panel('shipping'");
        assertThat(html).contains("~{fragments/screen-intro :: toggle}");
        for (String suffix : List.of("title", "lead", "item1", "item1.text", "item2", "item2.text", "item3", "item3.text")) {
            assertThat(pl).contains("\nintro.shipping." + suffix + "=");
            assertThat(en).contains("\nintro.shipping." + suffix + "=");
        }
        assertThat(pl).contains("Furgonetk");
        assertThat(en).contains("Furgonetka");
    }
}
