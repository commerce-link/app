package pl.commercelink.inventory;

import java.util.List;

class InventoryFilterChain {

    private List<InventoryFilter> filters;

    InventoryFilterChain(List<InventoryFilter> filters) {
        this.filters = filters;
    }

    MatchedInventory apply(MatchedInventory inventory) {
        if (filters.isEmpty()) {
            return inventory;
        }

        MatchedInventory result = inventory;
        for (InventoryFilter filter : filters) {
            result = filter.apply(result);
        }
        return result;
    }

}
