package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.autoconfigure.OptimisticLockingProperties;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.testsupport.RetryingOptimisticLockingExecutor;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Elko";

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrderAllocationsManager orderAllocationsManager;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private DeliveryCostSync deliveryCostSync;
    @Spy
    private OptimisticLockingExecutor optimisticLockingExecutor =
            new OptimisticLockingExecutor(new OptimisticLockingProperties());

    @InjectMocks
    private DeliveryCreationService service;

    @Test
    void completePendingAppliesConfirmedCostsAndFoldsDeltaIntoTotalCost() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.increaseTotalCost(100.0);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setExternalDeliveryId("EXT-9");
        form.setShippingCost(20.0);
        form.setPaymentCost(5.0);
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 15));
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        form.setItems(List.of(item));
        when(deliveryCostSync.apply(STORE_ID, delivery.getDeliveryId(), Map.of("MFN-1", 8.5))).thenReturn(3.0);

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        assertThat(delivery.getTotalCost()).isEqualTo(128.0);
        assertThat(delivery.getShippingCost()).isEqualTo(20.0);
        assertThat(delivery.getPaymentCost()).isEqualTo(5.0);
        assertThat(delivery.getOrderStatus()).isNull();
        assertThat(delivery.getExternalDeliveryId()).isEqualTo("EXT-9");
        verify(deliveriesRepository).save(delivery);
        verify(orderAllocationsManager, never()).commit(any(), any(), any(), any());
        verify(warehouseAllocationsManager, never()).commit(any(), any(), any(), any());
        verify(orderAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 15));
    }

    @Test
    void completePendingLosingTheSaveRaceAppliesTheSameCostDeltaToTheCurrentDelivery() {
        // given - the confirmed costs are synced onto the items, then a concurrent edit wins the save
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.increaseTotalCost(100.0);
        Delivery current = new Delivery(STORE_ID, null, "Acme");
        current.setDeliveryId(delivery.getDeliveryId());
        current.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        current.increaseTotalCost(100.0);
        current.setComment("edited meanwhile");
        DeliveryCreationForm form = pendingForm();
        when(deliveryCostSync.apply(STORE_ID, delivery.getDeliveryId(), Map.of("MFN-1", 8.5))).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("version changed")).when(deliveriesRepository).save(delivery);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId())).thenReturn(current);

        // when
        service.completePending(STORE_ID, delivery, form,
                target -> target.addEvent(new Event(EventType.action, "DELIVERY_ORDERED_AUTOMATICALLY", null)));

        // then
        verify(deliveryCostSync, times(1)).apply(any(), any(), any());
        verify(deliveriesRepository).save(current);
        assertThat(current.getTotalCost()).isEqualTo(70.0);
        assertThat(current.getOrderStatus()).isNull();
        assertThat(current.getExternalDeliveryId()).isEqualTo("EXT-9");
        assertThat(current.getComment()).isEqualTo("edited meanwhile");
        assertThat(current.getEvents()).extracting(Event::getName).contains("DELIVERY_ORDERED_AUTOMATICALLY");
        verify(orderAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 15));
    }

    @Test
    void completePendingLosingTheSaveRaceToAnAlreadyCompletedDeliveryAddsNothing() {
        // given - another writer already completed the purchase, delta included
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        Delivery current = new Delivery(STORE_ID, null, "Acme");
        current.setDeliveryId(delivery.getDeliveryId());
        current.increaseTotalCost(95.0);
        when(deliveryCostSync.apply(any(), any(), any())).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("version changed")).when(deliveriesRepository).save(delivery);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId())).thenReturn(current);

        // when
        service.completePending(STORE_ID, delivery, pendingForm(),
                target -> target.addEvent(new Event(EventType.action, "DELIVERY_ORDERED_AUTOMATICALLY", null)));

        // then
        assertThat(current.getTotalCost()).isEqualTo(95.0);
        assertThat(current.getEvents()).isEmpty();
        verify(orderAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 15));
    }

    @Test
    void completePendingSurvivesTwoConsecutiveConflictsAndAppliesTheDeltaOnce() {
        // given - the first save and the first recovery save both lose, the second recovery save wins
        DeliveryCreationService retrying = serviceWithRealExecutor();
        Delivery delivery = pendingDelivery();
        Delivery firstRead = storedCopyOf(delivery, 100.0, DeliveryOrderStatus.ORDER_DISPATCHED);
        Delivery secondRead = storedCopyOf(delivery, 100.0, DeliveryOrderStatus.ORDER_DISPATCHED);
        when(deliveryCostSync.apply(any(), any(), any())).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("v")).when(deliveriesRepository).save(delivery);
        doThrow(new ConditionalCheckFailedException("v")).when(deliveriesRepository).save(firstRead);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId()))
                .thenReturn(firstRead, secondRead);
        List<String> callerEvents = new java.util.ArrayList<>();

        // when
        retrying.completePending(STORE_ID, delivery, pendingForm(), target -> callerEvents.add("applied"));

        // then
        verify(deliveryCostSync, times(1)).apply(any(), any(), any());
        verify(deliveriesRepository).save(secondRead);
        assertThat(secondRead.getTotalCost()).isEqualTo(70.0);
        assertThat(secondRead.getOrderStatus()).isNull();
        assertThat(callerEvents).hasSize(2); // once per attempt; only the saved attempt's object is stored
        verify(orderAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 15));
    }

    @Test
    void completePendingPropagatesExhaustionAndDoesNotMarkTheClaimsOrdered() {
        // given - every save conflicts
        DeliveryCreationService retrying = serviceWithRealExecutor();
        Delivery delivery = pendingDelivery();
        when(deliveryCostSync.apply(any(), any(), any())).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("v")).when(deliveriesRepository).save(any(Delivery.class));
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId()))
                .thenAnswer(i -> storedCopyOf(delivery, 100.0, DeliveryOrderStatus.ORDER_DISPATCHED));

        // when / then
        assertThatThrownBy(() -> retrying.completePending(STORE_ID, delivery, pendingForm()))
                .isInstanceOf(OptimisticLockingExhaustedException.class);
        verify(orderAllocationsManager, never()).markClaimedAsOrdered(any(), any(), any());
    }

    @Test
    void completePendingFailsLoudlyWhenTheDeliveryIsGoneOnReRead() {
        // given - the supplier holds the order but the delivery was deleted meanwhile
        DeliveryCreationService retrying = serviceWithRealExecutor();
        Delivery delivery = pendingDelivery();
        when(deliveryCostSync.apply(any(), any(), any())).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("v")).when(deliveriesRepository).save(delivery);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId())).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> retrying.completePending(STORE_ID, delivery, pendingForm()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXT-9");
        verify(deliveriesRepository, times(1)).save(any(Delivery.class));
        verify(orderAllocationsManager, never()).markClaimedAsOrdered(any(), any(), any());
    }

    @Test
    void completePendingStillCompletesADeliveryThatFailedMeanwhile() {
        // given - the re-read delivery went FAILED after the first attempt; the supplier did place the order
        DeliveryCreationService retrying = serviceWithRealExecutor();
        Delivery delivery = pendingDelivery();
        Delivery current = storedCopyOf(delivery, 100.0, DeliveryOrderStatus.FAILED);
        when(deliveryCostSync.apply(any(), any(), any())).thenReturn(-30.0);
        doThrow(new ConditionalCheckFailedException("v")).when(deliveriesRepository).save(delivery);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, delivery.getDeliveryId())).thenReturn(current);

        // when
        retrying.completePending(STORE_ID, delivery, pendingForm());

        // then
        verify(deliveryCostSync, times(1)).apply(any(), any(), any());
        verify(deliveriesRepository).save(current);
        assertThat(current.getOrderStatus()).isNull();
        assertThat(current.getTotalCost()).isEqualTo(70.0);
    }

    private DeliveryCreationService serviceWithRealExecutor() {
        DeliveryCreationService retrying = new DeliveryCreationService();
        org.springframework.test.util.ReflectionTestUtils.setField(retrying, "deliveriesRepository", deliveriesRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(retrying, "orderAllocationsManager", orderAllocationsManager);
        org.springframework.test.util.ReflectionTestUtils.setField(retrying, "warehouseAllocationsManager", warehouseAllocationsManager);
        org.springframework.test.util.ReflectionTestUtils.setField(retrying, "deliveryCostSync", deliveryCostSync);
        org.springframework.test.util.ReflectionTestUtils.setField(retrying, "optimisticLockingExecutor",
                RetryingOptimisticLockingExecutor.create());
        return retrying;
    }

    private static Delivery pendingDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.increaseTotalCost(100.0);
        return delivery;
    }

    private static Delivery storedCopyOf(Delivery delivery, double totalCost, DeliveryOrderStatus status) {
        Delivery stored = new Delivery(STORE_ID, null, "Acme");
        stored.setDeliveryId(delivery.getDeliveryId());
        stored.setOrderStatus(status);
        stored.increaseTotalCost(totalCost);
        return stored;
    }

    private static DeliveryCreationForm pendingForm() {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setExternalDeliveryId("EXT-9");
        form.setShippingCost(0.0);
        form.setPaymentCost(0.0);
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 15));
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        form.setItems(List.of(item));
        return form;
    }

    @Test
    @DisplayName("claimAllocationsForPurchase claims both sides without ordering anything")
    void claimAllocationsForPurchaseClaimsBothSidesWithoutOrderingAnything() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        form.setItems(List.of(item));

        // when
        service.claimAllocationsForPurchase(STORE_ID, delivery, form);

        // then
        verify(orderAllocationsManager).claim(STORE_ID, delivery.getDeliveryId(), form.getItems());
        verify(warehouseAllocationsManager).claim(STORE_ID, delivery.getDeliveryId(), "Acme", form.getItems());
        verify(orderAllocationsManager, never()).commit(any(), any(), any(), any());
        verify(warehouseAllocationsManager, never()).commit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("claimAllocationsForPurchase leaves the warehouse alone for a dropship delivery")
    void claimAllocationsForPurchaseLeavesTheWarehouseAloneForADropshipDelivery() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        Allocation selected = new Allocation();
        selected.setType(AllocationType.Order);
        selected.setQty(2);
        selected.setSelected(true);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        item.setAllocations(List.of(selected));
        form.setItems(List.of(item));

        // when
        service.claimAllocationsForPurchase(STORE_ID, delivery, form);

        // then: the clamp keeps the full requested quantity because it matches the selected allocation
        assertEquals(2, item.getRequestedQty());
        verify(orderAllocationsManager).claim(STORE_ID, delivery.getDeliveryId(), form.getItems());
        verify(warehouseAllocationsManager, never()).claim(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a dropship purchase claims exactly what the allocations need, whatever the form asks for")
    void claimAllocationsForPurchaseClampsDropshipQuantities() {
        // given: a tampered form asks for more than the selected order allocation covers
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        Allocation selected = new Allocation();
        selected.setType(AllocationType.Order);
        selected.setQty(1);
        selected.setSelected(true);
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(9);
        item.setUnitCost(8.5);
        item.setAllocations(List.of(selected));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.getItems().add(item);

        // when
        service.claimAllocationsForPurchase(STORE_ID, delivery, form);

        // then
        assertEquals(1, item.getRequestedQty());
        assertEquals(8.5, delivery.getTotalCost());
        verify(warehouseAllocationsManager, never()).claim(any(), any(), any(), any());
    }

    @Test
    @DisplayName("completePending orders the claimed allocations after saving the delivery")
    void completePendingOrdersTheClaimedAllocationsAfterSavingTheDelivery() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setExternalDeliveryId("EXT-9");
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 25));
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        form.setItems(List.of(item));

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        InOrder inOrder = inOrder(deliveryCostSync, deliveriesRepository, orderAllocationsManager, warehouseAllocationsManager);
        inOrder.verify(deliveryCostSync).apply(STORE_ID, delivery.getDeliveryId(), Map.of("MFN-1", 8.5));
        inOrder.verify(deliveriesRepository).save(delivery);
        inOrder.verify(orderAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 25));
        inOrder.verify(warehouseAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId());
    }

    @Test
    @DisplayName("completePending survives a failure while ordering the claimed allocations")
    void completePendingSurvivesAFailureWhileOrderingTheClaimedAllocations() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 25));
        form.setItems(List.of());
        doThrow(new RuntimeException("boom")).when(orderAllocationsManager)
                .markClaimedAsOrdered(any(), any(), any());

        // when / then
        assertThatNoException().isThrownBy(() -> service.completePending(STORE_ID, delivery, form));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    @DisplayName("markClaimedAsOrdered still orders the warehouse side after the order side fails")
    void markClaimedAsOrderedStillOrdersTheWarehouseSideAfterTheOrderSideFails() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        LocalDate estimatedDeliveryAt = LocalDate.of(2026, 9, 25);
        doThrow(new RuntimeException("boom")).when(orderAllocationsManager)
                .markClaimedAsOrdered(any(), any(), any());

        // when
        assertThatNoException().isThrownBy(() ->
                service.markClaimedAsOrdered(STORE_ID, delivery, estimatedDeliveryAt));

        // then
        verify(warehouseAllocationsManager).markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId());
    }

    @Test
    void completePendingSkipsItemsWithoutRequestedQty() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        DeliveryItem ordered = new DeliveryItem();
        ordered.setMfn("MFN-1");
        ordered.setRequestedQty(1);
        ordered.setUnitCost(8.5);
        DeliveryItem skipped = new DeliveryItem();
        skipped.setMfn("MFN-2");
        skipped.setRequestedQty(0);
        skipped.setUnitCost(4.0);
        form.setItems(List.of(ordered, skipped));

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        verify(deliveryCostSync).apply(STORE_ID, delivery.getDeliveryId(), Map.of("MFN-1", 8.5));
    }

    @Test
    void releaseUnselectedAllocationsFreesTheUncheckedOrderItemsWithoutCreatingAnything() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        OrderItem orderItem = new OrderItem(order.getOrderId(), "Category", "Product", 1, 100.0, null, false);
        orderItem.setItemId("item-1");
        orderItem.setDeliveryId(PROVIDER);
        orderItem.setStatus(FulfilmentStatus.Allocation);
        Allocation unchecked = Allocation.fromOrderItem(order, orderItem);
        unchecked.setSelected(false);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(0);
        item.setAllocations(List.of(unchecked));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        form.setRemoveUnselected(true);
        form.getItems().add(item);

        // when
        service.releaseUnselectedAllocations(STORE_ID, form);

        // then
        verify(orderAllocationsManager).remove(STORE_ID, "order-1", List.of("item-1"));
        verify(orderAllocationsManager, never()).commit(any(), any(), any(), any());
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void claimAllocationsTakesOwnershipOfTheOrderAllocationsAndTheirCost() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider(PROVIDER);
        form.setEstimatedDeliveryAt(LocalDate.now());
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(2);
        item.setUnitCost(90.0);
        form.getItems().add(item);

        // when
        service.claimAllocations(STORE_ID, delivery, form);

        // then
        assertEquals(180.0, delivery.getTotalCost());
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq("delivery-1"), any(), eq(form.getItems()));
        verify(warehouseAllocationsManager).commit(STORE_ID, "delivery-1", PROVIDER, form.getItems());
    }

    @Test
    void releaseAllocationsHandsBothAllocationGroupsBackToTheSupplierAndTheWarehouse() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setProvider(PROVIDER);

        // when
        service.releaseAllocations(STORE_ID, delivery);

        // then
        verify(orderAllocationsManager).release(STORE_ID, "delivery-1", PROVIDER);
        verify(warehouseAllocationsManager).release(STORE_ID, "delivery-1", PROVIDER);
    }

    @Test
    void runStillCreatesDeliveryFromScratch() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setExternalDeliveryId("ELKO-2");
        form.setProvider(PROVIDER);
        form.setEstimatedDeliveryAt(LocalDate.now());
        form.setShippingCost(10.0);
        form.setPaymentCost(5.0);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(3);
        item.setUnitCost(50.0);
        form.getItems().add(item);

        // when
        String deliveryId = service.run(STORE_ID, form);

        // then
        assertNotNull(deliveryId);
        ArgumentCaptor<Delivery> deliveryCaptor = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(deliveryCaptor.capture());
        Delivery saved = deliveryCaptor.getValue();
        assertEquals(deliveryId, saved.getDeliveryId());
        assertEquals("ELKO-2", saved.getExternalDeliveryId());
        assertEquals(165.0, saved.getTotalCost());
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq(deliveryId), any(), eq(form.getItems()));
        verify(warehouseAllocationsManager).commit(STORE_ID, deliveryId, form.getProvider(), form.getItems());
    }

    @Test
    void runRefusesAFormWithoutTheSupplierOrderNumberOrDateBeforeTouchingAnything() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setRemoveUnselected(true);
        form.setEstimatedDeliveryAt(null);
        form.setExternalDeliveryId("EXT-1");

        // when / then
        assertThatThrownBy(() -> service.run("store-1", form))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orderAllocationsManager, warehouseAllocationsManager, deliveriesRepository);
    }

    @Test
    void stampsTheResolvedConnectionModeOnAManuallyCreatedDelivery() {
        // given
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setExternalDeliveryId("ELKO-3");
        form.setProvider(PROVIDER);
        form.setEstimatedDeliveryAt(LocalDate.now());
        form.setShippingCost(10.0);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(1);
        item.setUnitCost(50.0);
        form.getItems().add(item);
        when(supplierConnectionModeResolver.resolve(STORE_ID, PROVIDER)).thenReturn(ConnectionMode.OWN);

        // when
        service.run(STORE_ID, form);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesRepository).save(saved.capture());
        assertThat(saved.getValue().getConnectionMode()).isEqualTo(ConnectionMode.OWN);
    }

    @Test
    @DisplayName("completing a dropship purchase stamps the delivery date and orders the claimed items")
    void completePendingStampsTheDateAndOrdersClaimedDropshipItems() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setExternalDeliveryId("EXT-9");
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 9, 14));
        form.setItems(List.of());

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        assertThat(delivery.getEstimatedDeliveryAt()).isEqualTo(LocalDate.of(2026, 9, 14));
        verify(orderAllocationsManager)
                .markClaimedAsOrdered(STORE_ID, delivery.getDeliveryId(), LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("a dropship completion leaves the delivery header alone")
    void completePendingLeavesTheDropshipHeaderAlone() {
        // given
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setTax(23);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setShippingCost(99.0);
        form.setPaymentCost(7.0);
        form.setTax(8);
        form.setItems(List.of());

        // when
        service.completePending(STORE_ID, delivery, form);

        // then
        assertThat(delivery.getShippingCost()).isEqualTo(0.0);
        assertThat(delivery.getPaymentCost()).isEqualTo(0.0);
        assertThat(delivery.getTax()).isEqualTo(23);
    }

    @Test
    void claimAllocationsNeverTouchesTheWarehouseForADropshipDelivery() {
        // given: a tampered form asks for more than the selected order allocations cover
        Delivery delivery = new Delivery(STORE_ID, "ACME-DS-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        OrderItem orderItem = new OrderItem(order.getOrderId(), "Category", "Product", 1, 100.0, null, false);
        orderItem.setItemId("item-1");
        orderItem.setDeliveryId("Acme");
        orderItem.setStatus(FulfilmentStatus.Allocation);
        Allocation selected = Allocation.fromOrderItem(order, orderItem);
        selected.setSelected(true);
        DeliveryItem item = new DeliveryItem();
        item.setRequestedQty(3);
        item.setUnitCost(90.0);
        item.setAllocations(List.of(selected));
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.getItems().add(item);

        // when
        service.claimAllocations(STORE_ID, delivery, form);

        // then: only the selected order allocation is claimed and priced, nothing goes to the warehouse
        assertEquals(1, item.getRequestedQty());
        assertEquals(90.0, delivery.getTotalCost());
        verify(orderAllocationsManager).commit(eq(STORE_ID), eq(delivery.getDeliveryId()), any(), eq(form.getItems()));
        verify(warehouseAllocationsManager, never()).commit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("claimAllocationsForPurchase saves the delivery before reserving the order items for it")
    void claimAllocationsForPurchaseSavesTheDeliveryBeforeClaimingTheOrderAllocations() {
        // given: a claim is written onto the items and hides them from the allocation screen, so a delivery
        // saved only afterwards would leave them reserved for something that does not exist
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setItems(List.of());

        // when
        service.claimAllocationsForPurchase(STORE_ID, delivery, form);

        // then
        InOrder inOrder = inOrder(deliveriesRepository, orderAllocationsManager);
        inOrder.verify(deliveriesRepository).save(delivery);
        inOrder.verify(orderAllocationsManager).claim(eq(STORE_ID), eq(delivery.getDeliveryId()), any());
    }

    @Test
    @DisplayName("claimAllocations saves the delivery before the order items start pointing at it")
    void claimAllocationsSavesTheDeliveryBeforeCommittingTheOrderAllocations() {
        // given
        Delivery delivery = new Delivery(STORE_ID, "ACME-DS-2", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        form.setItems(List.of());

        // when
        service.claimAllocations(STORE_ID, delivery, form);

        // then
        InOrder inOrder = inOrder(deliveriesRepository, orderAllocationsManager);
        inOrder.verify(deliveriesRepository).save(delivery);
        inOrder.verify(orderAllocationsManager).commit(eq(STORE_ID), eq(delivery.getDeliveryId()), any(), any());
    }
}
