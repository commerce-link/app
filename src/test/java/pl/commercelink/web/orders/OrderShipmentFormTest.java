package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderShipmentFormTest {

    private static OrderShipmentForm posted(String shippedDate, String shippedTime) {
        return new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, "DPD", "T-1", null, null,
                shippedDate, shippedTime, null, null, List.of(), null, null);
    }

    @Test
    void aTimeIsTypedWithAColonOrADot() {
        // then
        assertThat(OrderShipmentForm.parseTime("14:30")).isEqualTo(LocalTime.of(14, 30));
        assertThat(OrderShipmentForm.parseTime(" 9.05 ")).isEqualTo(LocalTime.of(9, 5));
        assertThat(OrderShipmentForm.parseTime("24:00")).isNull();
        assertThat(OrderShipmentForm.parseTime("12:60")).isNull();
        assertThat(OrderShipmentForm.parseTime("1430")).isNull();
    }

    @Test
    void aTimeWithoutItsDateIsIgnoredAndAWrongOneIsNamed() {
        // when: the time field is prefilled, so clearing only the date must clear the moment
        OrderShipmentForm timeOnly = posted("", "10:00");
        OrderShipmentForm wrongTime = posted("2026-09-27", "noon");
        OrderShipmentForm wrongDate = posted("27.09.2026", null);

        // then
        assertThat(timeOnly.validate()).isEmpty();
        assertThat(timeOnly.toShipment(null).getShippedAt()).isNull();
        assertThat(wrongTime.validate()).containsEntry("shipment-0-shippedTime", "order.shipments.error.time");
        assertThat(wrongDate.validate()).containsEntry("shipment-0-shippedDate", "order.shipments.error.date");
        assertThat(posted("2026-09-27", "").validate()).isEmpty();
    }

    @Test
    void aNewShipmentNeedsMoreThanItsType() {
        // given
        OrderShipmentForm blank = new OrderShipmentForm("o-1", null, null, ShipmentType.PickupPoint, " ", null, null, null,
                null, "10:00", null, null, List.of(), null, null);
        OrderShipmentForm pointOnly = new OrderShipmentForm("o-1", null, null, ShipmentType.PickupPoint, null, null,
                "WAW01M", null, null, null, null, null, List.of(), null, null);

        // then
        assertThat(blank.validate()).containsEntry("shipment-new-trackingNo", "order.shipments.error.empty");
        assertThat(pointOnly.validate()).isEmpty();
    }

    @Test
    void theFormShowsTheSavedMomentAsADateAndATimeToTheMinute() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));

        // when
        OrderShipmentForm form = OrderShipmentForm.of("o-1", 2, shipment, List.of());

        // then
        assertThat(form.shippedDate()).isEqualTo("2026-09-27");
        assertThat(form.shippedTime()).isEqualTo("08:05");
        assertThat(form.number()).isEqualTo(3);
        assertThat(form.dialogId()).isEqualTo("shipment-dialog-2");
    }

    @Test
    void aTypedTimeReplacesTheSavedOneAndBlankTextFieldsAreStoredAsNothing() {
        // given
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setShippedAt(LocalDateTime.of(2026, 9, 27, 8, 5, 41));
        OrderShipmentForm form = new OrderShipmentForm("o-1", 0, "v", ShipmentType.Courier, " ", "T-1", "", " ",
                "2026-09-27", "16:45", null, null, List.of(), null, null);

        // when
        Shipment shipment = form.toShipment(saved);

        // then
        assertThat(shipment.getShippedAt()).isEqualTo(LocalDateTime.of(2026, 9, 27, 16, 45));
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
