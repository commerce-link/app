package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInStockQueryServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Test
    void exposesPurchaseCostToInventoryWhenUnitSystemCostIsNotSet() {
        // given
        WarehouseItem item = anAvailableItem(20.0);
        when(warehouseRepository.findAllAvailableByMfns("store-1", List.of("MFN-1"))).thenReturn(List.of(item));

        // when
        InventoryItem inventoryItem = firstInventoryItem();

        // then
        assertEquals(20.0, inventoryItem.netPrice());
    }

    @Test
    void exposesUnitSystemCostToInventoryInsteadOfPurchaseCost() {
        // given
        WarehouseItem item = anAvailableItem(20.0);
        item.setUnitSystemCost(150.0);
        when(warehouseRepository.findAllAvailableByMfns("store-1", List.of("MFN-1"))).thenReturn(List.of(item));

        // when
        InventoryItem inventoryItem = firstInventoryItem();

        // then
        assertEquals(150.0, inventoryItem.netPrice());
    }

    private InventoryItem firstInventoryItem() {
        List<WarehouseItemView> views = new BuiltInStockQueryService(warehouseRepository)
                .searchAvailableByMfns("store-1", List.of("MFN-1"));
        return views.get(0).toInventoryItem();
    }

    private WarehouseItem anAvailableItem(double unitCost) {
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", unitCost, 1);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
