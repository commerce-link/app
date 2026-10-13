package pl.commercelink.registration;

import org.springframework.context.MessageSource;
import org.springframework.ui.Model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Puts a registration error next to the field it is about, or above the form when the page has no such field
 * (captcha, rate limit, a failure of the account service, an address taken that is only found on the password step).
 * The server stops at the first error, so a page shows at most one.
 */
final class RegistrationErrors {

    static final String FIELD_ERRORS = "fieldErrors";
    static final String ERROR_MESSAGE = "errorMessage";
    static final String AUTOFOCUS = "autofocus";

    private static final Map<String, String> FIELD_BY_MESSAGE_KEY = Map.of(
            "registration.error.invalid-email", "email",
            "registration.error.email-exists", "email",
            "registration.error.store-name-required", "storeName",
            "registration.error.invalid-store-name", "storeName",
            "registration.error.terms-consent-required", "termsConsent",
            "registration.error.weak-password", "password",
            "registration.error.invalid-code", "code");

    private RegistrationErrors() {
    }

    static void prepare(Model model, String firstField) {
        model.addAttribute(FIELD_ERRORS, new LinkedHashMap<String, String>());
        model.addAttribute(AUTOFOCUS, firstField);
    }

    static void reject(Model model, String messageKey, Set<String> fieldsOnPage, MessageSource messageSource,
                       Locale locale) {
        Optional<String> field = fieldOf(messageKey).filter(fieldsOnPage::contains);
        if (field.isPresent()) {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put(field.get(), messageKey);
            model.addAttribute(FIELD_ERRORS, errors);
            model.addAttribute(AUTOFOCUS, field.get());
        } else {
            model.addAttribute(ERROR_MESSAGE, messageSource.getMessage(messageKey, null, locale));
        }
    }

    static Optional<String> fieldOf(String messageKey) {
        return Optional.ofNullable(FIELD_BY_MESSAGE_KEY.get(messageKey));
    }
}
