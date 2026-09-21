package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.provider.api.ProviderField.FieldType;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderFieldTextTest {

    @Test
    void theAdaptersRequiredMarkerIsDroppedFromTheLabel() {
        // when / then
        assertThat(ProviderFieldText.label(new ProviderField("apiUrl", "Adres sklepu *", FieldType.URL, true, null)))
                .isEqualTo("Adres sklepu");
        assertThat(ProviderFieldText.label(new ProviderField("fee", "Opłata (0-30)", FieldType.NUMBER, false, null)))
                .isEqualTo("Opłata (0-30)");
    }

    @Test
    void aMaskOrACopyOfTheLabelIsNoExample() {
        // when / then
        assertThat(ProviderFieldText.example(new ProviderField("clientId", "Client ID", FieldType.TEXT, true, "Client ID"))).isNull();
        assertThat(ProviderFieldText.example(new ProviderField("key", "Klucz", FieldType.TEXT, true, "******"))).isNull();
        assertThat(ProviderFieldText.example(new ProviderField("secret", "Sekret", FieldType.PASSWORD, true, "sk_live_1"))).isNull();
        assertThat(ProviderFieldText.example(new ProviderField("apiUrl", "Adres sklepu *", FieldType.URL, true, "https://sklep.pl")))
                .isEqualTo("https://sklep.pl");
    }
}
