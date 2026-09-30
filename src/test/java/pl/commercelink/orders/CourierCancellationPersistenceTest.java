package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CourierCancellationPersistenceTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void failedCancellationPersistsAsOneNestedMapAndRestoresEqual() {
        // given
        Shipment shipment = courierShipment();
        shipment.setCancellation(CourierCancellation.pending("cmd-1", REQUESTED).failed());
        Order order = orderWith(shipment);

        // when
        DynamoDBMapperTableModel<Order> model = orderModel();
        Map<String, AttributeValue> attributes = model.convert(order);
        Order restored = model.unconvert(attributes);

        // then
        Map<String, AttributeValue> stored = shipmentAttributes(attributes);
        assertThat(stored).doesNotContainKeys("cancellationStatus", "cancellationCommandId", "cancellationError",
                "cancellationRequestedAt");
        Map<String, AttributeValue> cancellation = stored.get("cancellation").getM();
        assertThat(cancellation).containsOnlyKeys("status", "commandId", "requestedAt");
        assertThat(cancellation.get("status").getS()).isEqualTo("FAILED");
        assertThat(cancellation.get("commandId").getS()).isEqualTo("cmd-1");
        assertThat(cancellation.get("requestedAt").getS())
                .isEqualTo(new DynamoDbLocalDateTimeConverter().convert(REQUESTED));
        assertThat(restored.getShipments().get(0).getCancellation()).isEqualTo(shipment.getCancellation());
    }

    @Test
    void shipmentWithoutACancellationHasNoCancellationAttribute() {
        // given
        Order order = orderWith(courierShipment());

        // when
        DynamoDBMapperTableModel<Order> model = orderModel();
        Map<String, AttributeValue> attributes = model.convert(order);
        Order restored = model.unconvert(attributes);

        // then
        assertThat(shipmentAttributes(attributes)).doesNotContainKey("cancellation");
        assertThat(restored.getShipments().get(0).getCancellation()).isNull();
    }

    private static Shipment courierShipment() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setExternalId("PKG-1");
        return shipment;
    }

    private static Order orderWith(Shipment shipment) {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setShipments(new ArrayList<>(List.of(shipment)));
        return order;
    }

    private static Map<String, AttributeValue> shipmentAttributes(Map<String, AttributeValue> order) {
        return order.get("shipments").getL().get(0).getM();
    }

    private static DynamoDBMapperTableModel<Order> orderModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(Order.class, DynamoDBMapperConfig.DEFAULT);
    }
}
