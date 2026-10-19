package pl.commercelink.web.activity;

class StoreInactiveException extends RuntimeException {

    StoreInactiveException(String storeId) {
        super("Store " + storeId + " is inactive");
    }
}
