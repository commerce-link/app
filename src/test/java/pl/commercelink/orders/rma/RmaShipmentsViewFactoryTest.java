package pl.commercelink.orders.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
        assertThat(row.retryHref()).isEqualTo("/dashboard/rma/rma-1#rmaItemsForm");
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
        assertThat(view.rows().get(0).hasActions()).isFalse();
    }

    @Test
    void theTableRendersTheFormattedStateAndTheActions() {
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
                "<div th:replace=\"~{fragments/rma-shipments :: table(${view})}\"></div>", Map.of("view", view));

        // then: the argument array is spread, never printed as one value
        assertThat(html).contains("<span class=\"cl-status is-ok\">Odbiór: czw. 8 paź, 9:00–17:00</span>")
                .contains("<span class=\"cl-status is-warn\">Nie udało się zamówić odbioru: Brak kuriera</span>")
                .contains("<span class=\"cl-status is-warn\">Nie udało się nadać: Brak środków</span>")
                .doesNotContain("[Ljava")
                .contains("action=\"/dashboard/rma/rma-1/shipments/21480004/pickup\"")
                .contains(">Zamów odbiór ponownie</button>")
                .contains("action=\"/dashboard/rma/rma-1/shipments/creations/cmd-1/remove\"")
                .contains("href=\"/dashboard/rma/rma-1#rmaItemsForm\"")
                .contains("action=\"/dashboard/rma/rma-1/return-shipment/retry\"")
                .contains("href=\"/dashboard/shipping/labels/furgonetka/21480003?back=/dashboard/rma/rma-1\"")
                .doesNotContain("??");
    }
}
