package pl.commercelink.inventory;

interface InventoryFilter {
    MatchedInventory apply(MatchedInventory inventory);
}
