package pl.commercelink.orders.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import pl.commercelink.shipping.ShippingIntegrationNames;
import pl.commercelink.shipping.ShippingIntegrationNamesFixture;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaShipmentsViewFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private ShippingService shippingService;
    @Mock private StoresRepository storesRepository;
    @Mock private Store store;
    @Spy private ShippingIntegrationNames shippingIntegrationNames = ShippingIntegrationNamesFixture.names();

    @InjectMocks
    private RmaShipmentsViewFactory factory;

    @BeforeEach
    void setUp() {
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingService.supportsLabels(eq(store), eq("furgonetka"))).thenReturn(true);
    }

    private static RMA rmaWith(Shipment... shipments) {
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setShipments(new ArrayList<>(List.of(shipments)));
        return rma;
    }

    private static Shipment operatorPackage() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("DPD");
        shipment.setPickUpAddressId("addr-1");
        shipment.setExternalId("21480003");
        shipment.setTrackingNo("0000123");
        shipment.setPickup(ShipmentPickup.awaiting());
        return shipment;
    }

    private static Shipment customerReturn() {
        Shipment shipment = operatorPackage();
        shipment.setPickUpAddressId(null);
        return shipment;
    }

    private static Shipment failedCreation(boolean customerReturn) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCarrier("DPD");
        shipment.setPickUpAddressId(customerReturn ? null : "addr-1");
        shipment.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()).failed("Brak środków"));
        return shipment;
    }

    /** Nothing to do about the shipment but correct it by hand ("Edytuj" in its menu). */
    private static void assertOnlyEditable(RmaShipmentsView.Row row) {
        assertThat(row.editable()).isTrue();
        assertThat(row.labelHref()).isNull();
        assertThat(row.retryHref()).isNull();
        assertThat(row.removeAction()).isNull();
        assertThat(row.pickupRetryAction()).isNull();
        assertThat(row.returnRetryAction()).isNull();
    }

    @Test
    void anOperatorPackageWaitingForPickupOffersTheLabelAndThePickupPage() {
        // when
        RmaShipmentsView view = factory.build(rmaWith(operatorPackage()), false, PL);

        // then
        RmaShipmentsView.Row row = view.rows().get(0);
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.pickup.awaiting");
        assertThat(row.labelHref()).isEqualTo("/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/rma/rma-1");
        assertThat(row.pickupRetryAction()).isNull();
        assertThat(view.pickupHref())
                .isEqualTo("/dashboard/shipping/pickups/new?group=furgonetka%7CDPD%7Caddr-1&back=/dashboard/rma/rma-1");
        assertThat(view.pollHref()).isNull();
    }

    @Test
    void theRmaCardKeepsItsPickupButton() {
        // given: unlike the order card, the RMA card stays an entry to the pickup page (there is no RMA list button)
        RmaShipmentsView view = factory.build(rmaWith(operatorPackage()), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        assertThat(html).contains("href=\"/dashboard/shipping/pickups/new?group=furgonetka%7CDPD%7Caddr-1&amp;back=/dashboard/rma/rma-1\"")
                .contains("<span>Zamów odbiór</span>");
    }

    @Test
    void aCustomerReturnWhosePickupFailedIsOrderedAgainHereNotOnThePickupPage() {
        // given
        Shipment failed = customerReturn();
        failed.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera"));

        // when
        RmaShipmentsView view = factory.build(rmaWith(failed), false, PL);

        // then
        RmaShipmentsView.Row row = view.rows().get(0);
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.pickup.failed");
        assertThat(row.stateArgs()).containsExactly("Brak kuriera");
        assertThat(row.pickupRetryAction()).isEqualTo("/dashboard/rma/rma-1/shipments/21480003/pickup");
        assertThat(view.pickupHref()).isNull();
    }

    @Test
    void aFailedOperatorCreationOffersRetryAndRemove() {
        // when
        RmaShipmentsView.Row row = factory.build(rmaWith(failedCreation(false)), false, PL).rows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("order.shipments.state.creation.failed");
        assertThat(row.retryHref()).isEqualTo("/dashboard/rma/rma-1#pozycje");
        assertThat(row.removeAction()).isEqualTo("/dashboard/rma/rma-1/shipments/creations/cmd-1/remove");
        assertThat(row.labelHref()).isNull();
    }

    @Test
    void aFailedCustomerReturnIsBookedAgainWithWhatTheCustomerChose() {
        // given
        RMA rma = rmaWith(failedCreation(true));
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");

        // when
        RmaShipmentsView.Row row = factory.build(rma, false, PL).rows().get(0);

        // then
        assertThat(row.returnRetryAction()).isEqualTo("/dashboard/rma/rma-1/return-shipment/retry");
        assertThat(row.retryHref()).isNull();
        assertThat(row.removeAction()).isNotNull();
    }

    @Test
    void aFailedCustomerReturnWithoutTheChosenPackageCanOnlyBeRemoved() {
        // given: submitted before the package template was kept on the RMA
        RMA rma = rmaWith(failedCreation(true));
        rma.setShippingDetails(ShippingDetails._default());

        // when
        RmaShipmentsView.Row row = factory.build(rma, false, PL).rows().get(0);

        // then
        assertThat(row.returnRetryAction()).isNull();
        assertThat(row.retryHref()).isNull();
        assertThat(row.removeAction()).isNotNull();
    }

    @Test
    void aFailedOperatorShipmentGetsNoReturnRetry() {
        // given
        RMA rma = rmaWith(failedCreation(false));
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");

        // when
        RmaShipmentsView.Row row = factory.build(rma, false, PL).rows().get(0);

        // then
        assertThat(row.returnRetryAction()).isNull();
        assertThat(row.retryHref()).isNotNull();
    }

    @Test
    void aShipmentBeingCreatedOrItsPickupOrderedMakesThePagePoll() {
        // given
        Shipment creating = new Shipment(ShipmentType.Courier);
        creating.setProvider("furgonetka");
        creating.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        Shipment ordering = operatorPackage();
        ordering.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));

        // when
        RmaShipmentsView whileCreating = factory.build(rmaWith(creating), false, PL);
        RmaShipmentsView whileOrdering = factory.build(rmaWith(ordering), false, PL);

        // then
        assertThat(whileCreating.pollHref()).isEqualTo("/dashboard/rma/rma-1/shipments/state");
        assertThat(whileCreating.rows().get(0).stateKey()).isEqualTo("order.shipments.state.creating");
        assertThat(whileCreating.rows().get(0).removeAction()).isNull();
        assertThat(whileOrdering.pollHref()).isEqualTo("/dashboard/rma/rma-1/shipments/state");
    }

    @Test
    void aCustomerReturnStuckPendingPastTheTimeoutCanBeBookedAgainOrRemoved() {
        // given
        Shipment stuck = failedCreation(true);
        stuck.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now().minusMinutes(11)));
        RMA rma = rmaWith(stuck);
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");

        // when
        RmaShipmentsView view = factory.build(rma, false, PL);

        // then
        RmaShipmentsView.Row row = view.rows().get(0);
        assertThat(row.stateKey()).isEqualTo("shipping.creation.unconfirmed");
        assertThat(row.returnRetryAction()).isEqualTo("/dashboard/rma/rma-1/return-shipment/retry");
        assertThat(row.removeAction()).isEqualTo("/dashboard/rma/rma-1/shipments/creations/cmd-1/remove");
        assertThat(view.pollHref()).isNull();
    }

    @Test
    void aCustomerReturnPickupPendingPastTheTimeoutIsOrderedAgainHere() {
        // given
        Shipment stuck = customerReturn();
        stuck.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now().minusMinutes(11), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));

        // when
        RmaShipmentsView view = factory.build(rmaWith(stuck), false, PL);

        // then
        RmaShipmentsView.Row row = view.rows().get(0);
        assertThat(row.stateKey()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(row.pickupRetryAction()).isEqualTo("/dashboard/rma/rma-1/shipments/21480003/pickup");
        assertThat(view.pollHref()).isNull();
    }

    @Test
    void aClosedRmaKeepsOnlyTheLabel() {
        // given
        Shipment failedPickup = customerReturn();
        failedPickup.setPickup(ShipmentPickup.awaiting().failed("x"));

        // when
        RmaShipmentsView view = factory.build(rmaWith(operatorPackage(), failedPickup, failedCreation(false)), true, PL);

        // then
        assertThat(view.pickupHref()).isNull();
        assertThat(view.rows().get(0).labelHref()).isNotNull();
        assertThat(view.rows().get(1).pickupRetryAction()).isNull();
        assertThat(view.rows().get(2).retryHref()).isNull();
        assertThat(view.rows().get(2).removeAction()).isNull();
    }

    @Test
    void aShipmentTypedInByHandHasNoStateAndAsksNothing() {
        // given
        Shipment manual = new Shipment(ShipmentType.Courier);
        manual.setTrackingNo("T-1");
        when(shippingService.supportsLabels(any(), any())).thenThrow(new AssertionError("no package, no account load"));

        // when
        RmaShipmentsView view = factory.build(rmaWith(manual), false, PL);

        // then
        assertThat(view.rows().get(0).stateKey()).isNull();
        assertOnlyEditable(view.rows().get(0));
    }

    @Test
    void theCardRendersTheFormattedStateAndTheActions() {
        // given
        Shipment ordered = operatorPackage();
        ordered.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));
        Shipment failedPickup = customerReturn();
        failedPickup.setExternalId("21480004");
        failedPickup.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera"));
        RMA rma = rmaWith(ordered, failedPickup, failedCreation(false), failedCreation(true));
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");
        RmaShipmentsView view = factory.build(rma, false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then: the argument array is spread, never printed as one value
        assertThat(html).contains("<span class=\"cl-status is-ok\">Odbiór: czw. 8 paź, 9:00–17:00</span>")
                .contains("<span class=\"cl-status is-warn\">Nie udało się zamówić odbioru: Brak kuriera</span>")
                .contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Brak środków</span>")
                .doesNotContain("[Ljava")
                .contains("action=\"/dashboard/rma/rma-1/shipments/21480004/pickup\"")
                .contains(">Zamów odbiór ponownie</button>")
                .contains("action=\"/dashboard/rma/rma-1/shipments/creations/cmd-1/remove\"")
                .contains("href=\"/dashboard/rma/rma-1#pozycje\"")
                .contains("action=\"/dashboard/rma/rma-1/return-shipment/retry\"")
                .contains("href=\"/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/rma/rma-1\"")
                .doesNotContain("??");
    }

    @Test
    void aFailedCreationWithOurOwnCauseRendersLikeTheProvidersReasonAndAnUnconfirmedOneStaysASentence() {
        // given: one package POST /packages refused with a 503, one Furgonetka never confirmed
        Shipment notCreated = failedCreation(false);
        notCreated.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now())
                .failedWithKey("shipping.creation.notCreated"));
        Shipment unconfirmed = failedCreation(true);
        unconfirmed.setCreation(ShipmentCreationState.pending("cmd-2", LocalDateTime.now())
                .failedWithKey("shipping.creation.unconfirmed"));
        RmaShipmentsView view = factory.build(rmaWith(notCreated, unconfirmed), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        assertThat(html).contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Integracja wysyłki (Furgonetka) "
                        + "nie utworzyła paczki (brak odpowiedzi lub błąd po jej stronie). Nic nie zostało opłacone — spróbuj "
                        + "ponownie za chwilę.</span>")
                .contains("<span class=\"cl-status is-warn\">Integracja wysyłki (Furgonetka) nie potwierdziła nadania — "
                        + "sprawdź przesyłkę w jej panelu, zanim nadasz ponownie.</span>")
                .doesNotContain("Nie udało się nadać: Integracja wysyłki (Furgonetka) nie potwierdziła")
                .doesNotContain("{0}")
                .doesNotContain("??");
    }

    @Test
    void aReturnWhoseCourierTheCarrierBookedShowsItsNumberAndCarrierWithoutAStateOrALabel() {
        // given: the courier brings the printed label to the customer, so the shop has nothing to print or check
        Shipment booked = customerReturn();
        booked.setTrackingUrl("https://tracking.example/0000123");
        booked.setPickup(ShipmentPickup.bookedByCarrier("APP/CRIN/13023761"));
        RmaShipmentsView view = factory.build(rmaWith(booked), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        RmaShipmentsView.Row row = view.rows().get(0);
        assertThat(row.stateKey()).isNull();
        assertThat(row.labelHref()).isNull();
        assertOnlyEditable(row);
        assertThat(view.pickupHref()).isNull();
        assertThat(html).contains("href=\"https://tracking.example/0000123\"").contains(">0000123</a>")
                .contains("<span>· DPD</span>")
                .doesNotContain("cl-status")
                .doesNotContain("Pobierz etykietę")
                .doesNotContain("APP/CRIN/13023761");
    }

    @Test
    void aCustomerReturnWhosePickupIsOrderedOrNotNeededShowsNoState() {
        // given
        Shipment ordered = customerReturn();
        ordered.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));
        Shipment atPoint = customerReturn();
        atPoint.setPickup(ShipmentPickup.notRequired());

        // when
        RmaShipmentsView view = factory.build(rmaWith(ordered, atPoint), false, PL);

        // then
        assertThat(view.rows()).allSatisfy(row -> {
            assertThat(row.stateKey()).isNull();
            assertThat(row.labelHref()).isNull();
        });
    }

    @Test
    void aCustomerReturnWaitingForItsPickupShowsNoAwaitingState() {
        // when
        RmaShipmentsView.Row row = factory.build(rmaWith(customerReturn()), false, PL).rows().get(0);

        // then
        assertThat(row.stateKey()).isNull();
        assertThat(row.labelHref()).isNull();
    }

    @Test
    void aCustomerReturnWhosePickupFailedKeepsItsStateAndRetryButOffersNoLabel() {
        // given
        Shipment failed = customerReturn();
        failed.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera"));
        RmaShipmentsView view = factory.build(rmaWith(failed), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        assertThat(view.rows().get(0).labelHref()).isNull();
        assertThat(html).contains("<span class=\"cl-status is-warn\">Nie udało się zamówić odbioru: Brak kuriera</span>")
                .contains(">Zamów odbiór ponownie</button>")
                .doesNotContain("Pobierz etykietę");
    }

    @Test
    void aCustomerReturnBeingCreatedOrWhosePickupIsBeingOrderedKeepsItsInProgressState() {
        // given
        Shipment creating = new Shipment(ShipmentType.Courier);
        creating.setProvider("furgonetka");
        creating.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        Shipment ordering = customerReturn();
        ordering.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));

        // when
        RmaShipmentsView view = factory.build(rmaWith(creating, ordering), false, PL);

        // then
        assertThat(view.rows().get(0).stateKey()).isEqualTo("order.shipments.state.creating");
        assertThat(view.rows().get(1).stateKey()).isEqualTo("order.shipments.state.pickup.pending");
        assertThat(view.pollHref()).isEqualTo("/dashboard/rma/rma-1/shipments/state");
    }

    @Test
    void aFailedCustomerReturnRendersItsReasonWithRetryAndRemove() {
        // given
        RMA rma = rmaWith(failedCreation(true));
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");
        RmaShipmentsView view = factory.build(rma, false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        assertThat(html).contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Brak środków</span>")
                .contains("action=\"/dashboard/rma/rma-1/return-shipment/retry\"")
                .contains("action=\"/dashboard/rma/rma-1/shipments/creations/cmd-1/remove\"")
                .doesNotContain("Pobierz etykietę");
    }

    @Test
    void anOperatorShipmentKeepsItsLabelAndItsStateInTheCard() {
        // given: the shop prints the label of what it sends itself
        Shipment ordered = operatorPackage();
        ordered.setPickup(ShipmentPickup.pending("cmd-2", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));
        RmaShipmentsView view = factory.build(rmaWith(ordered), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        assertThat(html).contains("<span class=\"cl-status is-ok\">Odbiór: czw. 8 paź, 9:00–17:00</span>")
                .contains("href=\"/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/rma/rma-1\"")
                .contains("<span>Pobierz etykietę</span>");
    }

    @Test
    void theCarrierStateAndActionsSitInTheShipmentsRowOfAList() {
        // given
        Shipment failedPickup = customerReturn();
        failedPickup.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera"));
        RmaShipmentsView view = factory.build(rmaWith(failedPickup), false, PL);

        // when
        String html = SettingsTemplateRenderer.render(
                "<div th:replace=\"~{rma/detail/shipments :: card}\"></div>", Map.of("rmaShipments", view));

        // then
        String row = html.substring(html.indexOf("<li class=\"cl-list-item\""), html.lastIndexOf("</li>"));
        assertThat(html).doesNotContain("<table");
        assertThat(row).contains("<span>· DPD</span>").contains("cl-status is-warn").contains(">Zamów odbiór ponownie</button>");
    }

    @Test
    void rowsCarryTheirNumberFormattedDatesAndWhetherTheyCanBeEdited() {
        // given
        Shipment typed = new Shipment(ShipmentType.Courier);
        typed.setCarrier("InPost");
        typed.setTrackingNo("5201130293");
        typed.setShippedAt(LocalDateTime.of(2026, 10, 13, 8, 31));
        typed.setDeliveredAt(LocalDateTime.of(2026, 10, 14, 9, 55));
        RMA rma = rmaWith(typed, failedCreation(false));

        // when
        RmaShipmentsView open = factory.build(rma, false, PL);
        RmaShipmentsView closed = factory.build(rma, true, PL);

        // then: numbered from 1 over the whole list like the ?open=shipment-{n} dialogs; a shipment being created has nothing to edit
        RmaShipmentsView.Row first = open.rows().get(0);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.index()).isZero();
        assertThat(first.dialogId()).isEqualTo("shipment-dialog-1");
        assertThat(first.typeKey()).isEqualTo("ShipmentType.Courier");
        assertThat(first.shippedAt()).isEqualTo("13.10.2026");
        assertThat(first.deliveredAt()).isEqualTo("14.10.2026");
        assertThat(first.editable()).isTrue();
        assertThat(open.rows().get(1).number()).isEqualTo(2);
        assertThat(open.rows().get(1).shippedAt()).isNull();
        assertThat(open.rows().get(1).editable()).isFalse();
        assertThat(open.editable()).isTrue();
        assertThat(open.updateAction()).isEqualTo("/dashboard/rma/rma-1/updateShipments");
        assertThat(open.types()).containsExactly(ShipmentType.values());
        assertThat(closed.rows().get(0).editable()).isFalse();
        assertThat(closed.editable()).isFalse();
    }
}
