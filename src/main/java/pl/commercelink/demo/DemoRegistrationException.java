package pl.commercelink.demo;

import lombok.Getter;

import java.util.Locale;

@Getter
public class DemoRegistrationException extends RuntimeException {

    public enum Reason {
        INVALID_EMAIL, RATE_LIMITED, EMAIL_EXISTS, CREATION_FAILED
    }

    private final Reason reason;

    public DemoRegistrationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public String messageKey() {
        return "demo.register.error." + reason.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
