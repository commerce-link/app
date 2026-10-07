package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.*;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderShipmentOwnerTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private ShipmentTrackingSubscriber trackingSubscriber;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private OrderLifecycleEventPublisher lifecycleEventPublisher;

    private OrderShipmentOwner owner;
    private Order order;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        order = new Order("store-1");
        order.setOrderId("order-1");
        when(ordersRepository.findById("store-1", "order-1")).thenAnswer(i -> order);
        owner = new OrderShipmentOwner(ordersRepository, optimisticLockingExecutor, trackingSubscriber,
                orderLifecycle, lifecycleEventPublisher);
    }

    private static ShipmentCreationCheckRequest request(String commandId) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.ORDER)
                .ownerId("order-1").commandId(commandId).externalId("21480003").build();
    }

    private static Shipment placeholder(String commandId) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setCreation(ShipmentCreationState.pending(commandId, LocalDateTime.now()));
        return s;
    }

    @Test
    void theCreatingShipmentTakesThePlaceOfTheDeliveryChoiceAndInheritsIt() {
        // given: the order holds only the customer's delivery choice
        Shipment choice = new Shipment(ShipmentType.PickupPoint);
        choice.setCollectionPointCode("WAW23M");
        order.setShipments(new ArrayList<>(List.of(choice)));

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isTrue();
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).isCreationPendingFor("cmd-1")).isTrue();
        assertThat(order.getShipments().get(0).getCollectionPointCode()).isEqualTo("WAW23M");
    }

    @Test
    void aNewAttemptDropsTheFailedOne() {
        // given
        Shipment failed = placeholder("cmd-0");
        failed.setCreation(failed.getCreation().failed("Błąd"));
        order.setShipments(new ArrayList<>(List.of(failed)));

        // when
        owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).isCreationPendingFor("cmd-1")).isTrue();
    }

    @Test
    void aFailedCreationWithAPackageIdIsReplacedSoTheOrderHasNothingLeftToBook() {
        // given: Furgonetka gave a package id, then failed the command
        Shipment failed = placeholder("cmd-0");
        failed.setExternalId("ext-0");
        failed.setCreation(failed.getCreation().failed("Błąd"));
        order.setShipments(new ArrayList<>(List.of(failed)));

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));
        Shipment created = new Shipment(ShipmentType.Courier);
        created.setExternalId("21480003");
        created.setTrackingNo("A");
        owner.succeeded(request("cmd-1"), List.of(created));

        // then
        assertThat(marked).isTrue();
        assertThat(order.getShipments()).extracting(Shipment::getExternalId).containsExactly("21480003");
        assertThat(order.hasShipmentToBook()).isFalse();
    }

    @Test
    void aCreationPendingPastTheTimeoutIsDroppedByANewBookingAndItsLateResultIgnored() {
        // given
        Shipment stuck = new Shipment(ShipmentType.Courier);
        stuck.setExternalId("ext-0");
        stuck.setCreation(ShipmentCreationState.pending("cmd-0", LocalDateTime.now().minusMinutes(11)));
        order.setShipments(new ArrayList<>(List.of(stuck)));

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));
        boolean lateResult = owner.succeeded(request("cmd-0"), List.of(new Shipment(ShipmentType.Courier)));

        // then
        assertThat(marked).isTrue();
        assertThat(lateResult).isFalse();
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).isCreationPendingFor("cmd-1")).isTrue();
    }

    @Test
    void aLateResultStillSettlesACreationPendingPastTheTimeoutThatNobodyReplaced() {
        // given
        Shipment stuck = placeholder("cmd-0");
        stuck.setCreation(ShipmentCreationState.pending("cmd-0", LocalDateTime.now().minusMinutes(11)));
        order.setShipments(new ArrayList<>(List.of(stuck)));
        Shipment created = new Shipment(ShipmentType.Courier);
        created.setExternalId("21480003");

        // when
        boolean settled = owner.succeeded(request("cmd-0"), List.of(created));

        // then
        assertThat(settled).isTrue();
        assertThat(order.getShipments()).extracting(Shipment::getExternalId).containsExactly("21480003");
    }

    @Test
    void theExternalIdIsRecordedAndARefusalMarksTheShipmentFailed() {
        // given
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));

        // when
        owner.recordExternalId(request("cmd-1"));
        owner.refused(request("cmd-1"), "Nieprawidłowy kod pocztowy", null);

        // then
        Shipment s = order.getShipments().get(0);
        assertThat(s.getExternalId()).isEqualTo("21480003");
        assertThat(s.creationFailed()).isTrue();
        assertThat(s.getCreation().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }

    @Test
    void aRefusalInOurOwnWordsIsStoredAsAKey() {
        // given
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));

        // when
        owner.refused(request("cmd-1"), null, "shipping.creation.notCreated");

        // then
        Shipment s = order.getShipments().get(0);
        assertThat(s.creationFailed()).isTrue();
        assertThat(s.getCreation().getErrorKey()).isEqualTo("shipping.creation.notCreated");
        assertThat(s.getCreation().getError()).isNull();
    }

    @Test
    void aMissingOrderIsGone() {
        // given
        when(ordersRepository.findById("store-1", "order-1")).thenReturn(null);

        // when / then
        assertThat(owner.markCreating(request("cmd-1"), placeholder("cmd-1"))).isFalse();
    }

    @Test
    void shipmentsWithACourierOrderOrDataStayAndOnlyTheDataLessOnesGo() {
        // given: a booked label, a shipment sent by hand, a personal collection and a data-less row
        Shipment booked = new Shipment(ShipmentType.Courier);
        booked.setExternalId("ext-0");
        Shipment sentByHand = new Shipment(ShipmentType.Courier);
        sentByHand.setCarrier("DPD");
        sentByHand.setTrackingNo("T-1");
        sentByHand.setShippedAt(LocalDateTime.now());
        Shipment collected = new Shipment(ShipmentType.PersonalCollection);
        collected.setShippedAt(LocalDateTime.now());
        Shipment dataLess = new Shipment(ShipmentType.Courier);
        order.setShipments(new ArrayList<>(List.of(booked, sentByHand, collected, dataLess)));

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isTrue();
        assertThat(order.getShipments()).hasSize(4);
        assertThat(order.getShipments().subList(0, 3)).containsExactly(booked, sentByHand, collected);
        assertThat(order.getShipments().get(3).isCreationPendingFor("cmd-1")).isTrue();
    }

    @Test
    void theDeliveryChoiceIsNotCopiedOntoTheShipmentsThatStay() {
        // given
        Shipment choice = new Shipment(ShipmentType.PickupPoint);
        choice.setCollectionPointCode("WAW23M");
        Shipment booked = new Shipment(ShipmentType.Courier);
        booked.setExternalId("ext-0");
        order.setShipments(new ArrayList<>(List.of(choice, booked)));

        // when
        owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(order.getShipments()).hasSize(2);
        assertThat(order.getShipments().get(0).getType()).isEqualTo(ShipmentType.Courier);
        assertThat(order.getShipments().get(0).getCollectionPointCode()).isNull();
        assertThat(order.getShipments().get(1).getCollectionPointCode()).isEqualTo("WAW23M");
    }

    @Test
    void aShipmentAlreadyBeingCreatedKeepsASecondCommandFromStarting() {
        // given: another tab started a creation between the page's check and this write
        Shipment dataLess = new Shipment(ShipmentType.Courier);
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-0"), dataLess)));

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isFalse();
        assertThat(order.getShipments()).hasSize(2);
        assertThat(order.getShipments().get(0).isCreationPendingFor("cmd-0")).isTrue();
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void theOrderAwaitsOnlyTheCommandOfItsCreatingShipment() {
        // given
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));

        // when / then
        assertThat(owner.awaits(request("cmd-1"))).isTrue();
        assertThat(owner.awaits(request("cmd-2"))).isFalse();
    }

    @Test
    void createdShipmentsKeepThePlaceholdersDeliveryPointTypeAndCarrier() {
        // given
        Shipment waiting = placeholder("cmd-1");
        waiting.setType(ShipmentType.PickupPoint);
        waiting.setCollectionPointCode("WAW23M");
        waiting.setCarrier("InPost Paczkomaty");
        order.setShipments(new ArrayList<>(List.of(waiting)));
        Shipment created = new Shipment(ShipmentType.Courier);
        created.setExternalId("21480003");
        created.setTrackingNo("A");

        // when
        boolean settled = owner.succeeded(request("cmd-1"), List.of(created));

        // then
        assertThat(settled).isTrue();
        Shipment saved = order.getShipments().get(0);
        assertThat(saved.getTrackingNo()).isEqualTo("A");
        assertThat(saved.getType()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(saved.getCollectionPointCode()).isEqualTo("WAW23M");
        assertThat(saved.getCarrier()).isEqualTo("InPost Paczkomaty");
        verify(lifecycleEventPublisher).publish(any(Order.class), eq(OrderLifecycleEventType.ShipmentCreated));
    }

    @Test
    void aSideEffectThatFailsStillLeavesTheShipmentCreated() {
        // given: a redelivery would find no placeholder, so the creation must count as settled
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        doThrow(new RuntimeException("tracking down")).when(trackingSubscriber).subscribe(any(), any(Order.class));
        Shipment created = new Shipment(ShipmentType.Courier);
        created.setTrackingNo("A");

        // when
        boolean settled = owner.succeeded(request("cmd-1"), List.of(created));

        // then
        assertThat(settled).isTrue();
        assertThat(order.getShipments().get(0).getTrackingNo()).isEqualTo("A");
    }

    @Test
    void ourReasonIsKeptAsAKeyAndTheProvidersAsText() {
        // given
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"), placeholder("cmd-2"))));

        // when
        owner.failed(request("cmd-1"), null, "shipping.creation.unconfirmed");
        owner.failed(request("cmd-2"), "Nieprawidłowy kod pocztowy", null);

        // then
        assertThat(order.getShipments().get(0).getCreation().getErrorKey()).isEqualTo("shipping.creation.unconfirmed");
        assertThat(order.getShipments().get(0).getCreation().getError()).isNull();
        assertThat(order.getShipments().get(1).getCreation().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }

    @Test
    void aFailureForAnotherCommandChangesNothing() {
        // given
        order.setShipments(new ArrayList<>(List.of(placeholder("cmd-2"))));

        // when
        owner.failed(request("cmd-1"), "Błąd", null);

        // then
        assertThat(order.getShipments().get(0).getCreation().isPending()).isTrue();
        verify(ordersRepository, never()).save(any());
        verifyNoInteractions(lifecycleEventPublisher);
    }

    private static Shipment awaitingPickup(String externalId, String trackingNo) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo(trackingNo);
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    @Test
    void aPickupChangeOnAMissingOrderAppliesNowhere() {
        // given
        when(ordersRepository.findById("store-1", "gone")).thenReturn(null);

        // when / then
        assertThat(owner.applyPickup("store-1", "gone", List.of("1"), p -> ShipmentPickup.notRequired())).isZero();
    }

    @Test
    void aPickupChangeLandsOnEveryRowOfThePackageOnly() {
        // given: two parcels of package 1 and another package
        order.setShipments(new ArrayList<>(List.of(awaitingPickup("1", "A"), awaitingPickup("1", "B"),
                awaitingPickup("2", "C"))));

        // when
        int changed = owner.applyPickup("store-1", "order-1", List.of("1"), p -> p.failed("x"));

        // then
        assertThat(changed).isEqualTo(2);
        assertThat(order.getShipments()).extracting(s -> s.getPickup().isFailed()).containsExactly(true, true, false);
        verify(ordersRepository).save(order);
    }

    @Test
    void aPickupChangeThatAppliesNowhereSavesNothing() {
        // given
        order.setShipments(new ArrayList<>(List.of(awaitingPickup("1", "A"))));

        // when
        int changed = owner.applyPickup("store-1", "order-1", List.of("1"), p -> p);

        // then
        assertThat(changed).isZero();
        verify(ordersRepository, never()).save(any());
    }
}
