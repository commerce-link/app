package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaReturnShipmentOwnerTest {

    @Mock private RMARepository rmaRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private EmailClient emailClient;
    @Mock private StoreNotificationService notifications;
    @Mock private MessageSource messageSource;

    private RMA rma;
    private RmaReturnShipmentOwner owner;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(emailClient.send(any(), any(), any())).thenReturn(true);
        rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setOrderId("order-1");
        rma.setEmail("jan@example.com");
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setExternalId("21480003");
        shipment.setTrackingNo("A");
        shipment.setTrackingUrl("https://t/A");
        shipment.setPickup(ShipmentPickup.awaiting());
        rma.setShipments(new ArrayList<>(List.of(shipment)));
        when(rmaRepository.findById("store-1", "rma-1")).thenAnswer(i -> rma);
        owner = new RmaReturnShipmentOwner(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle,
                trackingSubscriber, emailClient, notifications, messageSource);
    }

    private static PickupTarget target() {
        return new PickupTarget(ShipmentOwnerType.RMA_RETURN, "rma-1", "21480003", "A");
    }

    private static ShipmentPickup pending(String commandId) {
        return ShipmentPickup.pending(commandId, LocalDateTime.now(), LocalDate.of(2026, 10, 6), LocalTime.of(14, 0),
                LocalTime.of(17, 0));
    }

    private static ShipmentCreationCheckRequest creation(String commandId) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.RMA_RETURN)
                .ownerId("rma-1").commandId(commandId).build();
    }

    @Test
    void returnEmailIsSentOnce() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), pending("pick-1").ordered("p-1"));
        owner.onPickupSettled("store-1", "furgonetka", target(), pending("pick-1").ordered("p-1"));

        // then
        verify(emailClient, times(1)).send(eq("store-1"), eq(EmailNotificationType.RMA_CARRIER_CONFIRMATION), any());
        assertThat(rma.getEvents()).filteredOn(e -> e.getType() == EventType.email
                && EmailNotificationType.RMA_CARRIER_CONFIRMATION.name().equals(e.getName())).hasSize(1);
    }

    @Test
    void theEventIsRecordedBeforeTheEmailIsSent() {
        // given
        when(emailClient.send(any(), any(), any())).thenAnswer(i -> {
            assertThat(rma.getEvents()).anyMatch(e -> EmailNotificationType.RMA_CARRIER_CONFIRMATION.name()
                    .equals(e.getName()));
            return true;
        });

        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.notRequired());

        // then
        verify(rmaRepository).save(rma);
        verify(emailClient).send(eq("store-1"), eq(EmailNotificationType.RMA_CARRIER_CONFIRMATION),
                argThat(msg -> msg.getRecipientEmail().equals("jan@example.com")));
    }

    @Test
    void anEmailRefusedBySendingIsNotRetried() {
        // given
        when(emailClient.send(any(), any(), any())).thenReturn(false);

        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.notRequired());
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.notRequired());

        // then
        verify(emailClient, times(1)).send(any(), any(), any());
    }

    @Test
    void aFailedPickupTellsTheStore() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), pending("pick-1").failed("Brak kuriera"));

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.RMA_RETURN_PICKUP_FAILED && "rma-1:pick-1".equals(n.getObject())
                        && n.getSeverity() == StoreNotificationSeverity.WARNING));
        verify(emailClient, never()).send(any(), any(), any());
    }

    @Test
    void aPickupThatFailedWithoutACommandIsTiedToItsPackage() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.awaiting().failed("Brak okien"));

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                "rma-1:21480003".equals(n.getObject())));
    }

    @Test
    void aFailedCreationTellsTheStore() {
        // given
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        rma.setShipments(new ArrayList<>(List.of(placeholder)));

        // when
        owner.failed(creation("cmd-1"), "Nieprawidłowy kod pocztowy", null);

        // then
        assertThat(rma.getShipments().get(0).creationFailed()).isTrue();
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.RMA_RETURN_SHIPMENT_FAILED && "rma-1:cmd-1".equals(n.getObject())));
        verify(messageSource).getMessage(eq("shipping.notification.return.failed"),
                argThat(args -> "rma-1".equals(args[0]) && "Nieprawidłowy kod pocztowy".equals(args[1])), any());
    }

    @Test
    void aRefusalWhileTheCustomerCanSubmitAgainLeavesNothingOnTheRma() {
        // given
        rma.setStatus(RMAStatus.Approved);
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        rma.setShipments(new ArrayList<>(List.of(placeholder)));

        // when
        owner.refused(creation("cmd-1"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).isEmpty();
    }

    @Test
    void aRefusedRetryOfTheOperatorLeavesAFailedRowToRetryAgain() {
        // given: the RMA waits for the items, so only the operator books the return again
        rma.setStatus(RMAStatus.WaitingForItems);
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setCreation(ShipmentCreationState.pending("cmd-2", LocalDateTime.now()));
        rma.setShipments(new ArrayList<>(List.of(placeholder)));

        // when
        owner.refused(creation("cmd-2"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).creationFailed()).isTrue();
        assertThat(rma.getShipments().get(0).getCreation().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }
}
