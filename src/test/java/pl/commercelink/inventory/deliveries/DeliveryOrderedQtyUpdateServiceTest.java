package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.Document;
import pl.commercelink.orders.event.Event;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DeliveryOrderedQtyUpdateServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String MFN = "MFN-1";

    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;

    @InjectMocks
    private DeliveryOrderedQtyUpdateService deliveryOrderedQtyUpdateService;

    @Test
    @DisplayName("run increases existing warehouse item and delivery cost when qty grows")
    void runIncreasesExistingWarehouseItem() {
        // given
        Delivery delivery = delivery(
                orderAllocation("order-item-1", 2, 10.0),
                warehouseAllocation("warehouse-item-1", 3, 10.0, true));
        delivery.setTotalCost(50.0);

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 8);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(warehouseAllocationsManager).increaseQty(STORE_ID, "warehouse-item-1", 3);
        verifyNoMoreInteractions(warehouseAllocationsManager);
        assertThat(delivery.getTotalCost()).isEqualTo(80.0);
        assertThat(delivery.getEvents()).extracting(Event::getName).contains("DELIVERY_ITEM_QTY_UPDATED");
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    @DisplayName("run creates new ordered warehouse item when qty grows and no warehouse allocation exists")
    void runCreatesWarehouseItemWhenNoneExists() {
        // given
        Delivery delivery = delivery(orderAllocation("order-item-1", 2, 10.0));
        delivery.setTotalCost(20.0);
        DeliveryItem item = delivery.getItems().getFirst();

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 5);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(warehouseAllocationsManager).createOrdered(STORE_ID, delivery.getDeliveryId(), delivery.getProvider(), item, 3);
        verifyNoMoreInteractions(warehouseAllocationsManager);
        assertThat(delivery.getTotalCost()).isEqualTo(50.0);
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    @DisplayName("run decreases warehouse items across allocations when qty shrinks")
    void runDecreasesWarehouseItems() {
        // given
        Delivery delivery = delivery(
                orderAllocation("order-item-1", 1, 10.0),
                warehouseAllocation("warehouse-item-1", 2, 10.0, true),
                warehouseAllocation("warehouse-item-2", 3, 20.0, true));
        delivery.setTotalCost(90.0);
        when(warehouseAllocationsManager.decreaseQty(STORE_ID, "warehouse-item-1", 4)).thenReturn(2);
        when(warehouseAllocationsManager.decreaseQty(STORE_ID, "warehouse-item-2", 2)).thenReturn(2);

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 2);

        // then
        assertThat(result.isSuccess()).isTrue();
        verify(warehouseAllocationsManager).decreaseQty(STORE_ID, "warehouse-item-1", 4);
        verify(warehouseAllocationsManager).decreaseQty(STORE_ID, "warehouse-item-2", 2);
        assertThat(delivery.getTotalCost()).isEqualTo(30.0);
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    @DisplayName("run rejects qty below order reservations and received units")
    void runRejectsQtyBelowMin() {
        // given
        Delivery delivery = delivery(
                orderAllocation("order-item-1", 3, 10.0),
                warehouseAllocation("warehouse-item-1", 2, 10.0, true));

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 2);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.qty.below.min");
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    @DisplayName("run counts received warehouse units into the minimum qty")
    void runCountsReceivedUnitsIntoMin() {
        // given
        Delivery delivery = delivery(
                warehouseAllocation("warehouse-item-1", 2, 10.0, false),
                warehouseAllocation("warehouse-item-2", 3, 10.0, true));

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 1);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.qty.below.min");
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    @DisplayName("run rejects edit when delivery has been received")
    void runRejectsReceivedDelivery() {
        // given
        Delivery delivery = delivery(warehouseAllocation("warehouse-item-1", 2, 10.0, true));
        delivery.setReceivedAt(LocalDateTime.now());

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 5);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.qty.not.editable");
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    @DisplayName("run rejects edit when delivery has any documents")
    void runRejectsDeliveryWithDocuments() {
        // given
        Delivery delivery = delivery(warehouseAllocation("warehouse-item-1", 2, 10.0, true));
        delivery.getDocuments().add(new Document());

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 5);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.qty.not.editable");
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    @DisplayName("run rejects unknown manufacturer code")
    void runRejectsUnknownMfn() {
        // given
        Delivery delivery = delivery(warehouseAllocation("warehouse-item-1", 2, 10.0, true));

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), "unknown-mfn", 5);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("error.message.delivery.qty.item.not.found");
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    @DisplayName("run leaves delivery untouched when qty does not change")
    void runIgnoresUnchangedQty() {
        // given
        Delivery delivery = delivery(warehouseAllocation("warehouse-item-1", 2, 10.0, true));

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(STORE_ID, delivery.getDeliveryId(), MFN, 2);

        // then
        assertThat(result.isSuccess()).isTrue();
        verifyNoInteractions(warehouseAllocationsManager, deliveriesRepository);
    }

    private Delivery delivery(Allocation... allocations) {
        Delivery delivery = new Delivery(STORE_ID, "EXT-1", "TechData");
        delivery.setAllocations(List.of(allocations));
        delivery.setItems(DeliveryItem.groupAndUnify(List.of(allocations)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        return delivery;
    }

    private Allocation orderAllocation(String itemId, int qty, double unitCost) {
        Allocation allocation = allocation(AllocationType.Order, "order-1", itemId, qty, unitCost);
        allocation.setInAllocation(true);
        return allocation;
    }

    private Allocation warehouseAllocation(String itemId, int qty, double unitCost, boolean inAllocation) {
        Allocation allocation = allocation(AllocationType.Warehouse, null, itemId, qty, unitCost);
        allocation.setInAllocation(inAllocation);
        return allocation;
    }

    private Allocation allocation(AllocationType type, String orderId, String itemId, int qty, double unitCost) {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(orderId, itemId, "Warehouse"));
        allocation.setType(type);
        allocation.setMfn(MFN);
        allocation.setEan("1234567890123");
        allocation.setQty(qty);
        allocation.setUnitCost(unitCost);
        return allocation;
    }

    @Test
    void runRejectsDropshipDelivery() {
        // given
        Delivery delivery = new Delivery("store-1", "ACME-DS-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        when(deliveriesQueryService.fetchDeliveryWithAllocations("store-1", delivery.getDeliveryId())).thenReturn(delivery);

        // when
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run("store-1", delivery.getDeliveryId(), "MFN-1", 5);

        // then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("deliveries.editOrderedQty.error.dropship");
        verifyNoInteractions(warehouseAllocationsManager);
        verify(deliveriesRepository, never()).save(any());
    }
}
