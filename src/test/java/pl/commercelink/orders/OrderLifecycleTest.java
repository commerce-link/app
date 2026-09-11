package pl.commercelink.orders;

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
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDate;
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

    @InjectMocks
    private OrderLifecycle orderLifecycle;

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
        order.updateEstimatedAssemblyAt(LocalDate.of(2026, 9, 11));
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
        when(item.getDeliveryId()).thenReturn("d1");

        Delivery dropshipDelivery = mock(Delivery.class);
        when(dropshipDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        when(dropshipItemLookup.deliveriesOf(eq("store-1"), any())).thenReturn(List.of(dropshipDelivery));
        when(dropshipItemLookup.isEntirelyDropship(List.of(dropshipDelivery))).thenReturn(true);

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
        when(dropshipItem.getDeliveryId()).thenReturn("d1");
        OrderItem warehouseItem = mock(OrderItem.class);
        when(warehouseItem.isOrdered()).thenReturn(true);
        when(warehouseItem.isDelivered()).thenReturn(false);
        when(warehouseItem.getDeliveryId()).thenReturn("d2");

        Delivery dropshipDelivery = mock(Delivery.class);
        when(dropshipDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        Delivery warehouseDelivery = mock(Delivery.class);
        when(warehouseDelivery.getEstimatedDeliveryAt()).thenReturn(LocalDate.of(2026, 9, 14));
        when(dropshipItemLookup.deliveriesOf(eq("store-1"), any()))
                .thenReturn(List.of(dropshipDelivery, warehouseDelivery));
        when(dropshipItemLookup.isEntirelyDropship(List.of(dropshipDelivery, warehouseDelivery))).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(dropshipItem, warehouseItem));

        // then
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
        doReturn(false).when(order).isDelivered();
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
}
