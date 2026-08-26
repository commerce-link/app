package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;
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

    @Test
    void excludesNotSealedItemsFromAvailableStock() {
        // given
        WarehouseItem sealed = anAvailableItem(20.0);
        WarehouseItem openBox = anAvailableItem(20.0);
        openBox.setCondition(ItemCondition.OpenBox);
        WarehouseItem damaged = anAvailableItem(20.0);
        damaged.setCondition(ItemCondition.Damaged);
        when(warehouseRepository.findAllAvailableByMfns("store-1", List.of("MFN-1"))).thenReturn(List.of(openBox, sealed, damaged));

        // when
        List<WarehouseItemView> views = new BuiltInStockQueryService(warehouseRepository)
                .searchAvailableByMfns("store-1", List.of("MFN-1"));

        // then
        assertEquals(1, views.size());
        assertEquals(sealed.getItemId(), views.get(0).getItemId());
    }

    @Test
    void excludesNotSealedItemsFromStockLevelSearch() {
        // given
        WarehouseItem openBox = anAvailableItem(20.0);
        openBox.setCondition(ItemCondition.OpenBox);
        when(warehouseRepository.findAllByMfns("store-1", List.of("MFN-1"))).thenReturn(List.of(openBox));

        // when
        List<WarehouseItemView> views = new BuiltInStockQueryService(warehouseRepository)
                .searchByMfns("store-1", List.of("MFN-1"));

        // then
        assertEquals(0, views.size());
    }

    @Test
    void returnsOnlyNotSealedItemsWithTheirCondition() {
        // given
        WarehouseItem sealed = anAvailableItem(20.0);
        WarehouseItem damaged = anAvailableItem(20.0);
        damaged.setCondition(ItemCondition.Damaged);
        when(warehouseRepository.findAllAvailableByMfns("store-1", List.of("MFN-1"))).thenReturn(List.of(sealed, damaged));

        // when
        List<WarehouseItemView> views = new BuiltInStockQueryService(warehouseRepository)
                .searchNotSealedAvailableByMfns("store-1", List.of("MFN-1"));

        // then
        assertEquals(1, views.size());
        assertEquals(damaged.getItemId(), views.get(0).getItemId());
        assertEquals(ItemCondition.Damaged, views.get(0).getCondition());
    }

    @Test
    void findsNotSealedItemById() {
        // given
        WarehouseItem openBox = anAvailableItem(20.0);
        openBox.setCondition(ItemCondition.OpenBox);
        when(warehouseRepository.findById("store-1", openBox.getItemId())).thenReturn(openBox);

        // when
        WarehouseItemView view = new BuiltInStockQueryService(warehouseRepository)
                .findById("store-1", openBox.getItemId());

        // then
        assertEquals(openBox.getItemId(), view.getItemId());
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
