package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseAllocationsManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "TechData";
    private static final String ITEM_ID = "warehouse-item-1";

    @Mock
    private WarehouseItemFactory warehouseItemFactory;
    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @Test
    @DisplayName("updateFulfilment updates fulfilment data of item in Allocation state assigned to the given provider")
    void updateFulfilmentUpdatesItemInAllocationState() {
        // given
        WarehouseItem warehouseItem = warehouseItemInStatus(FulfilmentStatus.Allocation);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(warehouseItem);

        // when
        boolean updated = warehouseAllocationsManager.updateFulfilment(STORE_ID, PROVIDER, ITEM_ID, "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isTrue();
        verify(warehouseRepository).save(warehouseItem);
        assertThat(warehouseItem.getEan()).isEqualTo("new-ean");
        assertThat(warehouseItem.getManufacturerCode()).isEqualTo("NEW-MFN");
        assertThat(warehouseItem.getCost()).isEqualTo(55.5);
    }

    @Test
    @DisplayName("updateFulfilment does not touch item that is no longer in Allocation state")
    void updateFulfilmentSkipsItemOutsideAllocationState() {
        // given
        WarehouseItem warehouseItem = warehouseItemInStatus(FulfilmentStatus.Ordered);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(warehouseItem);

        // when
        boolean updated = warehouseAllocationsManager.updateFulfilment(STORE_ID, PROVIDER, ITEM_ID, "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(warehouseRepository, never()).save(any());
        assertThat(warehouseItem.getEan()).isEqualTo("old-ean");
    }

    @Test
    @DisplayName("updateFulfilment does not touch item assigned to a different provider")
    void updateFulfilmentSkipsItemOfDifferentProvider() {
        // given
        WarehouseItem warehouseItem = warehouseItemInStatus(FulfilmentStatus.Allocation);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(warehouseItem);

        // when
        boolean updated = warehouseAllocationsManager.updateFulfilment(STORE_ID, "other-provider", ITEM_ID, "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFulfilment does not fail when item is not found")
    void updateFulfilmentSkipsMissingItem() {
        // given
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(null);

        // when
        boolean updated = warehouseAllocationsManager.updateFulfilment(STORE_ID, PROVIDER, ITEM_ID, "new-ean", "NEW-MFN", 55.5);

        // then
        assertThat(updated).isFalse();
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void releaseReturnsAllDeliveryWarehouseItemsToAllocationPool() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(4);
        when(warehouseRepository.findByDeliveryId("store-1", "delivery-1")).thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release("store-1", "delivery-1");

        // then
        assertEquals(FulfilmentStatus.Allocation, claimed.getStatus());
        assertNull(claimed.getDeliveryId());
        assertEquals(4, claimed.getQty());
        verify(warehouseRepository).save(claimed);
    }

    private WarehouseItem warehouseItemInStatus(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem(STORE_ID, PROVIDER, "Other", "test", "old-ean", "OLD-MFN", 10.0, 1);
        item.setStatus(status);
        return item;
    }
}
