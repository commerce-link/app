package pl.commercelink.stores;

import java.util.function.Predicate;

public enum SupplierScope {

    PRICING(connection -> connection.isEnabled() && connection.isIncludeInPricing()),
    FULFILMENT(connection -> connection.isEnabled() && connection.isIncludeInFulfilment());

    private final Predicate<StoreSupplierConnection> includes;

    SupplierScope(Predicate<StoreSupplierConnection> includes) {
        this.includes = includes;
    }

    boolean includes(StoreSupplierConnection connection) {
        return includes.test(connection);
    }
}
