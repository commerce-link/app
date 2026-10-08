package pl.commercelink.shipping;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.ShippingException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderErrorsTest {

    @Test
    void a4xxAnswerIsARefusalAnd5xxOrNoAnswerIsNot() {
        // when / then
        assertThat(ProviderErrors.isRefusal(new ShippingException("x", new HttpClientException(400, "{}")))).isTrue();
        assertThat(ProviderErrors.isRefusal(new ShippingException("x", new HttpClientException(502, "{}")))).isFalse();
        assertThat(ProviderErrors.isRefusal(new RuntimeException("HTTP request failed"))).isFalse();
        assertThat(ProviderErrors.isRefusal(new ShippingException("checked before sending"))).isTrue();
    }

    @Test
    void describeReadsTheProvidersMessagesFromAJsonBody() {
        // given
        RuntimeException e = new ShippingException("HTTP 400", new HttpClientException(400,
                "{\"errors\":[{\"path\":\"/receiver/name\",\"message\":\"Imię i nazwisko może składać się tylko z liter\"},"
                        + "{\"message\":\"Nieprawidłowy kod pocztowy\"}]}"));

        // when / then
        assertThat(ProviderErrors.describe(e))
                .isEqualTo("Imię i nazwisko może składać się tylko z liter; Nieprawidłowy kod pocztowy");
    }

    @Test
    void describeFallsBackToTheExceptionMessage() {
        // when / then
        assertThat(ProviderErrors.describe(new ShippingException("Shipment cannot be cancelled")))
                .isEqualTo("Shipment cannot be cancelled");
    }

    @Test
    void theAdaptersOwnProviderMessagesAreShownBeforeTheBodyIsParsed() {
        // given: Allegro answers {"errors":[{"code":"…","userMessage":"Waga przekracza limit"}]}
        HttpClientException http = new HttpClientException(422, "{\"errors\":[{\"code\":\"VALIDATION_ERROR\"}]}");
        ShippingException refusal = new ShippingException("Allegro refused the shipment", http,
                List.of("Waga przekracza limit", "Brak numeru telefonu"));

        // when / then
        assertThat(ProviderErrors.describe(refusal)).isEqualTo("Waga przekracza limit; Brak numeru telefonu");
        assertThat(ProviderErrors.isRefusal(refusal)).isTrue();
    }

    @Test
    void providerMessagesAreFoundDeeperInTheCauseChain() {
        // given
        ShippingException inner = new ShippingException("refused", null, List.of("Kod pocztowy niepoprawny"));
        RuntimeException outer = new RuntimeException("wrapped", inner);

        // when / then
        assertThat(ProviderErrors.describe(outer)).isEqualTo("Kod pocztowy niepoprawny");
    }
}
