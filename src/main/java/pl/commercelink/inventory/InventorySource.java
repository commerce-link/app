package pl.commercelink.inventory;

interface InventorySource {
    void mergeInto(MatchedInventory result, InventoryKey lookupKey);
}
