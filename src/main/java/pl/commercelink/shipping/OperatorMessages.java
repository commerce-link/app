package pl.commercelink.shipping;

import org.springframework.context.MessageSource;
import pl.commercelink.orders.ProviderCommand;

import java.util.Locale;

/** The texts of the store's bell about shipments, written by whatever thread settles a message. */
final class OperatorMessages {

    // the bell is read by the store's staff, whose language is Polish whatever thread settles the message
    static final Locale OPERATOR_LOCALE = Locale.forLanguageTag("pl");

    private OperatorMessages() {
    }

    static String message(MessageSource messageSource, String key, Object... args) {
        return messageSource.getMessage(key, args, OPERATOR_LOCALE);
    }

    /**
     * Our own reason (a message key) in the operator's words, else the provider's words as they are. integration: the
     * name of the shipping integration, which the keys that name it take as their argument.
     */
    static String reason(MessageSource messageSource, String error, String errorKey, String integration) {
        if (errorKey != null) {
            return messageSource.getMessage(errorKey, new Object[]{integration}, OPERATOR_LOCALE);
        }
        return error != null ? error : "";
    }

    /** The failure reason of a command, as above; none for no command. */
    static String reason(MessageSource messageSource, ProviderCommand command, String integration) {
        return command == null ? "" : reason(messageSource, command.getError(), command.getErrorKey(), integration);
    }
}
