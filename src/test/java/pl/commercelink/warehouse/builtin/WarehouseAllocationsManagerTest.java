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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("release returns all Ordered delivery warehouse items to the allocation pool")
    void releaseReturnsAllDeliveryWarehouseItemsToAllocationPool() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(4);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1");

        // then
        assertThat(claimed.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
        assertThat(claimed.getDeliveryId()).isNull();
        assertThat(claimed.getQty()).isEqualTo(4);
        verify(warehouseRepository).save(claimed);
    }

    @Test
    @DisplayName("release leaves a Delivered item untouched")
    void releaseLeavesADeliveredItemUntouched() {
        // given
        WarehouseItem delivered = new WarehouseItem();
        delivered.setStatus(FulfilmentStatus.Delivered);
        delivered.setDeliveryId("delivery-1");
        delivered.setQty(4);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of());

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1");

        // then
        assertThat(delivered.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(delivered.getDeliveryId()).isEqualTo("delivery-1");
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUnitCosts applies confirmed prices to items matched by manufacturer code")
    void updateUnitCostsAppliesConfirmedPricesByMfn() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setManufacturerCode("MFN-1");
        claimed.setCost(10.0);
        when(warehouseRepository.findByDeliveryId(STORE_ID, "delivery-1")).thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.updateUnitCosts(STORE_ID, "delivery-1", Map.of("MFN-1", 8.5));

        // then
        assertThat(claimed.getCost()).isEqualTo(8.5);
        verify(warehouseRepository).save(claimed);
    }

    @Test
    @DisplayName("updateUnitCosts skips items without a confirmed price")
    void updateUnitCostsSkipsItemsWithoutConfirmedPrice() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setManufacturerCode("MFN-2");
        claimed.setCost(10.0);
        when(warehouseRepository.findByDeliveryId(STORE_ID, "delivery-1")).thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.updateUnitCosts(STORE_ID, "delivery-1", Map.of("MFN-1", 8.5));

        // then
        assertThat(claimed.getCost()).isEqualTo(10.0);
        verify(warehouseRepository, never()).save(any());
    }

    private WarehouseItem warehouseItemInStatus(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem(STORE_ID, PROVIDER, "Other", "test", "old-ean", "OLD-MFN", 10.0, 1);
        item.setStatus(status);
        return item;
    }
}
