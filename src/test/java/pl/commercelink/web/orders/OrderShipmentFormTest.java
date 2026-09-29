package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderShipmentFormTest {

    // a day after every date the tests type, so "today" is never one of them unless a test says so
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 15, 42, 7, 500_000_000);

    private static OrderShipmentForm posted(String shippedDate) {
        return new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-1", null, null,
                shippedDate, null, List.of(), null, null);
    }

    @Test
    void aBlankDateClearsTheMomentAndAWrongOneIsNamed() {
        // given
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));

        // when
        OrderShipmentForm blank = posted("");
        OrderShipmentForm wrongDate = posted("27.09.2026");

        // then
        assertThat(blank.validate()).isEmpty();
        assertThat(blank.toShipment(saved).getShippedAt()).isNull();
        assertThat(wrongDate.validate()).containsEntry("shipment-0-shippedDate", "order.shipments.error.date");
        assertThat(posted("2026-09-27").validate()).isEmpty();
    }

    @Test
    void aNewShipmentNeedsMoreThanItsType() {
        // given
        OrderShipmentForm blank = new OrderShipmentForm("o-1", null, null, ShipmentType.PickupPoint, " ", null, null, null,
                null, null, List.of(), null, null);
        OrderShipmentForm pointOnly = new OrderShipmentForm("o-1", null, null, ShipmentType.PickupPoint, null, null,
                "WAW01M", null, null, null, List.of(), null, null);

        // then
        assertThat(blank.validate()).containsEntry("shipment-new-trackingNo", "order.shipments.error.empty");
        assertThat(pointOnly.validate()).isEmpty();
    }

    @Test
    void theFormShowsTheSavedMomentAsADateOnly() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));

        // when
        OrderShipmentForm form = OrderShipmentForm.of("o-1", 2, shipment, List.of());

        // then
        assertThat(form.shippedDate()).isEqualTo("2026-09-27");
        assertThat(form.deliveredDate()).isNull();
        assertThat(form.number()).isEqualTo(3);
        assertThat(form.dialogId()).isEqualTo("shipment-dialog-2");
    }

    @Test
    void theSavedDateKeepsItsSavedTimeToTheSecond() {
        // given: the tracking stored the moment to the second
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));
        saved.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 13, 20));
        OrderShipmentForm form = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-1", null, null,
                "2026-09-27", "2026-09-28", List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(saved);

        // then
        assertThat(shipment.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 8, 5, 41));
        assertThat(shipment.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 13, 20));
    }

    @Test
    void todayIsSavedAsNowSoItNeverFallsBeforeSomethingSwitchedOnEarlierToday() {
        // given: delivered today, typed by hand into a new shipment; shipped yesterday
        OrderShipmentForm form = new OrderShipmentForm("o-1", null, null, ShipmentType.Courier, "DPD", "T-1", null, null,
                "2026-09-29", "2026-09-30", List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(null, NOW);

        // then
        assertThat(shipment.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 0, 0));
        assertThat(shipment.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 15, 42, 7));
    }

    @Test
    void aChangedOrNewDateStartsAtMidnightAndBlankTextFieldsAreStoredAsNothing() {
        // given
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));
        OrderShipmentForm form = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, " ", "T-1", "", " ",
                "2026-09-26", "2026-09-29", List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(saved, NOW);

        // then
        assertThat(shipment.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 26, 0, 0));
        assertThat(shipment.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 0, 0));
        assertThat(shipment.getCarrier()).isNull();
        assertThat(shipment.getCollectionPointCode()).isNull();
        assertThat(shipment.getTrackingUrl()).isNull();
    }

    @Test
    void theVersionChangesWithAnyShownFieldButNotWithSeconds() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));
        String version = OrderShipmentForm.version(shipment);

        // when
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 3));
        String sameMinute = OrderShipmentForm.version(shipment);
        shipment.setDeliveredAt(LocalDateTime.of(2026, 9, 28, 9, 0));

        // then
        assertThat(sameMinute).isEqualTo(version);
        assertThat(OrderShipmentForm.version(shipment)).isNotEqualTo(version);
    }
}
