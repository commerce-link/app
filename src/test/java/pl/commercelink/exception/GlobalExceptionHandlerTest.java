package pl.commercelink.exception;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutErrorResponse;
import pl.commercelink.checkout.CheckoutException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), PL);

    @Test
    @DisplayName("handleOptimisticLockingExhausted returns 409 Conflict with Retry-After header and user-facing message")
    void handleOptimisticLockingExhaustedReturnsConflictResponseWithRetryAfterHeader() {
        // given
        OptimisticLockingExhaustedException exception =
                new OptimisticLockingExhaustedException(3, new ConditionalCheckFailedException("test"));

        // when
        ResponseEntity<String> response = handler.handleOptimisticLockingExhausted(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Another user just modified this data");
    }

    @Test
    void clientCheckoutErrorIsBadRequestWithTypeAndMessageInRequestedLanguage() throws IOException {
        // given
        CheckoutException exception = new CheckoutException(CheckoutError.BillingDetailsMissing, "billing details not filled");

        // when
        ResponseEntity<CheckoutErrorResponse> polish = handler.handleCheckoutException(exception, PL);
        ResponseEntity<CheckoutErrorResponse> english = handler.handleCheckoutException(exception, Locale.ENGLISH);

        // then
        assertThat(polish.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(polish.getBody().error()).isEqualTo("billing-details-missing");
        assertThat(polish.getBody().message())
                .isEqualTo(raw("messages_pl.properties").getProperty("checkout.error.BillingDetailsMissing"));
        assertThat(english.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(english.getBody().error()).isEqualTo("billing-details-missing");
        assertThat(english.getBody().message())
                .isEqualTo(raw("messages_en.properties").getProperty("checkout.error.BillingDetailsMissing"));
    }

    @Test
    void languageWithoutMessagesFallsBackToPolish() throws IOException {
        // given
        CheckoutException exception = new CheckoutException(CheckoutError.PricelistOutdated, "pricelist-old");

        // when
        ResponseEntity<CheckoutErrorResponse> response = handler.handleCheckoutException(exception, Locale.GERMAN);

        // then
        assertThat(response.getBody().message())
                .isEqualTo(raw("messages_pl.properties").getProperty("checkout.error.PricelistOutdated"));
    }

    @Test
    void requiredItemMissingNamesTheCategoryInTheMessage() {
        // given
        CheckoutException exception = new CheckoutException(CheckoutError.RequiredItemMissing, "Obudowa", "Obudowa");

        // when
        ResponseEntity<CheckoutErrorResponse> polish = handler.handleCheckoutException(exception, PL);
        ResponseEntity<CheckoutErrorResponse> english = handler.handleCheckoutException(exception, Locale.ENGLISH);

        // then
        assertThat(polish.getBody().error()).isEqualTo("required-item-missing");
        assertThat(polish.getBody().message()).contains("Obudowa").doesNotContain("{0}");
        assertThat(english.getBody().message()).contains("Obudowa").doesNotContain("{0}");
    }

    @Test
    void paymentUnavailableIsServerErrorInTheSameShape() throws IOException {
        // given
        CheckoutException exception = new CheckoutException(CheckoutError.PaymentUnavailable, "gateway timeout",
                new RuntimeException("gateway timeout"));

        // when
        ResponseEntity<CheckoutErrorResponse> response = handler.handleCheckoutException(exception, PL);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("payment-unavailable");
        assertThat(response.getBody().message())
                .isEqualTo(raw("messages_pl.properties").getProperty("checkout.error.PaymentUnavailable"));
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    private static Properties raw(String file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(
                GlobalExceptionHandlerTest.class.getResourceAsStream("/" + file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
