package pl.commercelink.registration;

import java.io.Serializable;

public record PendingRegistration(String email, String storeName) implements Serializable {
}
