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
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMALifecycle;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaShipmentOwnerTest {

    @Mock private RMARepository rmaRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private RMALifecycle rmaLifecycle;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private EmailClient emailClient;
    @Mock private StoreNotificationService notifications;
    @Mock private MessageSource messageSource;

    private RMA rma;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        when(rmaRepository.findById("store-1", "rma-1")).thenAnswer(i -> rma);
    }

    private RmaShipmentOwner operatorOwner() {
        return new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle,
                trackingSubscriber);
    }

    private RmaShipmentOwner returnOwner() {
        return new RmaReturnShipmentOwner(rmaRepository, optimisticLockingExecutor, rmaItemsRepository, rmaLifecycle,
                trackingSubscriber, emailClient, notifications, messageSource);
    }

    private static ShipmentCreationCheckRequest settled(boolean toClient) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.RMA)
                .ownerId("rma-1").commandId("cmd-1").externalId("21480003").toClient(toClient)
                .itemIds(List.of("item-1")).build();
    }

    private static Shipment createdShipment() {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId("21480003");
        s.setTrackingNo("A");
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    private RMAItem item(String itemId) {
        RMAItem item = new RMAItem();
        item.setItemId(itemId);
        item.setRmaId("rma-1");
        return item;
    }

    private static ShipmentCreationCheckRequest request(String commandId) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.RMA)
                .ownerId("rma-1").commandId(commandId).externalId("21480003").build();
    }

    private static Shipment placeholder(String commandId) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setCreation(ShipmentCreationState.pending(commandId, LocalDateTime.now()));
        return s;
    }

    @Test
    void theCreatingShipmentReplacesTheEarlierOnes() {
        // given
        Shipment earlier = new Shipment(ShipmentType.Courier);
        earlier.setTrackingNo("T-0");
        rma.setShipments(new ArrayList<>(List.of(earlier)));
        RmaShipmentOwner owner = operatorOwner();

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isTrue();
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).isCreationPendingFor("cmd-1")).isTrue();
        verify(rmaRepository).save(rma);
    }

    @Test
    void anRmaWithAShipmentBeingCreatedIsNotSentASecondCommand() {
        // given: replacing the list would lose the first command's placeholder and with it a paid label
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-0"))));
        RmaShipmentOwner owner = operatorOwner();

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isFalse();
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).isCreationPendingFor("cmd-0")).isTrue();
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void anOperatorsRefusedShipmentStaysAsFailed() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RmaShipmentOwner owner = operatorOwner();

        // when
        owner.refused(request("cmd-1"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).getCreation().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }

    @Test
    void aCustomersRefusedReturnLeavesNothingWaitingOnTheRma() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RmaShipmentOwner owner = returnOwner();

        // when
        owner.refused(request("cmd-1"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).isEmpty();
        verify(rmaRepository).save(rma);
    }

    @Test
    void aWriteForAnotherCommandChangesNothing() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-2"))));
        RmaShipmentOwner owner = operatorOwner();

        // when
        owner.recordExternalId(request("cmd-1"));

        // then
        assertThat(rma.getShipments().get(0).getExternalId()).isNull();
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aCreatedShipmentToTheCustomerMarksTheItemsReturned() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RMAItem item = item("item-1");
        RMAItem notSent = item("item-2");
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(item, notSent));

        // when
        boolean settled = operatorOwner().succeeded(settled(true), List.of(createdShipment()));

        // then
        assertThat(settled).isTrue();
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).getTrackingNo()).isEqualTo("A");
        assertThat(item.getStatus()).isEqualTo(RMAItemStatus.ReturnedToClient);
        assertThat(notSent.getStatus()).isNotEqualTo(RMAItemStatus.ReturnedToClient);
        verify(rmaItemsRepository).batchSave(List.of(item));
        verify(trackingSubscriber).subscribe(eq("store-1"), any(RMA.class));
        verify(rmaLifecycle).update(any(RMA.class), eq(List.of(item)));
    }

    @Test
    void aCreatedShipmentToTheRepairCenterMarksTheItemsSentToRepair() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RMAItem item = item("item-1");
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(item));

        // when
        operatorOwner().succeeded(settled(false), List.of(createdShipment()));

        // then
        assertThat(item.getStatus()).isEqualTo(RMAItemStatus.SentForRepair);
    }

    @Test
    void aSecondDeliveryOfTheSameSuccessChangesNothing() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(item("item-1")));
        RmaShipmentOwner owner = operatorOwner();
        owner.succeeded(settled(true), List.of(createdShipment()));
        clearInvocations(rmaRepository, rmaItemsRepository, rmaLifecycle, trackingSubscriber);

        // when
        boolean settled = owner.succeeded(settled(true), List.of(createdShipment()));

        // then
        assertThat(settled).isFalse();
        verify(rmaRepository, never()).save(any());
        verifyNoInteractions(rmaItemsRepository, rmaLifecycle, trackingSubscriber);
    }

    @Test
    void aCustomersCreatedReturnLeavesTheItemsAsTheyAre() {
        // given: the items change once the goods arrive, as before
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));

        // when
        boolean settled = returnOwner().succeeded(settled(false), List.of(createdShipment()));

        // then
        assertThat(settled).isTrue();
        assertThat(rma.getShipments().get(0).getTrackingNo()).isEqualTo("A");
        verifyNoInteractions(rmaItemsRepository, rmaLifecycle, trackingSubscriber);
    }

    @Test
    void aFailureOfACustomersReturnStaysVisibleAsFailed() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));

        // when
        returnOwner().failed(request("cmd-1"), null, "shipping.creation.unconfirmed");

        // then
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).creationFailed()).isTrue();
        assertThat(rma.getShipments().get(0).getCreation().getErrorKey()).isEqualTo("shipping.creation.unconfirmed");
    }
}
