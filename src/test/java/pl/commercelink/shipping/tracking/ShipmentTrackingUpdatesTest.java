package pl.commercelink.shipping.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentTrackingUpdatesTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 9, 2, 13, 30);

    @Mock private ShipmentTrackingsRepository shipmentTrackingsRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private RMARepository rmaRepository;
    @Mock private GoodsOutEventPublisher goodsOutEventPublisher;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;

    private ShipmentTrackingUpdates updates;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(optimisticLockingExecutor.modifyAndSaveReturning(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSaveReturning());
        when(shipmentTrackingsRepository.advance(any(), any())).thenReturn(true);
        updates = new ShipmentTrackingUpdates(shipmentTrackingsRepository, ordersRepository, orderLifecycle,
                rmaRepository, goodsOutEventPublisher, orderEventsRepository, optimisticLockingExecutor);
    }

    private static Shipment courier(String trackingNo) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(DELIVERED_AT.minusDays(2));
        return shipment;
    }

    private static Order order(OrderStatus status, Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        order.setStatus(status);
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    private ShipmentTracking indexedForOrder(String trackingNo) {
        ShipmentTracking row = new ShipmentTracking(STORE_ID, trackingNo, "order-1", null, DELIVERED_AT);
        when(shipmentTrackingsRepository.find(STORE_ID, trackingNo)).thenReturn(Optional.of(row));
        return row;
    }

    private boolean apply(String trackingNo, ShipmentTrackingState state) {
        return updates.apply(STORE_ID, trackingNo, state, DELIVERED_AT);
    }

    @Test
    void deliveredFillsDeliveredAtOnOrderFoundThroughIndexEvenWhenOrderIsStillInAssembly() {
        // given
        Order order = order(OrderStatus.Assembly, courier("PKG-1"), courier("PKG-2"));
        indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        boolean applied = apply("PKG-1", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(applied).isTrue();
        assertThat(order.getShipments().get(0).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        assertThat(order.getShipments().get(1).getDeliveredAt()).isNull();
        verify(orderLifecycle).update(order);
        verify(orderEventsRepository).save(any());
    }

    @Test
    void deliveredMatchesTheShipmentRegardlessOfTrackingNumberCase() {
        // given: operator typed the number in lower case, the carrier echoes it upper-cased
        Order order = order(OrderStatus.Shipping, courier("acmebtrkd89eaa836bc7"));
        indexedForOrder("ACMEBTRKD89EAA836BC7");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        apply("ACMEBTRKD89EAA836BC7", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(order.getShipments().get(0).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        verify(orderEventsRepository).save(any());
    }

    @Test
    void unknownTrackingNoIsIgnored() {
        // given
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-X")).thenReturn(Optional.empty());

        // when
        boolean applied = apply("PKG-X", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(applied).isFalse();
        verifyNoInteractions(ordersRepository, rmaRepository, orderLifecycle);
    }

    @Test
    void collectedPublishesGoodsOutAndRecordsTheEvent() {
        // given
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);

        // when
        apply("PKG-1", ShipmentTrackingState.COLLECTED);

        // then
        verify(goodsOutEventPublisher).publish(order, "System");
        verify(orderEventsRepository).save(event.capture());
        assertThat(event.getValue().getName()).isEqualTo("SHIPMENT_COLLECTED");
        assertThat(event.getValue().getCreatedAt()).isEqualTo(DELIVERED_AT);
        verify(orderLifecycle, never()).update(order);
    }

    @Test
    void collectedWhileOrderIsStillInAssemblyIsNeitherAppliedNorRemembered() {
        // given
        Order order = order(OrderStatus.Assembly, courier("PKG-1"));
        indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        boolean applied = apply("PKG-1", ShipmentTrackingState.COLLECTED);

        // then: not written, so a later webhook or poll applies it once the order ships
        assertThat(applied).isFalse();
        verify(shipmentTrackingsRepository, never()).advance(any(), any());
        verify(goodsOutEventPublisher, never()).publish(any(), any());
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void repeatedCollectedIsAppliedOnce() {
        // given: the first COLLECTED has been written to the row
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        ShipmentTracking row = indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(shipmentTrackingsRepository.advance(eq(row), eq(ShipmentTrackingState.COLLECTED))).thenAnswer(invocation -> {
            row.setState("COLLECTED");
            return true;
        });

        // when: the webhook arrives and an hourly poll reports the same state
        boolean first = apply("PKG-1", ShipmentTrackingState.COLLECTED);
        boolean second = apply("PKG-1", ShipmentTrackingState.COLLECTED);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(goodsOutEventPublisher, times(1)).publish(order, "System");
        verify(orderEventsRepository, times(1)).save(any());
    }

    @Test
    void concurrentCollectedWritesOnce() {
        // given: two writers read the row before either wrote; the conditional write lets only one through
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(shipmentTrackingsRepository.advance(any(), eq(ShipmentTrackingState.COLLECTED))).thenReturn(true, false);

        // when
        boolean webhook = apply("PKG-1", ShipmentTrackingState.COLLECTED);
        boolean poll = apply("PKG-1", ShipmentTrackingState.COLLECTED);

        // then
        assertThat(webhook).isTrue();
        assertThat(poll).isFalse();
        verify(goodsOutEventPublisher, times(1)).publish(order, "System");
        verify(orderEventsRepository, times(1)).save(any());
    }

    @Test
    void deliveredAfterDeliveredIsIgnored() {
        // given
        ShipmentTracking row = indexedForOrder("PKG-1");
        row.setState("DELIVERED");

        // when
        boolean applied = apply("PKG-1", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(applied).isFalse();
        verifyNoInteractions(ordersRepository, orderLifecycle, orderEventsRepository);
    }

    @Test
    void expiredRecordsAnOrderEventWithoutTouchingTheOrder() {
        // given
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);

        // when
        boolean applied = apply("PKG-1", ShipmentTrackingState.EXPIRED);

        // then
        assertThat(applied).isTrue();
        verify(orderEventsRepository).save(event.capture());
        assertThat(event.getValue().getName()).isEqualTo(ShipmentTrackingUpdates.EVENT_EXPIRED);
        verify(orderLifecycle, never()).update(any());
        verify(goodsOutEventPublisher, never()).publish(any(), any());
    }

    @Test
    void deliveredForRmaMarksItemsReceivedWhenAllShipmentsDelivered() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("rma-1");
        rma.setStoreId(STORE_ID);
        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setShipments(new ArrayList<>(List.of(courier("RET-1"))));
        when(shipmentTrackingsRepository.find(STORE_ID, "RET-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "RET-1", null, "rma-1", DELIVERED_AT)));
        when(rmaRepository.findById(STORE_ID, "rma-1")).thenReturn(rma);

        // when
        apply("RET-1", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(rma.getShipments().get(0).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.ItemsReceived);
        verify(rmaRepository).save(rma);
    }

    @Test
    void deliveredForRmaDoesNotRegressStatusWhenRmaIsNotWaitingForItems() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("rma-1");
        rma.setStoreId(STORE_ID);
        rma.setStatus(RMAStatus.Processing);
        rma.setShipments(new ArrayList<>(List.of(courier("RET-1"))));
        when(shipmentTrackingsRepository.find(STORE_ID, "RET-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "RET-1", null, "rma-1", DELIVERED_AT)));
        when(rmaRepository.findById(STORE_ID, "rma-1")).thenReturn(rma);

        // when
        boolean applied = apply("RET-1", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(applied).isFalse();
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Processing);
        assertThat(rma.getShipments().get(0).getDeliveredAt()).isNull();
        verify(rmaRepository, never()).save(any());
        verify(shipmentTrackingsRepository, never()).advance(any(), any());
    }

    @Test
    void deliveredForStaleIndexRowRecordsNoEvent() {
        // given: the tracking number was edited after the index row was written
        Order order = order(OrderStatus.Shipping, courier("PKG-NEW"));
        indexedForOrder("PKG-OLD");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        boolean applied = apply("PKG-OLD", ShipmentTrackingState.DELIVERED);

        // then
        assertThat(applied).isFalse();
        assertThat(order.getShipments().get(0).getDeliveredAt()).isNull();
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void failedEffectsRevertTheStateAndPropagateSoARetryAppliesThemOnce() {
        // given: the first attempt fails while publishing, the retry works
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        ShipmentTracking row = indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        when(shipmentTrackingsRepository.advance(eq(row), eq(ShipmentTrackingState.COLLECTED))).thenAnswer(invocation -> {
            row.setState("COLLECTED");
            return true;
        });
        when(shipmentTrackingsRepository.revert(eq(row), any())).thenAnswer(invocation -> {
            row.setState(invocation.getArgument(1));
            return true;
        });
        doThrow(new IllegalStateException("sqs down")).doNothing().when(goodsOutEventPublisher).publish(order, "System");

        // when
        assertThatThrownBy(() -> apply("PKG-1", ShipmentTrackingState.COLLECTED)).isInstanceOf(IllegalStateException.class);
        boolean retried = apply("PKG-1", ShipmentTrackingState.COLLECTED);

        // then
        assertThat(retried).isTrue();
        assertThat(row.getState()).isEqualTo("COLLECTED");
        verify(shipmentTrackingsRepository).revert(row, null);
        verify(goodsOutEventPublisher, times(2)).publish(order, "System");
    }

    @Test
    void failedEffectsStillPropagateWhenTheRevertFails() {
        // given
        Order order = order(OrderStatus.Shipping, courier("PKG-1"));
        ShipmentTracking row = indexedForOrder("PKG-1");
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        doThrow(new IllegalStateException("sqs down")).when(goodsOutEventPublisher).publish(order, "System");
        when(shipmentTrackingsRepository.revert(eq(row), any())).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> apply("PKG-1", ShipmentTrackingState.COLLECTED))
                .isInstanceOf(IllegalStateException.class).hasMessage("sqs down");
    }

    @Test
    void failedRmaEffectsRevertTheState() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("rma-1");
        rma.setStoreId(STORE_ID);
        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setShipments(new ArrayList<>(List.of(courier("RET-1"))));
        ShipmentTracking row = new ShipmentTracking(STORE_ID, "RET-1", null, "rma-1", DELIVERED_AT);
        when(shipmentTrackingsRepository.find(STORE_ID, "RET-1")).thenReturn(Optional.of(row));
        when(rmaRepository.findById(STORE_ID, "rma-1")).thenReturn(rma);
        doThrow(new IllegalStateException("version conflict")).when(rmaRepository).save(any());

        // when / then
        assertThatThrownBy(() -> apply("RET-1", ShipmentTrackingState.DELIVERED)).isInstanceOf(IllegalStateException.class);
        verify(shipmentTrackingsRepository).revert(row, null);
    }
}
