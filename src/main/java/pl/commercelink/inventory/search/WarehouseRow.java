package pl.commercelink.inventory.search;

import pl.commercelink.warehouse.api.ItemCondition;

public record WarehouseRow(String productEan, String productCode, double grossUnitCost, int qty, boolean inDelivery,
                           ItemCondition condition, CodeMatch codeMatch) {

    public boolean hasSpecialCondition() {
        return condition != null && condition != ItemCondition.Sealed;
    }
}
