package pl.commercelink.stores;

import lombok.Getter;

@Getter
public class StoreSeedingException extends RuntimeException {

    private final String storeId;

    public StoreSeedingException(String storeId, Throwable cause) {
        super(cause);
        this.storeId = storeId;
    }
}
