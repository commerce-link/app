package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCancellationSettlerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ID = "21353832";
    private static final String COMMAND_ID = "cmd-1";
    private static final ShipmentCancellationCheckRequest REQUEST =
            ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, COMMAND_ID);

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private AwaitingPickupIndex awaitingPickupIndex;

    private ShipmentCancellationSettler settler;

    @BeforeEach
    void setUp() {
        // the retrying answer surfaces any exception thrown inside a mutator, as the real executor would
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        // the step back is a rule of the settlement, so it runs for real over the mocked events
        settler = new ShipmentCancellationSettler(ordersRepository, orderEventsRepository, optimisticLockingExecutor,
                new OrderRealizationStepBack(orderEventsRepository), awaitingPickupIndex);
    }

    private static Shipment pendingShipment(String commandId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRK-1");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
        shipment.setExternalId(EXTERNAL_ID);
        shipment.setCancellation(CourierCancellation.pending(commandId, LocalDateTime.now()));
        return shipment;
    }

    private static Order orderWith(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    @Test
    void succeedReplacesTheShipmentsAndDeletesTheShippingNotification() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.succeed(REQUEST).cleared();

        // then
        assertThat(saved).isTrue();
        verify(ordersRepository).save(order);
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).getType()).isEqualTo(ShipmentType.Courier);
        assertThat(order.getShipments().get(0).getExternalId()).isNull();
        assertThat(order.getShipments().get(0).getTrackingNo()).isNull();
        assertThat(order.getShipments().get(0).getCancellation()).isNull();
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    @Test
    void aCancelledPackageNoLongerWaitsForAPickup() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(pendingShipment(COMMAND_ID)));

        // when
        settler.succeed(REQUEST);

        // then
        verify(awaitingPickupIndex).remove(STORE_ID, List.of(EXTERNAL_ID));
    }

    @Test
    void aPickupIndexThatCannotBeUpdatedDoesNotUndoTheCancellation() {
        // given: the entry heals itself when the pickup page reads it
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(pendingShipment(COMMAND_ID)));
        doThrow(new RuntimeException("DynamoDB down")).when(awaitingPickupIndex).remove(any(), any());

        // when
        boolean cleared = settler.succeed(REQUEST).cleared();

        // then
        assertThat(cleared).isTrue();
    }

    @Test
    void aStaleCancellationLeavesThePickupIndexAlone() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(orderWith(pendingShipment("cmd-2")));

        // when
        settler.succeed(REQUEST);

        // then
        verifyNoInteractions(awaitingPickupIndex);
    }

    @Test
    void failKeepsTheShipmentAndLogsTheReason() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved;
        List<String> warnings;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentCancellationSettler.class)) {
            saved = settler.fail(REQUEST, "Przesyłka została już odebrana");
            warnings = logs.warnings();
        }

        // then: the reason is not stored on the shipment, the log keeps it with what identifies the command
        assertThat(saved).isTrue();
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(shipment.getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(shipment.getCancellation().hasCommand(COMMAND_ID)).isTrue();
        assertThat(warnings).singleElement().satisfies(message -> assertThat(message)
                .contains("store=" + STORE_ID, "order=" + ORDER_ID, "externalId=" + EXTERNAL_ID,
                        "commandId=" + COMMAND_ID, "Przesyłka została już odebrana"));
        verify(ordersRepository).save(order);
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void failOfAShipmentNoLongerWaitingLogsNothing() {
        // given
        Order order = orderWith(pendingShipment("cmd-2"));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        List<String> warnings;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentCancellationSettler.class)) {
            settler.fail(REQUEST, "reason");
            warnings = logs.warnings();
        }

        // then
        assertThat(warnings).isEmpty();
    }

    @Test
    void unconfirmedMarksTheShipmentUnconfirmed() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.unconfirmed(REQUEST);

        // then
        assertThat(saved).isTrue();
        assertThat(order.getShipments().get(0).getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
        verify(ordersRepository).save(order);
    }

    @Test
    void aShipmentWaitingForANewerCommandIsLeftAlone() {
        // given
        Order order = orderWith(pendingShipment("cmd-2"));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean succeeded = settler.succeed(REQUEST).cleared();
        boolean failed = settler.fail(REQUEST, "reason");
        boolean unconfirmed = settler.unconfirmed(REQUEST);

        // then
        assertThat(succeeded).isFalse();
        assertThat(failed).isFalse();
        assertThat(unconfirmed).isFalse();
        assertThat(order.getShipments().get(0).getCancellation().isPending()).isTrue();
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void aShipmentNoLongerPendingIsLeftAlone() {
        // given: the same command already settled as FAILED
        Shipment shipment = pendingShipment(COMMAND_ID);
        shipment.setCancellation(shipment.getCancellation().failed());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.succeed(REQUEST).cleared();

        // then
        assertThat(saved).isFalse();
        assertThat(order.getShipments().get(0).getExternalId()).isEqualTo(EXTERNAL_ID);
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void aReplacedShipmentIsLeftAlone() {
        // given: the package is no longer on the order
        Order order = orderWith(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.succeed(REQUEST).cleared();

        // then
        assertThat(saved).isFalse();
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void aDeletedOrderIsLeftAloneWithoutAnException() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        boolean succeeded = settler.succeed(REQUEST).cleared();
        boolean failed = settler.fail(REQUEST, "reason");
        boolean unconfirmed = settler.unconfirmed(REQUEST);

        // then
        assertThat(succeeded).isFalse();
        assertThat(failed).isFalse();
        assertThat(unconfirmed).isFalse();
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void succeedAppliesToTheFreshOrderAfterAVersionConflict() {
        // given
        Order firstAttempt = orderWith(pendingShipment(COMMAND_ID));
        Order secondAttempt = orderWith(pendingShipment(COMMAND_ID));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(firstAttempt, secondAttempt);
        doThrow(new ConditionalCheckFailedException("version conflict")).doNothing().when(ordersRepository).save(any());

        // when
        boolean saved = settler.succeed(REQUEST).cleared();

        // then
        assertThat(saved).isTrue();
        verify(ordersRepository).save(firstAttempt);
        verify(ordersRepository).save(secondAttempt);
        assertThat(secondAttempt.getShipments().get(0).getExternalId()).isNull();
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    @Test
    void theNextAttemptStartsCleanWhenTheShipmentDisappearsAfterAConflict() {
        // given: the first attempt finds the shipment but conflicts, the second no longer finds it
        Order firstAttempt = orderWith(pendingShipment(COMMAND_ID));
        Order secondAttempt = orderWith(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(firstAttempt, secondAttempt);
        doThrow(new ConditionalCheckFailedException("version conflict")).when(ordersRepository).save(firstAttempt);

        // when
        boolean saved = settler.succeed(REQUEST).cleared();

        // then
        assertThat(saved).isFalse();
        verify(ordersRepository, never()).save(secondAttempt);
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void succeedOfTheOnlyShippedCourierOrderOfAShippingOrderStepsBackToRealizationWithoutAnEmail() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        order.setStatus(OrderStatus.Shipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ShipmentCancellationSettler.Success success = settler.succeed(REQUEST);

        // then: the step back is recorded before the save, so the notifications skip "Zamówienie w realizacji"
        assertThat(success).isEqualTo(new ShipmentCancellationSettler.Success(true, true));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Realization);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        InOrder inOrder = inOrder(orderEventsRepository, ordersRepository);
        inOrder.verify(orderEventsRepository).save(event.capture());
        inOrder.verify(ordersRepository).save(order);
        assertThat(event.getValue().getType()).isEqualTo(EventType.action);
        assertThat(event.getValue().getName()).isEqualTo(OrderRealizationStepBack.EVENT);
    }

    @Test
    void succeedBeforeShippingKeepsTheStatus() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        order.setStatus(OrderStatus.Assembled);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ShipmentCancellationSettler.Success success = settler.succeed(REQUEST);

        // then
        assertThat(success).isEqualTo(new ShipmentCancellationSettler.Success(true, false));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Assembled);
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void succeedKeepsTheOtherShipmentsAndTheStatusWhileOneOfThemIsShipped() {
        // given: the courier order first, a parcel typed by hand that really went out after it
        Shipment booked = pendingShipment(COMMAND_ID);
        Shipment typed = new Shipment(ShipmentType.Courier);
        typed.setCarrier("DPD");
        typed.setTrackingNo("TRK-TYPED");
        typed.setShippedAt(LocalDateTime.of(2026, 9, 30, 10, 0));
        Order order = orderWith(booked, typed);
        order.setStatus(OrderStatus.Shipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ShipmentCancellationSettler.Success success = settler.succeed(REQUEST);

        // then: the typed parcel is with the carrier, so the order stays Shipping and the customer's e-mail stands
        assertThat(success).isEqualTo(new ShipmentCancellationSettler.Success(true, false));
        assertThat(order.getShipments()).containsExactly(typed);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Shipping);
        verify(ordersRepository).save(order);
        verify(orderEventsRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void succeedRemovesEveryParcelOfTheCourierOrderAndStepsBackWhenWhatIsLeftHasNotGoneOut() {
        // given: two parcels of one courier order (only the first carries the command) and a shipment still waiting
        Shipment parcel = pendingShipment(COMMAND_ID);
        Shipment secondParcel = new Shipment(ShipmentType.Courier);
        secondParcel.setCarrier("DPD");
        secondParcel.setTrackingNo("TRK-2");
        secondParcel.setShippedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
        secondParcel.setExternalId(EXTERNAL_ID);
        Shipment waiting = new Shipment(ShipmentType.Courier);
        waiting.setTrackingNo("TRK-WAITING");
        Order order = orderWith(parcel, secondParcel, waiting);
        order.setStatus(OrderStatus.Shipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        ShipmentCancellationSettler.Success success = settler.succeed(REQUEST);

        // then
        assertThat(success).isEqualTo(new ShipmentCancellationSettler.Success(true, true));
        assertThat(order.getShipments()).containsExactly(waiting);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Realization);
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    @Test
    void succeedLeavesABareShipmentWithTheCustomersDeliveryChoiceWhenNothingElseRemains() {
        // given: a pickup-point courier order
        Shipment booked = pendingShipment(COMMAND_ID);
        booked.setType(ShipmentType.PickupPoint);
        booked.setCollectionPointCode("WAW01A");
        Order order = orderWith(booked);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        settler.succeed(REQUEST);

        // then
        assertThat(order.getShipments()).hasSize(1);
        Shipment bare = order.getShipments().get(0);
        assertThat(bare.getType()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(bare.getCollectionPointCode()).isEqualTo("WAW01A");
        assertThat(bare.getCarrier()).isEqualTo("DPD");
        assertThat(bare.getExternalId()).isNull();
        assertThat(bare.getTrackingNo()).isNull();
    }

    @Test
    void succeedRecordsTheStepBackOnceWhenTheSaveConflicts() {
        // given: the first attempt steps back and conflicts, the second steps back again on the fresh order
        Order firstAttempt = orderWith(pendingShipment(COMMAND_ID));
        firstAttempt.setStatus(OrderStatus.Shipping);
        Order secondAttempt = orderWith(pendingShipment(COMMAND_ID));
        secondAttempt.setStatus(OrderStatus.Shipping);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(firstAttempt, secondAttempt);
        doThrow(new ConditionalCheckFailedException("version conflict")).when(ordersRepository).save(firstAttempt);

        // when
        ShipmentCancellationSettler.Success success = settler.succeed(REQUEST);

        // then
        assertThat(success).isEqualTo(new ShipmentCancellationSettler.Success(true, true));
        assertThat(secondAttempt.getStatus()).isEqualTo(OrderStatus.Realization);
        verify(ordersRepository).save(secondAttempt);
        verify(orderEventsRepository, times(1)).save(any());
    }
}
