package pl.commercelink.orders;

import lombok.Getter;

@Getter
public class ClientPreferredShippingDateException extends RuntimeException {

    public enum Reason {
        NOT_EDITABLE,
        INVALID_DATE
    }

    private final Reason reason;

    public ClientPreferredShippingDateException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
