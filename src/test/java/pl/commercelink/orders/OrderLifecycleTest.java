package pl.commercelink.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.receipts.ReceiptAttempt;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.receipts.ReceiptAttemptState;
import pl.commercelink.receipts.ReceiptOrderState;
import pl.commercelink.receipts.ReceiptTrigger;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderLifecycleTest {

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock private OrderNotificationsEventPublisher notificationEventPublisher;
    @Mock private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock private GoodsOutEventPublisher goodsOutEventPublisher;
    @Mock private DropshipItemLookup dropshipItemLookup;
    @Mock private ReceiptTrigger receiptTrigger;
    @Mock private ReceiptAttemptService receiptAttemptService;

    @InjectMocks
    private OrderLifecycle orderLifecycle;

    @BeforeEach
    void emptyRouteByDefault() {
        // Promotion to Assembly always asks for the route now; tests that are not about dates say so by
        // leaving this empty answer in place rather than by never being asked.
        when(dropshipItemLookup.routeOf(any(), any()))
                .thenReturn(new DropshipItemLookup.GoodsRoute(List.of(), false));
    }

    @Test
    void publishesOrderAcceptedWhenNewOrderMovesToAssembly() {
        // given
        Order order = new Order("store-1");
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Assembly, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderAccepted);
    }

    @Test
    void updateKeepsAnExistingAssemblyDateOnAnAssemblyOrder() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setOrderRealizationDays(1);
        order.updateEstimatedAssemblyAt(LocalDate.of(2026, 9, 11), false);
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertEquals(OrderStatus.Assembly, order.getStatus());
    }

    @Test
    void orderIsPersistedBeforeLifecycleEventsArePublished() {
        // given
        Order order = new Order("store-1");
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        InOrder inOrder = inOrder(ordersRepository, orderLifecycleEventPublisher);
        inOrder.verify(ordersRepository).save(order);
        inOrder.verify(orderLifecycleEventPublisher).publish(eq(order), any());
    }

    @Test
    void publishesNoEventWhenStatusDoesNotChange() {
        // given
        Order order = new Order("store-1");
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void publishesOrderCancelledWhenAllItemsReturnedAfterDelivery() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(false).when(order).isSettled(anyBoolean());
        doReturn(mock(OrderReview.class)).when(order).getReview();
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Cancelled, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
        verifyNoMoreInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void allItemsReturnedDoesNotCancelWhileTheEReceiptIsBeingIssued() {
        // given: every item came back through RMA while the e-receipt is still being issued
        Order order = fullyReturnedDeliveredOrder();
        withAttempt(order, ReceiptAttemptState.ISSUING);
        OrderItem item = returnedItem();

        // when: e.g. the refund is recorded with "Dodaj wpłatę"
        orderLifecycle.update(order, List.of(item));

        // then: a cancel now could leave a fiscalised receipt on a cancelled order nobody was warned about
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Delivered);
        verify(ordersRepository).save(order);
        verify(orderLifecycleEventPublisher, never()).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    void allItemsReturnedDoesNotCancelWhileAFiscalisedReceiptIsNotYetAttached() {
        // given: registered in fiscal memory, its document not on the order yet
        Order order = fullyReturnedDeliveredOrder();
        withAttempt(order, ReceiptAttemptState.FISCALISED);
        OrderItem item = returnedItem();

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Delivered);
        verify(orderLifecycleEventPublisher, never()).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    void allItemsReturnedCancelsOnceTheFiscalisedReceiptIsAttached() {
        // given: the receipt's document is on the order (the save that attaches it goes through here)
        Order order = fullyReturnedDeliveredOrder();
        ReceiptAttempt attempt = withAttempt(order, ReceiptAttemptState.FISCALISED);
        order.addDocument(new Document(attempt.getReceiptKey(), "PAR/1/2026", null, DocumentType.Receipt,
                LocalDate.of(2026, 9, 29)));
        OrderItem item = returnedItem();

        // when
        orderLifecycle.update(order, List.of(item));

        // then: cancelling after fiscalisation is allowed (owner decision); the receipt stays as issued
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Cancelled);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    void aHeldBackCancelGoesThroughOnTheNextSaveOnceTheAttemptDied() {
        // given: cancelling was held back while the e-receipt was being issued
        Order order = fullyReturnedDeliveredOrder();
        ReceiptAttempt attempt = withAttempt(order, ReceiptAttemptState.ISSUING);
        OrderItem item = returnedItem();
        orderLifecycle.update(order, List.of(item));
        attempt.setState(ReceiptAttemptState.FAILED);

        // when: the next save (the processor saves no order; the lifecycle cron's pass at the latest)
        orderLifecycle.update(order, List.of(item));

        // then: a dead attempt fiscalised nothing, so nothing holds the cancel any more
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Cancelled);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    private static Order fullyReturnedDeliveredOrder() {
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(false).when(order).isSettled(anyBoolean());
        return order;
    }

    private static OrderItem returnedItem() {
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(true);
        return item;
    }

    /** The order's only attempt, read through the real lock rule (ReceiptOrderState#locksOrder). */
    private ReceiptAttempt withAttempt(Order order, ReceiptAttemptState state) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setReceiptKey(order.getOrderId() + ":R1");
        attempt.setState(state);
        when(receiptAttemptService.locksOrder(order)).thenAnswer(i -> ReceiptOrderState.locksOrder(
                ReceiptAttemptService.blocksManualReceipt(List.of(attempt)), order));
        return attempt;
    }

    @Test
    void aDeliveredOrderWithoutItemsIsNotCancelled() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(false).when(order).isSettled(anyBoolean());

        // when
        orderLifecycle.update(order, List.of());

        // then
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.Cancelled);
        verify(orderLifecycleEventPublisher, never()).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    void anOrderWithoutAReviewIsCancelledAfterAFullReturnWithoutFailing() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        order.setReview(null);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(false).when(order).isSettled(anyBoolean());
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Cancelled);
    }

    @Test
    void publishesOrderCompletedWhenDeliveredOrderIsSettled() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(true).when(order).isSettled(anyBoolean());
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCompleted);
        verifyNoMoreInteractions(orderLifecycleEventPublisher);
        InOrder inOrder = inOrder(ordersRepository, receiptTrigger);
        inOrder.verify(ordersRepository).save(order);
        inOrder.verify(receiptTrigger).onOrderSaved(eq(order), any());
    }

    @Test
    void publishesBothOrderAcceptedAndOrderCompletedWhenNewOrderIsSettled() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.New);
        doReturn(true).when(order).isSettled(anyBoolean());
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(false);
        when(item.isReturned()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderAccepted);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCompleted);
        verifyNoMoreInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void publishesOrderAcceptedAndCompletedWhenBlockedOrderBecomesSettled() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Blocked);
        order.addDocument(new Document("doc-1", "FV/1/2026", "https://example.com/fv/1", DocumentType.InvoiceVat));
        order.addShipment(deliveredShipment());

        // when
        orderLifecycle.update(order);

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderAccepted);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCompleted);
        verifyNoMoreInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void publishesNoEventWhenStatusChangesBetweenIntermediateStates() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Assembled, order.getStatus());
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void publishesOrderAcceptedAndCompletedWhenGenuinelySettledOrderReachesCompleted() {
        // given
        Order order = new Order("store-1");
        order.addDocument(new Document("doc-1", "FV/1/2026", "https://example.com/fv/1", DocumentType.InvoiceVat));
        order.addShipment(deliveredShipment());
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderAccepted);
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCompleted);
        verifyNoMoreInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void aDropshipOnlyOrderDoesNotWaitForAWarehouseDocument() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        OrderItem item = mock(OrderItem.class);
        when(item.isProduct()).thenReturn(true);
        when(item.getItemId()).thenReturn("item-1");
        when(item.isReturned()).thenReturn(false);

        Store store = mock(Store.class);
        when(store.hasDocumentsGenerationEnabled()).thenReturn(true);
        when(store.getStoreId()).thenReturn("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(eq("store-1"), any())).thenReturn(Set.of("item-1"));

        doReturn(true).when(order).isDelivered();
        doReturn(true).when(order).isFullyPaid();
        doReturn(true).when(order).isInvoiced();
        doReturn(false).when(order).isAwaitingReview();
        doReturn(false).when(order).isAwaitingInvoiceGeneration();

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verifyNoInteractions(goodsOutEventPublisher);
    }

    @Test
    void aMixedOrderStillWaitsForAWarehouseDocument() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        OrderItem dropshipItem = mock(OrderItem.class);
        when(dropshipItem.isProduct()).thenReturn(true);
        when(dropshipItem.getItemId()).thenReturn("item-1");
        when(dropshipItem.isReturned()).thenReturn(false);
        OrderItem warehouseItem = mock(OrderItem.class);
        when(warehouseItem.isProduct()).thenReturn(true);
        when(warehouseItem.getItemId()).thenReturn("item-2");
        when(warehouseItem.isReturned()).thenReturn(false);

        Store store = mock(Store.class);
        when(store.hasDocumentsGenerationEnabled()).thenReturn(true);
        when(store.getStoreId()).thenReturn("store-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(dropshipItemLookup.itemIdsInDropshipDeliveries(eq("store-1"), any())).thenReturn(Set.of("item-1"));

        doReturn(true).when(order).isDelivered();
        doReturn(true).when(order).isFullyPaid();
        doReturn(true).when(order).isInvoiced();
        doReturn(false).when(order).isAwaitingReview();
        doReturn(false).when(order).isAwaitingInvoiceGeneration();

        // when
        orderLifecycle.update(order, List.of(dropshipItem, warehouseItem));

        // then
        assertEquals(OrderStatus.Delivered, order.getStatus());
        verify(goodsOutEventPublisher).publish(eq(order), any());
    }

    @Test
    @DisplayName("an order with no date takes the dropship rule when every leg travels by dropship")
    void fallbackFollowsADropshipOnlyOrder() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(false);

        Delivery dropshipDelivery = mock(Delivery.class);
        when(dropshipDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        when(dropshipItemLookup.routeOf(eq("store-1"), any()))
                .thenReturn(new DropshipItemLookup.GoodsRoute(List.of(dropshipDelivery), true));

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("an order split between a warehouse and a dropship delivery keeps its realization days")
    void fallbackKeepsRealizationDaysForAMixedOrder() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        OrderItem dropshipItem = mock(OrderItem.class);
        when(dropshipItem.isOrdered()).thenReturn(true);
        when(dropshipItem.isDelivered()).thenReturn(false);
        OrderItem warehouseItem = mock(OrderItem.class);
        when(warehouseItem.isOrdered()).thenReturn(true);
        when(warehouseItem.isDelivered()).thenReturn(false);

        Delivery dropshipDelivery = mock(Delivery.class);
        when(dropshipDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        Delivery warehouseDelivery = mock(Delivery.class);
        when(warehouseDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        when(dropshipItemLookup.routeOf(eq("store-1"), any()))
                .thenReturn(new DropshipItemLookup.GoodsRoute(List.of(dropshipDelivery, warehouseDelivery), false));

        // when
        orderLifecycle.update(order, List.of(dropshipItem, warehouseItem));

        // then
        assertThat(order.getEstimatedShippingAt()).isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    @DisplayName("an order that already has dates still has its shipping date re-derived when it is promoted")
    void promotionReDerivesTheShippingDateOfAnOrderThatAlreadyHasOne() {
        // given: an earlier, dropship-only leg already stamped the order, so both dates are the same day.
        // The warehouse leg that completes the order arrives through fulfilment, not through a supplier
        // confirmation, so this promotion is the only place left that can put the handling time back.
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.New);
        order.setOrderRealizationDays(3);
        order.setEstimatedAssemblyAt(LocalDate.of(2026, 9, 14));
        order.setEstimatedShippingAt(LocalDate.of(2026, 9, 14));
        OrderItem dropshipItem = mock(OrderItem.class);
        when(dropshipItem.isOrdered()).thenReturn(true);
        when(dropshipItem.isDelivered()).thenReturn(false);
        OrderItem warehouseItem = mock(OrderItem.class);
        when(warehouseItem.isOrdered()).thenReturn(true);
        when(warehouseItem.isDelivered()).thenReturn(false);

        Delivery dropshipDelivery = mock(Delivery.class);
        when(dropshipDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        Delivery warehouseDelivery = mock(Delivery.class);
        when(warehouseDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        when(dropshipItemLookup.routeOf(eq("store-1"), any()))
                .thenReturn(new DropshipItemLookup.GoodsRoute(List.of(dropshipDelivery, warehouseDelivery), false));

        // when
        orderLifecycle.update(order, List.of(dropshipItem, warehouseItem));

        // then
        assertEquals(OrderStatus.Assembly, order.getStatus());
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    @DisplayName("an all-dropship order that is fully delivered gains no realization days")
    void assembledDropshipOnlyOrderShipsOnTheAssemblyDay() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(true);
        when(dropshipItemLookup.isEntirelyDropship(eq("store-1"), any())).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Assembled, order.getStatus());
        assertThat(order.getEstimatedShippingAt()).isEqualTo(order.getEstimatedAssemblyAt());
    }

    @Test
    @DisplayName("an order with a warehouse leg still adds the realization days once fully delivered")
    void assembledOrderWithAWarehouseLegKeepsItsRealizationDays() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Assembly);
        order.setOrderRealizationDays(3);
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(true);
        when(dropshipItemLookup.isEntirelyDropship(eq("store-1"), any())).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Assembled, order.getStatus());
        assertThat(order.getEstimatedShippingAt()).isAfter(order.getEstimatedAssemblyAt());
    }

    @Test
    void doesNotConsultDropshipLookupWhenOrderIsNotYetDelivered() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Assembly);
        order.setShipments(new ArrayList<>(List.of(new Shipment(ShipmentType.Courier))));
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(false);
        when(item.isDelivered()).thenReturn(false);

        Store store = mock(Store.class);
        when(store.hasDocumentsGenerationEnabled()).thenReturn(true);
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        verifyNoInteractions(dropshipItemLookup);
    }

    @Test
    void aShippingOrderWhoseOnlyShipmentWasRemovedStaysShipping() {
        // given: allMatch on no shipments is true, which delivered (and could complete) the order
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Shipping);
        order.setShipments(new ArrayList<>());
        order.addDocument(new Document("doc-1", "FV/1/2026", "https://example.com/fv/1", DocumentType.InvoiceVat));

        // when
        orderLifecycle.update(order, List.of());

        // then
        assertEquals(OrderStatus.Shipping, order.getStatus());
        verifyNoInteractions(orderLifecycleEventPublisher, goodsOutEventPublisher);
    }

    @Test
    void aShippingOrderDoesNotStampAPersonalCollectionWithoutItsDateAsReady() {
        // given: K8 — the operator has just emptied the collection (removed shipment's placeholder, cleared date)
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Shipping);
        Shipment parcel = new Shipment(ShipmentType.Courier);
        parcel.setCarrier("InPost");
        parcel.setTrackingNo("T-1");
        parcel.setShippedAt(LocalDateTime.of(2026, 9, 27, 9, 0));
        Shipment collection = new Shipment(ShipmentType.PersonalCollection);
        order.setShipments(new ArrayList<>(List.of(parcel, collection)));

        // when
        orderLifecycle.update(order, List.of());

        // then
        assertEquals(OrderStatus.Shipping, order.getStatus());
        assertThat(collection.getShippedAt()).isNull();
    }

    @Test
    void aReadyCollectionMovesARealizationOrderToShippingAsBefore() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Realization);
        Shipment collection = new Shipment(ShipmentType.PersonalCollection);
        collection.setShippedAt(LocalDateTime.of(2026, 9, 30, 11, 40));
        order.setShipments(new ArrayList<>(List.of(collection)));

        // when
        orderLifecycle.update(order, List.of());

        // then
        assertEquals(OrderStatus.Shipping, order.getStatus());
        assertThat(collection.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 11, 40));
    }

    @Test
    void aShippingOrderIsDeliveredOnceEveryShipmentHasADeliveryDate() {
        // given
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Shipping);
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 9, 0));
        shipment.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 0, 0));
        order.setShipments(new ArrayList<>(List.of(shipment)));
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Delivered, order.getStatus());
    }

    @Test
    void removingTheOnlyShipmentOfAPaidInvoicedOrderInRealizationDoesNotCompleteIt() {
        // given: paid in full, invoiced, no review to collect; its only shipment (still waiting to go out) was removed
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Realization);
        order.setTotalPrice(100);
        order.addPayment(new Payment("ref-1", "Wpłata", PaymentSource.BankTransfer, 100, 0));
        order.addDocument(new Document("doc-1", "FV/1/2026", "https://example.com/fv/1", DocumentType.InvoiceVat));
        order.setShipments(new ArrayList<>());

        // when
        orderLifecycle.update(order, List.of());

        // then: it never shipped, so it is neither delivered nor completed, and the marketplace hears nothing
        assertEquals(OrderStatus.Realization, order.getStatus());
        verify(orderLifecycleEventPublisher, never()).publish(order, OrderLifecycleEventType.OrderCompleted);
        verifyNoInteractions(goodsOutEventPublisher);
    }

    @Test
    void editingAPaymentAfterTheOnlyShipmentWasRemovedDoesNotCompleteTheOrder() {
        // given: an invoiced Realization order left without shipments, paid only in part
        Order order = new Order("store-1");
        order.setStatus(OrderStatus.Realization);
        order.setTotalPrice(100);
        Payment payment = new Payment("ref-1", "Wpłata", PaymentSource.BankTransfer, 40, 0);
        order.addPayment(payment);
        order.addDocument(new Document("doc-1", "FV/1/2026", "https://example.com/fv/1", DocumentType.InvoiceVat));
        order.setShipments(new ArrayList<>());
        orderLifecycle.update(order, List.of());

        // when: the payment is corrected to the full amount, which is saved through the lifecycle
        payment.setAmount(100);
        orderLifecycle.update(order, List.of());

        // then
        assertEquals(OrderStatus.Realization, order.getStatus());
        verify(orderLifecycleEventPublisher, never()).publish(order, OrderLifecycleEventType.OrderCompleted);
    }

    /** Shipped and delivered: without shipments an order waits before Delivered instead of settling. */
    private static Shipment deliveredShipment() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("T-1");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 9, 0));
        shipment.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 10, 0));
        return shipment;
    }
}
