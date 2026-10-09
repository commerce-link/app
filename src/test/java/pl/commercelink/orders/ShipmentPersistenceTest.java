package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The shape a shipment's creation and pickup take in the Orders table, and that it reads back unchanged. */
class ShipmentPersistenceTest {

    private static final LocalDateTime SENT = LocalDateTime.of(2026, 10, 7, 10, 0);

    @Test
    void aPendingCreationIsStoredAsItsStatusAndCommand() {
        // given
        Shipment creating = new Shipment();
        creating.setCreation(ShipmentCreationState.pending("cmd-1", SENT));

        // when
        Map<String, AttributeValue> creation = shipmentItem(creating).get("creation").getM();

        // then
        assertThat(creation.keySet()).containsExactlyInAnyOrder("status", "command");
        assertThat(creation.get("status").getS()).isEqualTo("PENDING");
        assertThat(creation.get("command").getM().get("commandId").getS()).isEqualTo("cmd-1");
        assertThat(creation.get("command").getM()).containsKey("requestedAt").doesNotContainKeys("error", "errorKey");
    }

    @Test
    void anOrderedPickupIsStoredWithItsWindowAndCommand() {
        // given
        Shipment shipment = new Shipment();
        shipment.setPickup(ShipmentPickup.pending("pick-1", SENT, LocalDate.of(2026, 10, 8), LocalTime.of(9, 0),
                LocalTime.of(17, 0)).ordered("P-7"));

        // when
        Map<String, AttributeValue> pickup = shipmentItem(shipment).get("pickup").getM();

        // then
        assertThat(pickup.keySet()).containsExactlyInAnyOrder("status", "pickupId", "window", "command");
        assertThat(pickup.get("window").getM()).containsOnlyKeys("date", "from", "to");
        assertThat(pickup.get("window").getM().get("from").getS()).isEqualTo("09:00");
        assertThat(pickup.get("command").getM().get("commandId").getS()).isEqualTo("pick-1");
    }

    @Test
    void creationAndPickupReadBackUnchanged() {
        // given
        Shipment failed = new Shipment();
        failed.setCreation(ShipmentCreationState.pending("cmd-1", SENT).failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));
        Shipment refused = new Shipment();
        refused.setPickup(ShipmentPickup.pending("pick-1", SENT, LocalDate.of(2026, 10, 8), LocalTime.of(9, 0),
                LocalTime.of(17, 0)).failed("Brak kuriera"));
        Shipment booked = new Shipment();
        booked.setPickup(ShipmentPickup.bookedByCarrier("P-9"));

        // when
        List<Shipment> restored = roundTrip(failed, refused, booked);

        // then
        ShipmentCreationState creation = restored.get(0).getCreation();
        assertThat(creation.isFailed()).isTrue();
        assertThat(creation.hasCommand("cmd-1")).isTrue();
        assertThat(creation.getCommand().getRequestedAt()).isEqualTo(SENT);
        assertThat(creation.getCommand().getErrorKey()).isEqualTo(ShipmentCreationState.UNCONFIRMED_KEY);
        ShipmentPickup pickup = restored.get(1).getPickup();
        assertThat(pickup.isFailed()).isTrue();
        assertThat(pickup.getCommand().getError()).isEqualTo("Brak kuriera");
        assertThat(pickup.getWindow().getDate()).isEqualTo("2026-10-08");
        assertThat(pickup.getWindow().getTo()).isEqualTo("17:00");
        assertThat(restored.get(2).getPickup().isBookedByCarrier()).isTrue();
        assertThat(restored.get(2).getPickup().getPickupId()).isEqualTo("P-9");
    }

    private static Map<String, AttributeValue> shipmentItem(Shipment shipment) {
        return orderItem(shipment).get("shipments").getL().get(0).getM();
    }

    private static Map<String, AttributeValue> orderItem(Shipment... shipments) {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setShipments(List.of(shipments));
        return orderModel().convert(order);
    }

    private static List<Shipment> roundTrip(Shipment... shipments) {
        return orderModel().unconvert(orderItem(shipments)).getShipments();
    }

    private static DynamoDBMapperTableModel<Order> orderModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Order.class, DynamoDBMapperConfig.DEFAULT);
    }
}
