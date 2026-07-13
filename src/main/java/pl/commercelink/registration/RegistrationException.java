package pl.commercelink.registration;

import lombok.Getter;

import java.util.Locale;

@Getter
public class RegistrationException extends RuntimeException {

    public enum Reason {
        INVALID_EMAIL, RATE_LIMITED, EMAIL_EXISTS, CREATION_FAILED
    }

    private final Reason reason;

    public RegistrationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public String messageKey() {
        return "registration.error." + reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
