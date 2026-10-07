package pl.commercelink.shipping;

import org.springframework.context.MessageSource;

import java.util.Locale;

/** The texts of the store's bell about shipments, written by whatever thread settles a message. */
final class OperatorMessages {

    // the bell is read by the store's staff, whose language is Polish whatever thread settles the message
    private static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private OperatorMessages() {
    }

    static String message(MessageSource messageSource, String key, Object... args) {
        return messageSource.getMessage(key, args, OPERATOR_LOCALE);
    }

    /** Our own reason (a message key) in the operator's words, else the provider's words as they are. */
    static String reason(MessageSource messageSource, String error, String errorKey) {
        if (errorKey != null) {
            return messageSource.getMessage(errorKey, null, OPERATOR_LOCALE);
        }
        return error != null ? error : "";
    }
}
