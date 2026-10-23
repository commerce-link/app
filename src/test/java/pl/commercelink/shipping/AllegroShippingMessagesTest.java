package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AllegroShippingMessagesTest {

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    @Test
    void englishLimitMessagesShowTheirNumbersAndTheApostrophe() {
        // given: a single ' in a message with arguments would quote the rest of it, {0} included
        ResourceBundleMessageSource messages = messages();

        // when
        String dimensions = messages.getMessage("shipping.allegro.error.dimensions", new Object[]{"64", "38", "41"}, Locale.ENGLISH);
        String weight = messages.getMessage("shipping.allegro.error.weight", new Object[]{"25"}, Locale.ENGLISH);
        String cod = messages.getMessage("shipping.allegro.error.cod", new Object[]{"5000"}, Locale.ENGLISH);
        String insurance = messages.getMessage("shipping.allegro.error.insurance.max", new Object[]{"3000"}, Locale.ENGLISH);

        // then
        assertThat(dimensions).isEqualTo("The parcel exceeds the method's dimensions: at most 64 × 38 × 41 cm.");
        assertThat(weight).isEqualTo("The parcel exceeds the method's weight: at most 25 kg.");
        assertThat(cod).isEqualTo("The cash on delivery exceeds the method's limit: at most 5000 PLN.");
        assertThat(insurance).isEqualTo("The insurance exceeds the method's limit: at most 3000 PLN.");
    }

    @Test
    void carriersHeaderNamesTheIntegrationInPolish() {
        // when
        String header = messages().getMessage("store.shipping.carriers.title.of", new Object[]{"Furgonetka"}, Locale.of("pl"));

        // then
        assertThat(header).isEqualTo("Przewoźnicy integracji Furgonetka");
    }
}
