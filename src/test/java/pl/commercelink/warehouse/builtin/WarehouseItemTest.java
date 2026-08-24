package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseItemTest {

    @Test
    void fallsBackToPurchaseCostWhenUnitSystemCostIsNotSet() {
        // given
        WarehouseItem item = anItem(120.0);

        // when
        double effectiveCost = item.getEffectiveUnitSystemCost();

        // then
        assertEquals(120.0, effectiveCost);
    }

    @Test
    void usesUnitSystemCostWhenSet() {
        // given
        WarehouseItem item = anItem(20.0);
        item.setUnitSystemCost(150.0);

        // when
        double effectiveCost = item.getEffectiveUnitSystemCost();

        // then
        assertEquals(150.0, effectiveCost);
    }

    @Test
    void updatesUnitSystemCostOnAlreadyDeliveredItem() {
        // given
        WarehouseItem existingItem = anItem(20.0);
        existingItem.setStatus(FulfilmentStatus.Delivered);

        WarehouseItem updatedItem = anItem(999.0);
        updatedItem.setUnitSystemCost(150.0);

        // when
        existingItem.update(updatedItem);

        // then
        assertEquals(150.0, existingItem.getUnitSystemCost());
        assertEquals(20.0, existingItem.getCost());
    }

    @Test
    void copiesUnitSystemCostOntoSplitOffItem() {
        // given
        WarehouseItem item = anItem(20.0);
        item.setQty(5);
        item.setUnitSystemCost(150.0);

        // when
        WarehouseItem splitItem = item.splitOff(2);

        // then
        assertEquals(150.0, splitItem.getUnitSystemCost());
        assertEquals(20.0, splitItem.getCost());
    }

    @Test
    void reportsNoUnitSystemCostWhenItWasNeverSet() {
        // given
        WarehouseItem item = anItem(20.0);

        // when
        boolean hasUnitSystemCost = item.hasUnitSystemCost();

        // then
        assertFalse(hasUnitSystemCost);
    }

    @Test
    void exposesUnitSystemCostAsNetAndGrossPriceForTheWarehouseList() {
        // given
        WarehouseItem item = anItem(20.0);
        item.setTax(1.23);
        item.setUnitSystemCost(150.0);

        // when
        Price systemCost = item.systemCost();

        // then
        assertTrue(item.hasUnitSystemCost());
        assertEquals(150.0, systemCost.netValue());
        assertEquals(184.5, systemCost.grossValue());
    }

    private WarehouseItem anItem(double unitCost) {
        return new WarehouseItem("store-1", "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", unitCost, 1);
    }
}
