package pl.commercelink.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutErrorTest {

    @Test
    void apiCodesAreTheOnesTheStorefrontHandles() {
        // when
        var codes = Arrays.stream(CheckoutError.values()).map(CheckoutError::apiCode).toList();

        // then
        assertThat(codes).containsExactlyInAnyOrder(
                "billing-details-missing", "shipping-details-missing", "pricelist-outdated", "item-not-found",
                "required-item-missing", "delivery-option-not-found", "payment-option-not-found", "payment-unavailable");
    }

    @Test
    void onlyPaymentUnavailableIsAServerError() {
        // when / then
        for (CheckoutError error : CheckoutError.values()) {
            HttpStatus expected = error == CheckoutError.PaymentUnavailable ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
            assertThat(error.status()).as(error.name()).isEqualTo(expected);
        }
    }

    @Test
    void everyErrorHasAMessageInPolishAndEnglish() throws IOException {
        // given
        Properties pl = raw("messages_pl.properties");
        Properties en = raw("messages_en.properties");

        for (CheckoutError error : CheckoutError.values()) {
            // when
            String key = error.messageKey();

            // then
            assertThat(pl.getProperty(key)).as("pl " + key).isNotBlank();
            assertThat(en.getProperty(key)).as("en " + key).isNotBlank();
        }
    }

    private static Properties raw(String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                CheckoutErrorTest.class.getResourceAsStream("/" + file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
