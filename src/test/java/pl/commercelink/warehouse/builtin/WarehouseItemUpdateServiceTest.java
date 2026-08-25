package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseItemUpdateServiceTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseItemUpdateService service;

    @Test
    void mergesItemIntoExistingSealedItemWhenConditionChangesBackToSealed() {
        // given
        WarehouseItem sealed = aDeliveredItem(4);
        sealed.setSerialNo("SN-1,SN-2");
        WarehouseItem openBox = aDeliveredItem(1);
        openBox.setCondition(ItemCondition.OpenBox);
        openBox.setSerialNo("SN-3");
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Delivered)))
                .thenReturn(List.of(sealed, openBox));

        WarehouseItem updated = aDeliveredItem(1);
        updated.setCondition(ItemCondition.Sealed);
        updated.setSerialNo("SN-3");
        updated.setComment("resealed");

        // when
        service.update(STORE_ID, openBox, updated);

        // then
        assertEquals(5, sealed.getQty());
        assertEquals("SN-1,SN-2,SN-3", sealed.getSerialNo());
        assertEquals("resealed", sealed.getComment());
        verify(warehouseRepository).save(sealed);
        verify(warehouseRepository).delete(openBox);
    }

    @Test
    void savesItemWhenNoJoinableItemExistsAfterConditionChange() {
        // given
        WarehouseItem damaged = aDeliveredItem(2);
        damaged.setCondition(ItemCondition.Damaged);
        WarehouseItem openBox = aDeliveredItem(1);
        openBox.setCondition(ItemCondition.OpenBox);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Delivered)))
                .thenReturn(List.of(damaged, openBox));

        WarehouseItem updated = aDeliveredItem(1);
        updated.setCondition(ItemCondition.Sealed);

        // when
        service.update(STORE_ID, openBox, updated);

        // then
        assertEquals(ItemCondition.Sealed, openBox.getCondition());
        verify(warehouseRepository).save(openBox);
        verify(warehouseRepository, never()).delete(any(WarehouseItem.class));
    }

    @Test
    void doesNotLookForJoinableItemsWhenConditionIsUnchanged() {
        // given
        WarehouseItem item = aDeliveredItem(1);
        WarehouseItem updated = aDeliveredItem(1);
        updated.setComment("updated comment");

        // when
        service.update(STORE_ID, item, updated);

        // then
        assertEquals("updated comment", item.getComment());
        verify(warehouseRepository).save(item);
        verify(warehouseRepository, never()).findByDeliveryIdAndStatuses(any(), any(), any());
    }

    private WarehouseItem aDeliveredItem(int qty) {
        WarehouseItem item = new WarehouseItem(STORE_ID, "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", 20.0, qty);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
