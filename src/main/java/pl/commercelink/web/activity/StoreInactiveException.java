package pl.commercelink.web.activity;

import pl.commercelink.stores.Store;

class StoreInactiveException extends RuntimeException {

    private final transient Store store;

    StoreInactiveException(Store store) {
        super("Store " + store.getStoreId() + " is inactive");
        this.store = store;
    }

    Store store() {
        return store;
    }
}
