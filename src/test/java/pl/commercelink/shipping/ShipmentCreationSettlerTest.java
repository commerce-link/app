package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.*;
import pl.commercelink.shipping.api.ShipmentResult;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCreationSettlerTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private OrderLifecycleEventPublisher lifecycleEventPublisher;
    @Mock private ShipmentOwners owners;
    @Mock private ImmediatePickup immediatePickup;
    @Mock private ShipmentOwner returnOwner;

    private ShipmentCreationSettler settler;
    private Order order;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        order = new Order("store-1");
        order.setOrderId("order-1");
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setProvider("furgonetka");
        placeholder.setPickUpAddressId("addr-1");
        placeholder.setExternalId("21480003");
        placeholder.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        order.setShipments(new ArrayList<>(List.of(placeholder)));
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);
        OrderShipmentOwner owner = new OrderShipmentOwner(ordersRepository, optimisticLockingExecutor,
                trackingSubscriber, orderLifecycle, lifecycleEventPublisher);
        when(owners.get(ShipmentOwnerType.ORDER)).thenReturn(owner);
        when(owners.get(ShipmentOwnerType.RMA_RETURN)).thenReturn(returnOwner);
        settler = new ShipmentCreationSettler(owners, immediatePickup);
    }

    private static ShipmentCreationCheckRequest request() {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.ORDER)
                .ownerId("order-1").commandId("cmd-1").externalId("21480003").provider("furgonetka").attempt(1).build();
    }

    private static ShipmentResult result() {
        return new ShipmentResult("21480003", List.of(new ShipmentResult.ShipmentParcelResult("A", "dpd", "https://t/A", true, null)), null);
    }

    @Test
    void aSuccessReplacesThePlaceholderAndAnnouncesTheShipment() {
        // when
        settler.succeeded(request(), result());

        // then
        assertThat(order.getShipments()).hasSize(1);
        Shipment created = order.getShipments().get(0);
        assertThat(created.getTrackingNo()).isEqualTo("A");
        assertThat(created.awaitsPickup()).isTrue();
        verifyNoInteractions(immediatePickup);
        verify(trackingSubscriber).subscribe(eq("store-1"), any(Order.class));
        verify(orderLifecycle).update(any(Order.class));
        verify(lifecycleEventPublisher).publish(any(Order.class), eq(OrderLifecycleEventType.ShipmentCreated));
    }

    @Test
    void secondDeliveryOfSameSuccessChangesNothing() {
        // given
        settler.succeeded(request(), result());
        clearInvocations(trackingSubscriber, orderLifecycle, lifecycleEventPublisher, ordersRepository);
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);

        // when
        settler.succeeded(request(), result());

        // then
        verify(ordersRepository, never()).save(any());
        verifyNoInteractions(trackingSubscriber, orderLifecycle, lifecycleEventPublisher);
    }

    @Test
    void aFailureKeepsTheReasonOnTheShipment() {
        // when
        settler.failed(request(), "Nieprawidłowy kod pocztowy");

        // then
        Shipment s = order.getShipments().get(0);
        assertThat(s.creationFailed()).isTrue();
        assertThat(s.getCreation().getCommand().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }

    @Test
    void aReturnOrdersItsPickupRightAway() {
        // given
        when(returnOwner.succeeded(any(), anyList())).thenReturn(true);
        ShipmentCreationCheckRequest request = request().toBuilder().ownerType(ShipmentOwnerType.RMA_RETURN).build();

        // when
        settler.succeeded(request, result());

        // then
        verify(immediatePickup).orderFor(eq(request), anyList());
    }

    @Test
    void anOrderLeavesItsPickupToTheOperator() {
        // when
        settler.succeeded(request(), result());

        // then
        verifyNoInteractions(immediatePickup);
    }

    @Test
    void aDroppedReturnResultOrdersNoPickup() {
        // given
        when(returnOwner.succeeded(any(), anyList())).thenReturn(false);

        // when
        settler.succeeded(request().toBuilder().ownerType(ShipmentOwnerType.RMA_RETURN).build(), result());

        // then
        verifyNoInteractions(immediatePickup);
    }

    @Test
    void anImmediatePickupThatBreaksOffIsAnErrorAndNotRetried() {
        // given
        when(returnOwner.succeeded(any(), anyList())).thenReturn(true);
        doThrow(new RuntimeException("dynamo down")).when(immediatePickup).orderFor(any(), anyList());

        // when
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentCreationSettler.class)) {
            settler.succeeded(request().toBuilder().ownerType(ShipmentOwnerType.RMA_RETURN).build(), result());
            errors = logs.errors();
        }

        // then
        assertThat(errors).singleElement().asString().contains("21480003", "RMA_RETURN", "ordering its pickup failed");
    }
}
