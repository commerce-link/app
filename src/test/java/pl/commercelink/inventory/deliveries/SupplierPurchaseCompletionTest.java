package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierOrderAwaitingSupplierException;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderOutcomeUnknownException;
import pl.commercelink.inventory.supplier.api.SupplierOrderRejectedException;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierPurchaseCompletionTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String DELIVERY_ID = "delivery-1";
    private static final String EAN = "5900000000001";
    private static final String MFN = "MFN-1";

    @Mock
    private SupplierProviderResolver supplierProviderResolver;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DeliveryCreationService deliveryCreationService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DeliveryTaxResolver deliveryTaxResolver;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierProvider supplierProvider;
    @Mock
    private SupplierSkuResolver supplierSkuResolver;
    @Mock
    private SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    @Mock
    private OrderIdRefreshEventPublisher orderIdRefreshEventPublisher;
    @Mock
    private SupplierPurchaseCompletionEventPublisher supplierPurchaseCompletionEventPublisher;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private SupplierConnectionModeResolver supplierConnectionModeResolver;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DropshipPurchaseService dropshipPurchaseService;

    @InjectMocks
    private SupplierPurchaseService service;

    @BeforeEach
    void setUp() {
        lenient().when(supplierProviderResolver.resolve(STORE_ID, PROVIDER)).thenReturn(supplierProvider);
        lenient().when(supplierSkuResolver.forStore(anyString(), anyString())).thenReturn((ean, mfn) -> "SKU-A");
        lenient().when(supplierRegistry.get(PROVIDER)).thenReturn(new SupplierInfo(
                PROVIDER, SupplierType.Distributor, 5, "PL",
                new ShippingPolicy(new ShippingTerms(2, new ShippingCostPolicy.Free()))));
        lenient().when(deliveryTaxResolver.resolveFor(PROVIDER)).thenReturn(1.23);
    }

    @Test
    void completedReservationFinishesTheDeliveryLikeAnImmediateSuccess() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenReturn(new SupplierOrderResult("ZA/1", 50.0, "PLN",
                List.of(new SupplierQuote(EAN, MFN, 1, 50.0, "PLN"))));

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals("ZA/1", delivery.getExternalDeliveryId());
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        assertTrue(hasEvent(delivery, "DELIVERY_ORDERED_AUTOMATICALLY"));
        verify(deliveryCreationService).completePending(eq(STORE_ID), eq(delivery), any(), any());
        verify(supplierProvider, never()).placeOrder(any());
    }

    @Test
    void stillAwaitingBeforeLastAttemptThrowsForRedelivery() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenThrow(new SupplierOrderAwaitingSupplierException(
                "ZA/1", List.of("SKU-A: 0 of 1 reserved"), "still"));

        // when / then
        assertThrows(SupplierConfirmationPendingException.class,
                () -> service.completeAwaitingPurchase(request(delivery), 9));
        assertTrue(delivery.isAwaitingSupplierConfirmation());
        verify(deliveriesRepository, never()).save(delivery);
    }

    @Test
    void stillAwaitingOnLastAttemptHandsOverToOperator() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenThrow(new SupplierOrderAwaitingSupplierException(
                "ZA/1", List.of("SKU-A: 0 of 1 reserved"), "still"));

        // when
        service.completeAwaitingPurchase(request(delivery), 10);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals(DeliveryOrderStatus.ORDER_DISPATCHED, delivery.getOrderStatus());
        assertTrue(delivery.getOrderErrorMessage().contains("SKU-A: 0 of 1 reserved"));
        assertTrue(delivery.getOrderErrorMessage().contains("ZA/1"));
        assertTrue(delivery.getOrderErrorMessage().contains("was not confirmed by the supplier"));
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        assertTrue(hasEvent(delivery, "DELIVERY_SUPPLIER_CONFIRMATION_TIMEOUT"));
        verify(deliveriesRepository).save(delivery);
        verify(deliveryCreationService, never()).completePending(any(), any(), any(), any());
    }

    @Test
    void transientFailureCountsAsAnAttempt() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenThrow(new SupplierOrderException("timeout"));

        // when / then
        assertThrows(SupplierConfirmationPendingException.class,
                () -> service.completeAwaitingPurchase(request(delivery), 3));
        service.completeAwaitingPurchase(request(delivery), 10);
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals(DeliveryOrderStatus.ORDER_DISPATCHED, delivery.getOrderStatus());
        assertTrue(delivery.getOrderErrorMessage().contains("ZA/1"));
        assertTrue(delivery.getOrderErrorMessage().contains("the last check failed: timeout"),
                delivery.getOrderErrorMessage());
    }

    @Test
    void outcomeUnknownHandsOverWithSupplierMessage() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any()))
                .thenThrow(new SupplierOrderOutcomeUnknownException("order ZA/1 in unrecognised state"));

        // when
        service.completeAwaitingPurchase(request(delivery), 2);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals("order ZA/1 in unrecognised state", delivery.getOrderErrorMessage());
        assertFalse(delivery.isExternalDeliveryIdProvisional());
        assertEquals(DeliveryOrderStatus.ORDER_DISPATCHED, delivery.getOrderStatus());
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void rejectedCompletionFailsTheDelivery() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any()))
                .thenThrow(new SupplierOrderRejectedException("cancelled at Action"));

        // when
        service.completeAwaitingPurchase(request(delivery), 2);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals(DeliveryOrderStatus.FAILED, delivery.getOrderStatus());
        assertEquals("cancelled at Action", delivery.getOrderErrorMessage());
        assertFalse(delivery.isExternalDeliveryIdProvisional());
    }

    @Test
    void staleMessageIsAcknowledgedWithoutChanges() {
        // given
        Delivery delivery = awaitingDelivery();
        delivery.setAwaitingSupplierConfirmation(false); // operator / earlier attempt already settled it

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        verifyNoInteractions(supplierProvider);
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void messageForADeliveryNoLongerDispatchedIsIgnored() {
        // given
        Delivery delivery = awaitingDelivery();
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        verifyNoInteractions(supplierProvider);
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void messageForAMissingDeliveryIsIgnored() {
        // given
        SupplierPurchaseCompletionEventRequest request =
                new SupplierPurchaseCompletionEventRequest(STORE_ID, "gone", "ref-1", null);

        // when
        service.completeAwaitingPurchase(request, 1);

        // then
        verifyNoInteractions(supplierProvider);
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void messageForAnotherPurchaseRefIsIgnored() {
        // given
        Delivery delivery = awaitingDelivery();
        SupplierPurchaseCompletionEventRequest request = request(delivery);
        request.setPurchaseRef("other-ref");

        // when
        service.completeAwaitingPurchase(request, 1);

        // then
        verifyNoInteractions(supplierProvider);
    }

    @Test
    void unavailableGoodsDoNotBlockCompletion() {
        // given - our own reservation lowered Action's stock; checkAvailability reports zero
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.checkAvailability(any())).thenReturn(List.of(
                new SupplierQuote(EAN, MFN, 0, 50.0, "PLN")));
        when(supplierProvider.completePlacedOrder(any())).thenReturn(new SupplierOrderResult("ZA/1", 50.0, "PLN",
                List.of(new SupplierQuote(EAN, MFN, 1, 50.0, "PLN"))));

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        verify(deliveryCreationService).completePending(eq(STORE_ID), eq(delivery), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void completionHandsItsDeliveryChangesToCompletePendingForALostSaveRace() {
        // given - completePending replays these changes on the delivery as stored when a concurrent edit wins the save
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenReturn(new SupplierOrderResult("ZA/1", 50.0, "PLN",
                List.of(new SupplierQuote(EAN, MFN, 1, 50.0, "PLN"))));
        Delivery current = new Delivery(STORE_ID, "ZA/1", PROVIDER);
        current.setAwaitingSupplierConfirmation(true);
        current.setExternalDeliveryIdProvisional(true);

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        ArgumentCaptor<Consumer<Delivery>> changes = ArgumentCaptor.forClass(Consumer.class);
        verify(deliveryCreationService).completePending(eq(STORE_ID), eq(delivery), any(), changes.capture());
        changes.getValue().accept(current);
        assertFalse(current.isAwaitingSupplierConfirmation());
        assertFalse(current.isExternalDeliveryIdProvisional());
        assertTrue(hasEvent(current, "DELIVERY_ORDERED_AUTOMATICALLY"));
    }

    @Test
    void localFailureAfterSupplierCompletionPropagatesWithoutHandOver() {
        // given - the supplier completed the order, persisting it locally fails on the last attempt
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenReturn(new SupplierOrderResult("ZA/1", 50.0, "PLN",
                List.of(new SupplierQuote(EAN, MFN, 1, 50.0, "PLN"))));
        RuntimeException saveFailure = new RuntimeException("throttled");
        doThrow(saveFailure).when(deliveryCreationService).completePending(eq(STORE_ID), eq(delivery), any(), any());

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.completeAwaitingPurchase(request(delivery), 10));

        // then
        assertSame(saveFailure, thrown);
        assertNull(delivery.getOrderErrorMessage());
        assertFalse(hasEvent(delivery, "DELIVERY_SUPPLIER_CONFIRMATION_TIMEOUT"));
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void persistentFormRebuildFailureCountsAsAnAttemptAndHandsOverOnTheLast() {
        // given
        Delivery delivery = awaitingDelivery();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenThrow(new RuntimeException("allocations unavailable"));

        // when / then
        assertThrows(SupplierConfirmationPendingException.class,
                () -> service.completeAwaitingPurchase(request(delivery), 9));
        service.completeAwaitingPurchase(request(delivery), 10);
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals(DeliveryOrderStatus.ORDER_DISPATCHED, delivery.getOrderStatus());
        assertTrue(delivery.getOrderErrorMessage().contains("ZA/1"));
        assertTrue(delivery.getOrderErrorMessage().contains("the last check failed: allocations unavailable"),
                delivery.getOrderErrorMessage());
        assertFalse(delivery.getOrderErrorMessage().contains("not confirmed by the supplier"));
        assertTrue(hasEvent(delivery, "DELIVERY_SUPPLIER_CONFIRMATION_TIMEOUT"));
        verifyNoInteractions(supplierProvider);
    }

    @Test
    void completionWithoutAnOrderNumberIsAnUnknownOutcomeHandOver() {
        // given
        Delivery delivery = awaitingDelivery();
        when(supplierProvider.completePlacedOrder(any())).thenReturn(new SupplierOrderResult(" ", 50.0, "PLN",
                List.of(new SupplierQuote(EAN, MFN, 1, 50.0, "PLN"))));

        // when
        service.completeAwaitingPurchase(request(delivery), 1);

        // then
        assertFalse(delivery.isAwaitingSupplierConfirmation());
        assertEquals(DeliveryOrderStatus.ORDER_DISPATCHED, delivery.getOrderStatus());
        assertEquals("Supplier confirmed the order without an order number - check the supplier panel before "
                + "ordering again", delivery.getOrderErrorMessage());
        verify(deliveriesRepository).save(delivery);
        verify(deliveryCreationService, never()).completePending(any(), any(), any(), any());
    }

    private Delivery awaitingDelivery() {
        Delivery delivery = new Delivery(STORE_ID, null, PROVIDER);
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.setPurchaseRef("ref-1");
        delivery.setAwaitingSupplierConfirmation(true);
        delivery.setExternalDeliveryId("ZA/1");
        delivery.setExternalDeliveryIdProvisional(true);
        lenient().when(deliveriesRepository.findById(STORE_ID, DELIVERY_ID)).thenReturn(delivery);
        lenient().when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID))
                .thenReturn(deliveryWithAllocations());
        lenient().when(supplierProvider.checkAvailability(any())).thenReturn(List.of(
                new SupplierQuote(EAN, MFN, 10, 50.0, "PLN")));
        return delivery;
    }

    private Delivery deliveryWithAllocations() {
        Allocation allocation = new Allocation();
        allocation.setKey(new AllocationKey(null, UUID.randomUUID().toString(), "Warehouse"));
        allocation.setType(AllocationType.Warehouse);
        allocation.setName("Item");
        allocation.setEan(EAN);
        allocation.setMfn(MFN);
        allocation.setUnitCost(50.0);
        allocation.setQty(1);
        Delivery withAllocations = new Delivery();
        withAllocations.setDeliveryId(DELIVERY_ID);
        withAllocations.setProvider(PROVIDER);
        withAllocations.setItems(DeliveryItem.groupAndUnify(List.of(allocation)));
        return withAllocations;
    }

    private static SupplierPurchaseCompletionEventRequest request(Delivery delivery) {
        return new SupplierPurchaseCompletionEventRequest(STORE_ID, delivery.getDeliveryId(),
                delivery.getPurchaseRef(), null);
    }

    private static boolean hasEvent(Delivery delivery, String name) {
        return delivery.hasEvent(name);
    }
}
