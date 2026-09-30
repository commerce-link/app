package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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

    // 2026-09-29 15:00 in Warsaw
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T13:00:00Z"), ZoneOffset.UTC);

    private static OrderShipmentForm dated(String shippedDate, String deliveredDate) {
        return new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-1", null, null,
                shippedDate, deliveredDate, List.of(), null, null).withClock(CLOCK);
    }

    private static Shipment courierOrder() {
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setCarrier("DPD");
        saved.setTrackingNo("T-1");
        saved.setExternalId("EXT-1");
        saved.markTrackingActive("21037943");
        return saved;
    }

    @Test
    void editingTheTrackingNumberKeepsTheCourierOrder() {
        // given: whatever reaches toShipment, the paid label at the carrier must not be lost with a changed number
        Shipment saved = courierOrder();
        OrderShipmentForm form = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-2", null, null,
                null, null, List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(saved);

        // then: the courier order stays, the tracking subscription belonged to the old number
        assertThat(shipment.getExternalId()).isEqualTo("EXT-1");
        assertThat(shipment.getTrackingSubscriptionStatus()).isNull();
    }

    @Test
    void editKeepsThePendingCancellationOfTheCourierOrder() {
        // given: the operator edits the shipment while Furgonetka is still cancelling its courier order
        Shipment saved = courierOrder();
        saved.markCancellationPending("cmd-1", NOW.minusSeconds(20));
        OrderShipmentForm form = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-1", null, null,
                null, null, List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(saved, NOW);

        // then: the cancellation check must still find the shipment
        assertThat(shipment.getExternalId()).isEqualTo("EXT-1");
        assertThat(shipment.isCancellationPending()).isTrue();
        assertThat(shipment.hasCancellationCommand("cmd-1")).isTrue();
    }

    @Test
    void changingTheCarrierOfACourierShipmentIsAFieldError() {
        // given
        Shipment saved = courierOrder();
        OrderShipmentForm carrierChanged = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "GLS", "T-1",
                null, null, null, null, List.of(), null, null);
        OrderShipmentForm numberCleared = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", " ",
                null, null, null, null, List.of(), null, null);
        OrderShipmentForm urlAdded = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, " DPD ", "T-1",
                null, "https://tracking.example/T-1", null, null, List.of(), null, null);

        // when / then
        assertThat(carrierChanged.validate(saved)).containsOnly(
                Map.entry("shipment-0-carrier", "order.shipments.error.courierLocked"));
        assertThat(numberCleared.validate(saved)).containsOnly(
                Map.entry("shipment-0-trackingNo", "order.shipments.error.courierLocked"));
        OrderShipmentForm typeChanged = new OrderShipmentForm("o-1", 0, "v", ShipmentType.PersonalCollection, "DPD",
                "T-1", null, null, null, null, List.of(), null, null);
        assertThat(urlAdded.validate(saved)).isEmpty();
        assertThat(typeChanged.validate(saved)).containsOnly(
                Map.entry("shipment-0-type", "order.shipments.error.courierLocked"));
        assertThat(OrderShipmentForm.of("o-1", 0, saved, List.of()).courierOrder()).isTrue();
    }

    @Test
    void aDeliveryDateInTheFutureIsAFieldError() {
        // when
        Map<String, String> tomorrow = dated("2026-09-29", "2026-09-30").validate();
        Map<String, String> shippedTomorrow = dated("2026-09-30", null).validate();
        Map<String, String> today = dated("2026-09-29", "2026-09-29").validate();

        // then
        assertThat(tomorrow).containsOnly(Map.entry("shipment-0-deliveredDate", "order.shipments.error.future"));
        assertThat(shippedTomorrow).containsOnly(Map.entry("shipment-0-shippedDate", "order.shipments.error.future"));
        assertThat(today).isEmpty();
    }

    @Test
    void aDeliveryDateBeforeTheShippedDateIsAFieldError() {
        // when
        Map<String, String> before = dated("2026-09-28", "2026-09-27").validate();
        Map<String, String> sameDay = dated("2026-09-28", "2026-09-28").validate();
        Map<String, String> deliveredOnly = dated(null, "2026-09-27").validate();

        // then
        assertThat(before).containsOnly(
                Map.entry("shipment-0-deliveredDate", "order.shipments.error.deliveredBeforeShipped"));
        assertThat(sameDay).isEmpty();
        assertThat(deliveredOnly).isEmpty();
    }

    @Test
    void todayIsTheWarsawDayOnAServerRunningInUtc() {
        // given: 23:30 UTC on the 29th is 01:30 on the 30th in Warsaw
        Clock utcLateEvening = Clock.fixed(Instant.parse("2026-09-29T23:30:00Z"), ZoneOffset.UTC);
        OrderShipmentForm form = new OrderShipmentForm("o-1", null, null, ShipmentType.Courier, "DPD", "T-1", null, null,
                "2026-09-30", "2026-09-30", List.of(), null, null).withClock(utcLateEvening);

        // when
        Map<String, String> errors = form.validate();
        Shipment shipment = form.toShipment(null);

        // then: the operator's today is offered, accepted and saved as the moment it is recorded
        assertThat(form.today()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(errors).isEmpty();
        assertThat(shipment.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 1, 30));
    }
}
