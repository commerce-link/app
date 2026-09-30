package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private ShipmentCancellationSettler settler;

    @BeforeEach
    void setUp() {
        // the retrying answer surfaces any exception thrown inside a mutator, as the real executor would
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        settler = new ShipmentCancellationSettler(ordersRepository, orderEventsRepository, optimisticLockingExecutor);
    }

    private static Shipment pendingShipment(String commandId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRK-1");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
        shipment.setExternalId(EXTERNAL_ID);
        shipment.markCancellationPending(commandId, LocalDateTime.now());
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
        boolean saved = settler.succeed(REQUEST);

        // then
        assertThat(saved).isTrue();
        verify(ordersRepository).save(order);
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).getType()).isEqualTo(ShipmentType.Courier);
        assertThat(order.getShipments().get(0).getExternalId()).isNull();
        assertThat(order.getShipments().get(0).getTrackingNo()).isNull();
        assertThat(order.getShipments().get(0).getCancellationStatus()).isNull();
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    @Test
    void failKeepsTheShipmentWithTheReason() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.fail(REQUEST, "Przesyłka została już odebrana");

        // then
        assertThat(saved).isTrue();
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(shipment.getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(shipment.getCancellationError()).isEqualTo("Przesyłka została już odebrana");
        verify(ordersRepository).save(order);
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
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
        assertThat(order.getShipments().get(0).getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
        verify(ordersRepository).save(order);
    }

    @Test
    void aShipmentWaitingForANewerCommandIsLeftAlone() {
        // given
        Order order = orderWith(pendingShipment("cmd-2"));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean succeeded = settler.succeed(REQUEST);
        boolean failed = settler.fail(REQUEST, "reason");
        boolean unconfirmed = settler.unconfirmed(REQUEST);

        // then
        assertThat(succeeded).isFalse();
        assertThat(failed).isFalse();
        assertThat(unconfirmed).isFalse();
        assertThat(order.getShipments().get(0).isCancellationPending()).isTrue();
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void aShipmentNoLongerPendingIsLeftAlone() {
        // given: the same command already settled as FAILED
        Shipment shipment = pendingShipment(COMMAND_ID);
        shipment.markCancellationFailed("earlier reason");
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        boolean saved = settler.succeed(REQUEST);

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
        boolean saved = settler.succeed(REQUEST);

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
        boolean succeeded = settler.succeed(REQUEST);
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
        boolean saved = settler.succeed(REQUEST);

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
        boolean saved = settler.succeed(REQUEST);

        // then
        assertThat(saved).isFalse();
        verify(ordersRepository, never()).save(secondAttempt);
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }
}
