package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The messages about a shipment name its integration through an argument, so a store shipping through more than one
 * never reads "check the Furgonetka panel" about another integration's package. Each key is rendered through the real
 * bundles with the arguments its callers pass: a missing argument would show "{0}" to the operator.
 */
class ShippingIntegrationMessagesTest {

    private static final String NAME = "Wysyłam z Allegro";
    private static final ResourceBundleMessageSource BUNDLES = ShippingIntegrationNamesFixture.bundles();

    static Stream<Arguments> messagesNamingTheIntegration() {
        Object[] nameOnly = {NAME};
        return Stream.of(
                Arguments.of("order.shipment.tracking.status.ACTIVE", nameOnly),
                Arguments.of("shipping.creation.unconfirmed", nameOnly),
                Arguments.of("shipping.creation.notCreated", nameOnly),
                Arguments.of("shipping.pickup.unconfirmed", nameOnly),
                Arguments.of("shipping.label.empty", nameOnly),
                Arguments.of("shipping.notification.warehouse.pickup.failed",
                        new Object[]{"TRK-1", null, null, null, "Brak kuriera", null, NAME}),
                Arguments.of("order.shipments.cancel.error.pending", nameOnly),
                Arguments.of("order.shipments.cancel.error.not.cancellable", nameOnly),
                Arguments.of("shipment.cancel.requested", nameOnly),
                Arguments.of("shipment.cancel.rechecking", nameOnly),
                Arguments.of("shipment.cancel.failed", new Object[]{"Przesyłka została już odebrana", NAME}),
                Arguments.of("shipment.cancellation.failed", nameOnly),
                Arguments.of("shipment.cancellation.unconfirmed", nameOnly),
                Arguments.of("shipment.cancellation.reason.notReceived", nameOnly),
                Arguments.of("order.shipments.cancel.locked.pending", nameOnly),
                Arguments.of("order.shipments.remove.confirm.message.cancellationUnresolved", nameOnly));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messagesNamingTheIntegration")
    void everyMessageNamesTheIntegrationAndLeavesNoPlaceholderInBothLanguages(String key, Object[] args) {
        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            // when
            String text = BUNDLES.getMessage(key, args, locale);

            // then
            assertThat(text).as("%s in %s", key, locale).contains("(" + NAME + ")").doesNotContain("{")
                    .doesNotContain("Furgonet");
        }
    }

    @Test
    void theOtherReasonsStoredAsKeysIgnoreTheNameTheyAreGiven() {
        // given: every stored reason is resolved with the integration's name, whether it names it or not
        List<String> keys = List.of("shipping.pickup.not.sent", "shipping.pickup.no.provider",
                "shipping.pickup.immediate.no.windows");

        for (String key : keys) {
            for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
                // when
                String withName = BUNDLES.getMessage(key, new Object[]{NAME}, locale);

                // then
                assertThat(withName).as("%s in %s", key, locale).isEqualTo(BUNDLES.getMessage(key, null, locale));
            }
        }
    }

    @Test
    void onlyTheFurgonetkaSettingsAndTheIntroductionNameFurgonetkaItself() throws IOException {
        for (String bundle : List.of("messages_pl.properties", "messages_en.properties")) {
            // given
            Properties messages = load(bundle);

            // when
            List<String> naming = messages.stringPropertyNames().stream()
                    .filter(key -> messages.getProperty(key).contains("Furgonet"))
                    .filter(key -> !key.startsWith("store.furgonetka.") && !key.startsWith("intro."))
                    .sorted()
                    .toList();

            // then
            assertThat(naming).as(bundle).isEmpty();
        }
    }

    private static Properties load(String bundle) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ShippingIntegrationMessagesTest.class.getClassLoader().getResourceAsStream(bundle)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
