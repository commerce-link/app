package pl.commercelink.inventory;

/** One own group and the single global group that shares its brand and code. */
record LinkCandidate(MatchedInventory own, MatchedInventory global, double ownAveragePrice, double globalAveragePrice,
                     InventoryIndex ownIndex, InventoryIndex globalIndex) {
}
