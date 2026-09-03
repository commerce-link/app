package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void completeDropshipPendingSyncsPricesWithoutTouchingTheHeader() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-1");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setShippingCost(15.0);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setExternalDeliveryId("ACME-DS-1");
        form.setProvider("Acme");
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(2);
        item.setUnitCost(8.5);
        form.getItems().add(item);
        when(deliveryCostSync.apply(STORE_ID, "delivery-1", Map.of("MFN-1", 8.5))).thenReturn(3.0);

        // when
        service.completeDropshipPending(STORE_ID, delivery, form);

        // then
        assertEquals("ACME-DS-1", delivery.getExternalDeliveryId());
        assertNull(delivery.getOrderStatus());
        assertEquals(15.0, delivery.getShippingCost());
        assertEquals(3.0, delivery.getTotalCost());
        verify(deliveriesRepository).save(delivery);
        verify(warehouseAllocationsManager, never()).commit(any(), any(), any(), any());
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
}
