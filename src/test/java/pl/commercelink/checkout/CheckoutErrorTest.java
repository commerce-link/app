package pl.commercelink.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
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
                "required-item-missing", "delivery-option-not-found", "payment-option-not-found", "payment-unavailable",
                "discount-code-not-found", "discount-code-inactive", "discount-code-exhausted", "discount-code-expired",
                "discount-code-not-yet-valid", "discount-code-payment-method-mismatch",
                "discount-code-minimum-not-reached", "discount-code-not-applicable");
    }

    @Test
    void minimumNotReachedNamesTheThresholdInBothLanguages() {
        // given
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        Object[] threshold = {new BigDecimal("1500.00")};
        String key = CheckoutError.DiscountCodeMinimumNotReached.messageKey();

        // when
        String pl = messages.getMessage(key, threshold, Locale.forLanguageTag("pl"));
        String en = messages.getMessage(key, threshold, Locale.ENGLISH);

        // then
        assertThat(pl).isEqualTo("Kod rabatowy działa od zamówienia za 1 500,00 zł.");
        assertThat(en).contains("1,500.00 PLN");
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
