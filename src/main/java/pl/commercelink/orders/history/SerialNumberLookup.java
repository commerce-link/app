package pl.commercelink.orders.history;

/** Finds the records that carry a serial number; the index from side task #22 replaces the scan behind it. */
public interface SerialNumberLookup {

    SerialNumberMatches find(String storeId, String serialNo);
}
