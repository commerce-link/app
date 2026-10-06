package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.web.deliveries.details.DeliveryFixtures.*;

class DeliveryHeaderFactoryTest {

    private static DeliveryPageModel.Header header(Delivery delivery, DeliveryViewer viewer) {
        return DeliveryHeaderFactory.header(data(delivery), viewer,
                DeliveryLinks.of(viewer.superAdmin(), STORE_ID, delivery.getDeliveryId()));
    }

    private static List<DeliveryPageModel.StatusCard> cards(Delivery delivery, DeliveryViewer viewer) {
        return DeliveryHeaderFactory.statusCards(data(delivery), viewer,
                DeliveryLinks.of(viewer.superAdmin(), STORE_ID, delivery.getDeliveryId()));
    }

    static Stream<Arguments> primaryActions() {
        return Stream.of(
                Arguments.of("approval, super admin", (Supplier<Delivery>) () -> global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL)), SUPER_ADMIN, "deliveries.details.primary.approve"),
                Arguments.of("approval, store admin", (Supplier<Delivery>) () -> global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL)), ADMIN, null),
                Arguments.of("failed OWN, store admin", (Supplier<Delivery>) () -> own(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), ADMIN, "deliveries.details.primary.retry"),
                Arguments.of("failed OWN, super admin", (Supplier<Delivery>) () -> own(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), SUPER_ADMIN, null),
                Arguments.of("failed GLOBAL, store admin", (Supplier<Delivery>) () -> global(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), ADMIN, "deliveries.details.primary.receiveAll"),
                Arguments.of("failed GLOBAL, super admin", (Supplier<Delivery>) () -> global(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), SUPER_ADMIN, "deliveries.details.primary.retry"),
                Arguments.of("failed with a document", (Supplier<Delivery>) () -> withGoodsReceipt(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED))), ADMIN, "deliveries.details.primary.receiveAll"),
                Arguments.of("outcome unknown, store admin", (Supplier<Delivery>) () -> outcomeUnknown(own(warehouse())), ADMIN, "deliveries.details.primary.reconcile"),
                Arguments.of("outcome unknown, user", (Supplier<Delivery>) () -> outcomeUnknown(own(warehouse())), USER, "deliveries.details.primary.receiveAll"),
                Arguments.of("dispatched, store admin", (Supplier<Delivery>) () -> own(withStatus(warehouse(), DeliveryOrderStatus.ORDER_DISPATCHED)), ADMIN, "deliveries.details.primary.reconcile"),
                Arguments.of("ordering", (Supplier<Delivery>) () -> own(withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING)), ADMIN, null),
                Arguments.of("in transit, store admin", (Supplier<Delivery>) DeliveryFixtures::warehouse, ADMIN, "deliveries.details.primary.receiveAll"),
                Arguments.of("in transit, user", (Supplier<Delivery>) DeliveryFixtures::warehouse, USER, "deliveries.details.primary.receiveAll"),
                Arguments.of("in transit, super admin", (Supplier<Delivery>) DeliveryFixtures::warehouse, SUPER_ADMIN, null),
                Arguments.of("received", (Supplier<Delivery>) () -> received(warehouse()), ADMIN, null),
                Arguments.of("failed, then received", (Supplier<Delivery>) () -> received(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED))), ADMIN, null),
                Arguments.of("dropship waiting, store admin", (Supplier<Delivery>) DeliveryFixtures::dropship, ADMIN, "deliveries.details.primary.shipAll"),
                Arguments.of("dropship waiting, super admin", (Supplier<Delivery>) DeliveryFixtures::dropship, SUPER_ADMIN, "deliveries.details.primary.shipAll"),
                Arguments.of("dropship waiting, user", (Supplier<Delivery>) DeliveryFixtures::dropship, USER, null),
                Arguments.of("dropship cancelled by supplier", (Supplier<Delivery>) () -> tracking(dropship(), DeliveryTrackingState.CANCELLED_BY_SUPPLIER), ADMIN, null),
                Arguments.of("dropship shipped", (Supplier<Delivery>) () -> received(dropship()), ADMIN, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("primaryActions")
    void thePrimaryActionFollowsTheStateTheRoleAndTheConnection(String name, Supplier<Delivery> delivery,
                                                                DeliveryViewer viewer, String expectedLabelKey) {
        // when
        DeliveryPageModel.PrimaryAction primary = header(delivery.get(), viewer).primary();

        // then
        assertThat(primary == null ? null : primary.labelKey()).isEqualTo(expectedLabelKey);
    }

    @Test
    void receiveAllOpensItsDialogAndRetryPostsWithoutOne() {
        // when
        DeliveryPageModel.PrimaryAction receiveAll = header(warehouse(), ADMIN).primary();
        DeliveryPageModel.PrimaryAction retry = header(own(withStatus(warehouse(), DeliveryOrderStatus.FAILED)), ADMIN).primary();
        DeliveryPageModel.PrimaryAction approve = header(global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL)), SUPER_ADMIN).primary();

        // then
        assertThat(receiveAll.dialogId()).isEqualTo("receive-all-dialog");
        assertThat(receiveAll.href()).isEqualTo("/dashboard/deliveries/details?deliveryId=" + DELIVERY_ID + "&open=receive-all#receive-all-dialog");
        assertThat(retry.postAction()).isEqualTo("/dashboard/deliveries/" + DELIVERY_ID + "/purchase/retry");
        assertThat(retry.dialogId()).isNull();
        assertThat(approve.href()).isEqualTo("/dashboard/store/store-1/deliveries/ed2fca8a-073d-47cd-8bd3-2f1cedfdbeb2/approval");
    }

    @Test
    void thePillIsTheListState() {
        for (Delivery delivery : List.of(warehouse(), received(warehouse()), outcomeUnknown(own(warehouse())), dropship(),
                tracking(dropship(), DeliveryTrackingState.SHIPPED_WITHOUT_DATA))) {
            // when
            DeliveryPageModel.Header header = header(delivery, ADMIN);

            // then
            DeliveryListState state = DeliveryListState.of(delivery);
            assertThat(header.stateKey()).isEqualTo(state.messageKey());
            assertThat(header.stateTone()).isEqualTo(state.tone());
        }
    }

    @Test
    void theMetaLineCarriesTheNumberTheDatesTheProgressAndTheGrossTotal() {
        // given
        Delivery delivery = partlyReceived(warehouse());
        delivery.setExternalDeliveryIdProvisional(true);

        // when
        DeliveryPageModel.Header header = header(delivery, ADMIN);

        // then
        assertThat(header.typeKey()).isEqualTo("deliveries.details.type.warehouse");
        assertThat(header.typeIcon()).isEqualTo("fa-warehouse");
        assertThat(header.externalId()).isEqualTo("MH-2026/0917");
        assertThat(header.provisional()).isTrue();
        assertThat(header.orderedAt()).isEqualTo("01.10.2026");
        assertThat(header.dateKey()).isEqualTo("deliveries.details.meta.due");
        assertThat(header.date()).isEqualTo("08.10.2026");
        assertThat(header.showProgress()).isTrue();
        assertThat(header.receivedAllocations()).isEqualTo(1);
        assertThat(header.totalAllocations()).isEqualTo(2);
        assertThat(header.totalGross()).isEqualTo("6 253,32");
    }

    @Test
    void theGrossTotalIsMissingUntilTheVatIsSet() {
        // given
        Delivery unset = warehouse();
        unset.setTax(0.0);
        Delivery set = warehouse();
        set.setTax(1.23);

        // when
        String unsetGross = header(unset, ADMIN).totalGross();
        String setGross = header(set, ADMIN).totalGross();

        // then
        assertThat(unsetGross).isNull();
        assertThat(setGross).isEqualTo("6 253,32");
    }

    @Test
    void theNumberSaysWhyItIsMissing() {
        // given
        Delivery ordering = withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING);
        Delivery dispatched = withStatus(warehouse(), DeliveryOrderStatus.ORDER_DISPATCHED);
        dispatched.setExternalDeliveryId(null);
        Delivery none = warehouse();
        none.setExternalDeliveryId(" ");

        // when / then
        assertThat(header(ordering, ADMIN).externalId()).isNull();
        assertThat(header(ordering, ADMIN).externalIdNoteKey()).isEqualTo("deliveries.details.meta.number.pending");
        assertThat(header(dispatched, ADMIN).externalIdNoteKey()).isEqualTo("deliveries.details.meta.number.dispatched");
        assertThat(header(none, ADMIN).externalId()).isNull();
        assertThat(header(none, ADMIN).externalIdNoteKey()).isNull();
    }

    @Test
    void aReceivedDeliveryShowsWhenItArrivedOrWasShipped() {
        // when
        DeliveryPageModel.Header warehouse = header(received(warehouse()), ADMIN);
        DeliveryPageModel.Header dropship = header(received(dropship()), ADMIN);

        // then
        assertThat(warehouse.dateKey()).isEqualTo("deliveries.details.meta.received");
        assertThat(warehouse.date()).isEqualTo("02.10.2026");
        assertThat(warehouse.showProgress()).isFalse();
        assertThat(dropship.dateKey()).isEqualTo("deliveries.details.meta.shipped");
    }

    @Test
    void theMoreMenuGreysDeleteWithTheFirstReasonThatApplies() {
        // when
        DeliveryPageModel.MoreMenu withItems = header(warehouse(), ADMIN).more();
        Delivery empty = withStatus(withAllocations(warehouse()), DeliveryOrderStatus.ORDER_PENDING);
        DeliveryPageModel.MoreMenu ordering = header(empty, ADMIN).more();
        DeliveryPageModel.MoreMenu deletable = header(withAllocations(warehouse()), ADMIN).more();
        DeliveryPageModel.MoreMenu user = header(warehouse(), USER).more();

        // then
        assertThat(withItems.delete()).isEqualTo(DeliveryPageModel.ActionState.off("deliveries.details.reason.removeItemsFirst"));
        assertThat(ordering.delete()).isEqualTo(DeliveryPageModel.ActionState.off("deliveries.details.reason.ordering"));
        assertThat(deletable.delete()).isEqualTo(DeliveryPageModel.ActionState.on());
        assertThat(deletable.deleteHref()).isEqualTo("/dashboard/deliveries/" + DELIVERY_ID + "/confirm/delete");
        assertThat(user.shown()).isFalse();
    }

    @Test
    void aPartlyShippedDropshipShowsHowManyLinesWereShippedInTheMetaLine() {
        // given
        Delivery delivery = partlyReceived(withAllocations(dropship(),
                orderAllocation("G.Skill TwinMatch 32GB DDR5 Kit", "5900000000003", "MFN-TWIN-01", 448.0, 1, false),
                orderAllocation("Samsung MirageDrive 2TB NVMe", "5900000000006", "MFN-MIRAGE-01", 635.0, 1, false)));

        // when
        DeliveryPageModel.Header dropship = header(delivery, ADMIN);
        DeliveryPageModel.Header warehouse = header(partlyReceived(warehouse()), ADMIN);

        // then
        assertThat(dropship.showProgress()).isTrue();
        assertThat(dropship.progressKey()).isEqualTo("deliveries.details.meta.progress.shipped");
        assertThat(dropship.receivedAllocations()).isEqualTo(1);
        assertThat(dropship.totalAllocations()).isEqualTo(2);
        assertThat(warehouse.progressKey()).isEqualTo("deliveries.details.meta.progress");
    }

    @Test
    void anAdminSeesTheApprovalAsTheReasonDeleteIsGreyedEvenWhileTheDeliveryHoldsItems() {
        // given
        Delivery awaitingWithItems = withStatus(warehouse(), DeliveryOrderStatus.AWAITING_APPROVAL);
        Delivery orderingWithItems = withStatus(warehouse(), DeliveryOrderStatus.ORDER_PENDING);

        // when
        DeliveryPageModel.MoreMenu awaiting = header(awaitingWithItems, ADMIN).more();
        DeliveryPageModel.MoreMenu ordering = header(orderingWithItems, ADMIN).more();

        // then
        assertThat(awaiting.delete()).isEqualTo(DeliveryPageModel.ActionState.off("deliveries.details.reason.awaitingApproval"));
        assertThat(ordering.delete()).isEqualTo(DeliveryPageModel.ActionState.off("deliveries.details.reason.ordering"));
    }

    @Test
    void refreshingTheProvisionalNumberEndsWithTheReceipt() {
        // given
        Delivery inTransit = warehouse();
        inTransit.setExternalDeliveryIdProvisional(true);
        Delivery done = received(warehouse());
        done.setExternalDeliveryIdProvisional(true);

        // when / then
        assertThat(header(inTransit, ADMIN).more().refresh()).isEqualTo(DeliveryPageModel.ActionState.on());
        assertThat(header(done, ADMIN).more().refresh()).isEqualTo(DeliveryPageModel.ActionState.off("deliveries.details.reason.received"));
        assertThat(header(warehouse(), ADMIN).more().refresh().visible()).isFalse();
    }

    @Test
    void onlyTheSuperAdminRejectsARequest() {
        // given
        Delivery awaiting = global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL));

        // when / then
        assertThat(header(awaiting, SUPER_ADMIN).more().reject()).isEqualTo(DeliveryPageModel.ActionState.on());
        assertThat(header(awaiting, ADMIN).more().reject().visible()).isFalse();
    }

    @Test
    void aFailedOrderSaysWhoCanRetryIt() {
        // given
        Delivery ownFailed = own(withStatus(warehouse(), DeliveryOrderStatus.FAILED));
        ownFailed.setOrderErrorMessage("insufficient stock");
        Delivery globalFailed = global(withStatus(warehouse(), DeliveryOrderStatus.FAILED));

        // when
        DeliveryPageModel.StatusCard admin = cards(ownFailed, ADMIN).get(0);
        DeliveryPageModel.StatusCard platform = cards(globalFailed, ADMIN).get(0);
        DeliveryPageModel.StatusCard store = cards(ownFailed, SUPER_ADMIN).get(0);

        // then
        assertThat(admin.tone()).isEqualTo("is-bad");
        assertThat(admin.reason()).isEqualTo("insufficient stock");
        assertThat(admin.textKey()).isEqualTo("deliveries.details.status.failed.text");
        assertThat(admin.actions()).extracting(DeliveryPageModel.CardAction::dialogId).containsExactly("complete-dialog");
        assertThat(platform.textKey()).isEqualTo("deliveries.details.status.noRepair.platform");
        assertThat(platform.actions()).isEmpty();
        assertThat(store.textKey()).isEqualTo("deliveries.details.status.noRepair.store");
    }

    @Test
    void anUnknownOutcomeOffersConfirmationAndOrderingAnyway() {
        // when
        DeliveryPageModel.StatusCard card = cards(outcomeUnknown(own(warehouse())), ADMIN).get(0);

        // then
        assertThat(card.tone()).isEqualTo("is-warn");
        assertThat(card.titleKey()).isEqualTo("deliveries.details.status.unknown.title");
        assertThat(card.reason()).isEqualTo("HTTP 502 Bad Gateway");
        assertThat(card.actions()).extracting(DeliveryPageModel.CardAction::dialogId).containsExactly("complete-dialog", "force-dialog");
        assertThat(card.actions().get(1).danger()).isTrue();
    }

    @Test
    void aPurchaseAwaitingTheSupplierShowsAnInfoCardWithoutRepairs() {
        // when
        List<DeliveryPageModel.StatusCard> cards = cards(awaitingSupplier(own(warehouse())), ADMIN);

        // then
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).tone()).isEqualTo("is-info");
        assertThat(cards.get(0).titleKey()).isEqualTo("deliveries.details.status.awaitingSupplier.title");
        assertThat(cards.get(0).textKey()).isEqualTo("deliveries.details.status.awaitingSupplier.text");
        assertThat(cards.get(0).actions()).isEmpty();
    }

    @Test
    void whileTheSupplierConfirmsNeitherReconcileNorTheNumberRefreshIsOffered() {
        // given
        Delivery awaiting = awaitingSupplier(own(warehouse()));
        Delivery unconfirmed = own(withStatus(warehouse(), DeliveryOrderStatus.ORDER_DISPATCHED));
        unconfirmed.setExternalDeliveryIdProvisional(true);

        // when
        DeliveryPageModel.Header header = header(awaiting, ADMIN);

        // then
        assertThat(header.primary() == null ? null : header.primary().labelKey())
                .isNotEqualTo("deliveries.details.primary.reconcile");
        assertThat(header.more().refresh().visible()).isFalse();
        assertThat(header(unconfirmed, ADMIN).more().refresh().visible()).isTrue();
    }

    @Test
    void theStoreAdminLearnsWhoHasTheMoveWhileTheSuperAdminSeesNoCard() {
        // given
        Delivery awaiting = global(withStatus(dropship(), DeliveryOrderStatus.AWAITING_APPROVAL));

        // when / then
        assertThat(cards(awaiting, ADMIN)).extracting(DeliveryPageModel.StatusCard::titleKey)
                .containsExactly("deliveries.details.status.approval.title");
        assertThat(cards(awaiting, SUPER_ADMIN)).isEmpty();
    }

    @Test
    void trackingWarningsStopOnceTheDeliveryIsShipped() {
        // given
        Delivery cancelled = tracking(dropship(), DeliveryTrackingState.CANCELLED_BY_SUPPLIER);
        Delivery givenUp = tracking(dropship(), DeliveryTrackingState.GIVEN_UP);
        Delivery shippedAfterCancel = received(tracking(dropship(), DeliveryTrackingState.CANCELLED_BY_SUPPLIER));

        // when
        List<DeliveryPageModel.StatusCard> cancelledCards = cards(cancelled, ADMIN);

        // then
        assertThat(cancelledCards).extracting(DeliveryPageModel.StatusCard::titleKey)
                .containsExactly("deliveries.details.status.cancelled.title");
        assertThat(cancelledCards.get(0).actions().get(0).dialogId()).isEqualTo("remove-dialog");
        assertThat(cancelledCards.get(0).actions().get(0).href()).endsWith("&open=remove-all#remove-dialog");
        assertThat(cancelledCards.get(0).actions().get(0).selectPending()).isTrue();
        assertThat(cards(givenUp, ADMIN)).extracting(DeliveryPageModel.StatusCard::textKey)
                .containsExactly("deliveries.details.status.givenUp.text");
        assertThat(cards(shippedAfterCancel, ADMIN)).isEmpty();
    }

    @Test
    void theSupplierCancellationCardGoesAwayOnceTheItemsAreRemoved() {
        // given
        Delivery emptied = withAllocations(tracking(dropship(), DeliveryTrackingState.CANCELLED_BY_SUPPLIER));

        // when
        List<DeliveryPageModel.StatusCard> cards = cards(emptied, ADMIN);

        // then
        assertThat(cards).as("the card asks to remove the items; with none left it has nothing to ask").isEmpty();
    }

    @Test
    void goodsBoundForACustomerAreAnnouncedUntilReceived() {
        // given
        Delivery dtc = withAllocations(warehouse(), orderAllocation("SSD", "590", "MFN-SSD", 100, 1, true));

        // when / then
        assertThat(cards(dtc, ADMIN)).extracting(DeliveryPageModel.StatusCard::titleKey)
                .containsExactly("deliveries.details.status.dtc.title");
        assertThat(cards(received(dtc), ADMIN)).isEmpty();
    }
}
