package pl.commercelink.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.ExtendedModelMap;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationErrorsTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_EMAIL, email",
            "EMAIL_EXISTS, email",
            "STORE_NAME_REQUIRED, storeName",
            "INVALID_STORE_NAME, storeName",
            "WEAK_PASSWORD, password",
            "INVALID_CODE, code"
    })
    void placesFieldErrorsNextToTheirField(RegistrationException.Reason reason, String field) {
        // when
        Optional<String> placed = RegistrationErrors.fieldOf(new RegistrationException(reason).messageKey());

        // then
        assertEquals(Optional.of(field), placed);
    }

    @Test
    void keepsErrorsWithoutAFieldAboveTheForm() {
        // given
        Set<RegistrationException.Reason> withoutField = EnumSet.of(RegistrationException.Reason.RATE_LIMITED,
                RegistrationException.Reason.CREATION_FAILED, RegistrationException.Reason.CAPTCHA_FAILED,
                RegistrationException.Reason.VERIFICATION_FAILED);

        // when / then
        withoutField.forEach(reason ->
                assertTrue(RegistrationErrors.fieldOf(new RegistrationException(reason).messageKey()).isEmpty(), reason.name()));
    }

    @Test
    void termsConsentErrorBelongsToTheCheckbox() {
        // when / then
        assertEquals(Optional.of("termsConsent"), RegistrationErrors.fieldOf("registration.error.terms-consent-required"));
    }

    @Test
    void fieldErrorForAFieldThePageDoesNotHaveBecomesAnAlert() {
        // given
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("registration.error.email-exists", Locale.ROOT, "Konto istnieje.");
        ExtendedModelMap model = new ExtendedModelMap();
        RegistrationErrors.prepare(model, "password");

        // when
        RegistrationErrors.reject(model, "registration.error.email-exists", Set.of("password"), messages, Locale.ROOT);

        // then
        assertEquals("Konto istnieje.", model.getAttribute("errorMessage"));
        assertEquals(Map.of(), model.getAttribute("fieldErrors"));
    }
}
