package pl.commercelink.clientaccess;

import lombok.Getter;

@Getter
public class ClientVerificationException extends RuntimeException {

    public enum Reason {
        TOO_MANY_REQUESTS,
        EMAIL_NOT_SENT,
        NOT_FOUND,
        CODE_EXPIRED,
        TOO_MANY_ATTEMPTS,
        INVALID_CODE,
        INVALID_TOKEN
    }

    private final Reason reason;

    public ClientVerificationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
