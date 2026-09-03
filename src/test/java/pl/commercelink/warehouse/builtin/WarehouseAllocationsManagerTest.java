package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("reassign stamps the claimed delivery id of moved items with the target delivery")
    void reassignStampsClaimedDeliveryIdWithTargetDelivery() {
        // given
        WarehouseItem warehouseItem = warehouseItemInStatus(FulfilmentStatus.Ordered);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(warehouseItem);
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, ITEM_ID, "Warehouse"));

        // when
        warehouseAllocationsManager.reassign(STORE_ID, "delivery-target", List.of(allocation));

        // then
        verify(warehouseRepository).save(warehouseItem);
        assertThat(warehouseItem.getDeliveryId()).isEqualTo("delivery-target");
        assertThat(warehouseItem.getClaimedDeliveryId()).isEqualTo("delivery-target");
    }

    @Test
    @DisplayName("release returns an Ordered item with no purchase claim to the allocation pool unchanged")
    void releaseReturnsAllDeliveryWarehouseItemsToAllocationPool() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(4);
        claimed.setPurchaseClaimQty(0);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        assertThat(claimed.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
        assertThat(claimed.getDeliveryId()).isEqualTo("Acme");
        assertThat(claimed.getQty()).isEqualTo(4);
        assertThat(claimed.getPurchaseClaimQty()).isEqualTo(0);
        verify(warehouseRepository).save(claimed);
    }

    @Test
    @DisplayName("release deletes an Ordered item that exists only because of a purchase claim")
    void releaseDeletesItemFullyExplainedByClaim() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(3);
        claimed.setPurchaseClaimQty(3);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        verify(warehouseRepository).delete(claimed);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("release reverts the claim-inflated qty of a partially claimed item and returns the rest to the pool")
    void releaseRevertsPartiallyClaimedItemToPool() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(5);
        claimed.setPurchaseClaimQty(2);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        assertThat(claimed.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
        assertThat(claimed.getDeliveryId()).isEqualTo("Acme");
        assertThat(claimed.getQty()).isEqualTo(3);
        assertThat(claimed.getPurchaseClaimQty()).isEqualTo(0);
        verify(warehouseRepository).save(claimed);
        verify(warehouseRepository, never()).delete(any(WarehouseItem.class));
    }

    @Test
    @DisplayName("release restores a claim-shrunk item's qty when returning it to the pool")
    void releaseRestoresClaimShrunkItemQtyToPool() {
        // given
        WarehouseItem claimed = new WarehouseItem();
        claimed.setStatus(FulfilmentStatus.Ordered);
        claimed.setDeliveryId("delivery-1");
        claimed.setQty(3);
        claimed.setPurchaseClaimQty(-2);
        when(warehouseRepository.findByDeliveryIdAndStatuses(STORE_ID, "delivery-1", List.of(FulfilmentStatus.Ordered)))
                .thenReturn(List.of(claimed));

        // when
        warehouseAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        assertThat(claimed.getStatus()).isEqualTo(FulfilmentStatus.Allocation);
        assertThat(claimed.getDeliveryId()).isEqualTo("Acme");
        assertThat(claimed.getQty()).isEqualTo(5);
        assertThat(claimed.getPurchaseClaimQty()).isEqualTo(0);
        verify(warehouseRepository).save(claimed);
        verify(warehouseRepository, never()).delete(any(WarehouseItem.class));
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
        warehouseAllocationsManager.release(STORE_ID, "delivery-1", "Acme");

        // then
        assertThat(delivered.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(delivered.getDeliveryId()).isEqualTo("delivery-1");
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUnitCosts returns the summed cost delta for changed items")
    void updateUnitCostsReturnsSummedCostDelta() {
        // given
        WarehouseItem item = new WarehouseItem();
        item.setQty(3);
        item.setCost(10.0);
        item.setManufacturerCode("MFN-1");
        when(warehouseRepository.findByDeliveryId(STORE_ID, "delivery-1")).thenReturn(List.of(item));

        // when
        double delta = warehouseAllocationsManager.updateUnitCosts(STORE_ID, "delivery-1", Map.of("MFN-1", 12.5));

        // then
        assertThat(delta).isEqualTo(7.5);
        assertThat(item.getCost()).isEqualTo(12.5);
        verify(warehouseRepository).save(item);
    }

    @Test
    @DisplayName("updateUnitCosts returns zero and skips save when the price is unchanged")
    void updateUnitCostsReturnsZeroWhenPriceUnchanged() {
        // given
        WarehouseItem item = new WarehouseItem();
        item.setQty(3);
        item.setCost(10.0);
        item.setManufacturerCode("MFN-1");
        when(warehouseRepository.findByDeliveryId(STORE_ID, "delivery-1")).thenReturn(List.of(item));

        // when
        double delta = warehouseAllocationsManager.updateUnitCosts(STORE_ID, "delivery-1", Map.of("MFN-1", 10.0));

        // then
        assertThat(delta).isZero();
        verify(warehouseRepository, never()).save(any(WarehouseItem.class));
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
        double delta = warehouseAllocationsManager.updateUnitCosts(STORE_ID, "delivery-1", Map.of("MFN-1", 8.5));

        // then
        assertThat(delta).isZero();
        assertThat(claimed.getCost()).isEqualTo(10.0);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("commit marks a warehouse item still in Allocation as Ordered, applies the qty adjustment and saves")
    void commitMarksAWarehouseItemStillInAllocationAsOrderedAppliesQtyAdjustmentAndSaves() {
        // given
        WarehouseItem claimable = warehouseItemInStatus(FulfilmentStatus.Allocation);
        claimable.setQty(1);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(claimable);

        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, ITEM_ID, "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setQty(2);
        allocation.setSelected(true);

        DeliveryItem item = new DeliveryItem();
        item.setMfn("OLD-MFN");
        item.setUnitCost(55.5);
        item.setRequestedQty(5);
        item.setAllocations(List.of(allocation));

        // when
        warehouseAllocationsManager.commit(STORE_ID, "new-delivery", PROVIDER, List.of(item));

        // then
        assertThat(claimable.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(claimable.getDeliveryId()).isEqualTo("new-delivery");
        assertThat(claimable.getCost()).isEqualTo(55.5);
        assertThat(claimable.getQty()).isEqualTo(4);
        assertThat(claimable.getPurchaseClaimQty()).isEqualTo(3);
        verify(warehouseRepository).save(claimable);
    }

    @Test
    @DisplayName("commit creates a new warehouse item born entirely from the claim and stamps its full qty as the purchase claim")
    void commitCreatesNewWarehouseItemAndStampsFullQtyAsPurchaseClaim() {
        // given
        WarehouseItem created = new WarehouseItem();
        created.setQty(5);
        when(warehouseItemFactory.create(eq(STORE_ID), eq(PROVIDER), any(DeliveryItem.class))).thenReturn(created);

        DeliveryItem item = new DeliveryItem();
        item.setMfn("NEW-MFN");
        item.setUnitCost(20.0);
        item.setRequestedQty(5);

        // when
        warehouseAllocationsManager.commit(STORE_ID, "new-delivery", PROVIDER, List.of(item));

        // then
        assertThat(created.getStatus()).isEqualTo(FulfilmentStatus.Ordered);
        assertThat(created.getDeliveryId()).isEqualTo("new-delivery");
        assertThat(created.getQty()).isEqualTo(5);
        assertThat(created.getPurchaseClaimQty()).isEqualTo(5);
        verify(warehouseRepository).save(created);
    }

    @Test
    @DisplayName("commit does not re-mark or resave a warehouse item that is no longer claimable")
    void commitSkipsWarehouseItemNoLongerInAllocationState() {
        // given
        WarehouseItem alreadyOrdered = warehouseItemInStatus(FulfilmentStatus.Ordered);
        alreadyOrdered.setDeliveryId("existing-delivery");
        alreadyOrdered.setQty(2);
        when(warehouseRepository.findById(STORE_ID, ITEM_ID)).thenReturn(alreadyOrdered);

        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, ITEM_ID, "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setQty(2);
        allocation.setSelected(true);

        DeliveryItem item = new DeliveryItem();
        item.setMfn("OLD-MFN");
        item.setUnitCost(99.0);
        item.setRequestedQty(2);
        item.setAllocations(List.of(allocation));

        // when
        warehouseAllocationsManager.commit(STORE_ID, "new-delivery", PROVIDER, List.of(item));

        // then
        verify(warehouseRepository, never()).save(any());
        assertThat(alreadyOrdered.getQty()).isEqualTo(2);
        assertThat(alreadyOrdered.getDeliveryId()).isEqualTo("existing-delivery");
    }

    private WarehouseItem warehouseItemInStatus(FulfilmentStatus status) {
        WarehouseItem item = new WarehouseItem(STORE_ID, PROVIDER, "Other", "test", "old-ean", "OLD-MFN", 10.0, 1);
        item.setStatus(status);
        return item;
    }
}
