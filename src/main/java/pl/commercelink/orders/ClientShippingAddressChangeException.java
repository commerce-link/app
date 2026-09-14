package pl.commercelink.orders;

import lombok.Getter;

@Getter
public class ClientShippingAddressChangeException extends RuntimeException {

    public enum Reason {
        NOT_EDITABLE,
        INVALID_ADDRESS,
        COUNTRY_CHANGED
    }

    private final Reason reason;

    public ClientShippingAddressChangeException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
