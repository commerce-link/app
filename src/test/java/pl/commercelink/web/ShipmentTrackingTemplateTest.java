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
    private static final Path SHIPPING_ACCOUNT = Path.of("src/main/resources/templates/store-shipping-account.html");
    private static final Path MESSAGES_PL = Path.of("src/main/resources/messages_pl.properties");
    private static final Path MESSAGES_EN = Path.of("src/main/resources/messages_en.properties");

    private static final List<String> KEYS = List.of(
            "order.shipment.tracking.status",
            "order.shipment.tracking.status.PENDING",
            "order.shipment.tracking.status.ACTIVE",
            "order.shipment.tracking.status.FAILED",
            "store.shipping.account.webhook",
            "store.shipping.account.webhook.help",
            "store.shipping.account.webhook.steps.states",
            "store.shipping.account.webhook.steps.token",
            "store.shipping.account.webhookToken.help",
            "store.shipping.account.status.unverified",
            "store.shipping.account.status.unverified.text");

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
    }

    @Test
    void trackingColumnIsHiddenWhenNoShipmentIsTracked() throws Exception {
        // when
        String html = read(ORDER_DETAILS);

        // then
        assertThat(html).contains("<th th:if=\"${order.hasTrackedShipments()}\" th:text=\"#{order.shipment.tracking.status}\">");
        assertThat(html).contains("<td th:if=\"${order.hasTrackedShipments()}\">");
    }

    @Test
    void theCourierAccountPageShowsTheWebhookAddressOfEveryProviderThatHasOne() throws Exception {
        // when
        String account = read(SHIPPING_ACCOUNT);
        String hub = read(STORE_SHIPPING);

        // then
        assertThat(account).contains("th:if=\"${webhooks[provider.name()] != null}\"");
        assertThat(account).contains("settings-form :: copyField('webhook-'");
        assertThat(hub).contains("#{store.shipping.account.status.unverified(");
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

    /** The Furgonetka webhook guidance moved from the shipping screen guide to the courier account page. */
    @Test
    void theCourierAccountPageGuidesTheWebhookSetup() throws Exception {
        // when
        String html = read(STORE_SHIPPING.resolveSibling("store-shipping-account.html"));
        String pl = read(MESSAGES_PL);
        String en = read(MESSAGES_EN);

        // then
        assertThat(read(STORE_SHIPPING)).doesNotContain("screen-intro");
        assertThat(html).contains("store.shipping.account.webhook.steps.states");
        assertThat(pl).contains("\nstore.shipping.account.webhook.steps.states=");
        assertThat(en).contains("\nstore.shipping.account.webhook.steps.states=");
    }
}
