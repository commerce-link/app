package pl.commercelink.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import pl.commercelink.checkout.CheckoutError;
import pl.commercelink.checkout.CheckoutErrorResponse;
import pl.commercelink.checkout.CheckoutException;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;

import java.util.Locale;
import java.util.Set;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> LANGUAGES_WITH_MESSAGES = Set.of("pl", "en");

    private final MessageSource messageSource;
    private final Locale fallbackLocale;

    GlobalExceptionHandler(MessageSource messageSource,
                           @Value("${commercelink.localization.fallback-locale:pl}") Locale fallbackLocale) {
        this.messageSource = messageSource;
        this.fallbackLocale = fallbackLocale;
    }

    @ExceptionHandler(OptimisticLockingExhaustedException.class)
    public ResponseEntity<String> handleOptimisticLockingExhausted(OptimisticLockingExhaustedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .contentType(MediaType.TEXT_PLAIN)
                .body("Another user just modified this data. Please refresh the page and try again.");
    }

    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<CheckoutErrorResponse> handleCheckoutException(CheckoutException e, Locale locale) {
        CheckoutError error = e.getError();
        if (error.status().is5xxServerError()) {
            log.error("Checkout failed: {}", e.getMessage(), e);
        } else {
            log.warn("Checkout refused: {}", e.getMessage());
        }
        String message = messageSource.getMessage(error.messageKey(), e.getMessageArgs(), messageLocale(locale));
        return ResponseEntity.status(error.status()).body(new CheckoutErrorResponse(error.apiCode(), message));
    }

    private Locale messageLocale(Locale requested) {
        return requested != null && LANGUAGES_WITH_MESSAGES.contains(requested.getLanguage()) ? requested : fallbackLocale;
    }
}
