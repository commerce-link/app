package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderReplaceShipmentsTest {

    private Order orderShippedTo(String collectionPointCode, String carrier) {
        Order order = new Order();
        Shipment imported = new Shipment(ShipmentType.PickupPoint);
        imported.setCollectionPoint(collectionPointCode != null ? new CollectionPoint(collectionPointCode) : null);
        imported.setCarrier(carrier);
        order.addShipment(imported);
        return order;
    }

    @Test
    void labelCreationKeepsTheCollectionPointOfTheReplacedShipment() {
        // given
        Order order = orderShippedTo("KRA01M", "INPOST");
        Shipment fromCarrier = new Shipment();
        fromCarrier.setCarrier("InPost");
        fromCarrier.setTrackingNo("6205123456");

        // when
        order.replaceShipments(List.of(fromCarrier));

        // then
        assertEquals("KRA01M", order.getShipments().get(0).getCollectionPoint().getCode());
        assertEquals(ShipmentType.PickupPoint, order.getShipments().get(0).getType());
        assertEquals("InPost", order.getShipments().get(0).getCarrier());
    }

    @Test
    void everyParcelOfAPickupPointOrderKeepsThePoint() {
        // given
        Order order = orderShippedTo("KRA01M", "INPOST");

        // when
        order.replaceShipments(List.of(new Shipment(), new Shipment()));

        // then
        assertEquals(2, order.getShipments().size());
        order.getShipments().forEach(shipment -> {
            assertEquals("KRA01M", shipment.getCollectionPoint().getCode());
            assertEquals(ShipmentType.PickupPoint, shipment.getType());
        });
    }

    @Test
    void cancellationKeepsTheCollectionPointForTheNextAttempt() {
        // given
        Order order = orderShippedTo("KRA01M", "INPOST");

        // when
        order.replaceShipments(List.of(new Shipment(ShipmentType.PickupPoint)));

        // then
        assertEquals("KRA01M", order.getShipments().get(0).getCollectionPoint().getCode());
        assertEquals("INPOST", order.getShipments().get(0).getCarrier());
    }

    @Test
    void courierOrderIsNotTurnedIntoAPickupPointOrder() {
        // given
        Order order = orderShippedTo(null, "DPD");
        order.getShipments().get(0).setType(ShipmentType.Courier);

        // when
        order.replaceShipments(List.of(new Shipment()));

        // then
        assertNull(order.getShipments().get(0).getCollectionPoint());
        assertEquals(ShipmentType.Courier, order.getShipments().get(0).getType());
    }

    @Test
    void personalCollectionKeepsItsOwnType() {
        // given
        Order order = orderShippedTo("KRA01M", null);

        // when
        order.replaceShipments(List.of(new Shipment(ShipmentType.PersonalCollection)));

        // then
        assertEquals(ShipmentType.PersonalCollection, order.getShipments().get(0).getType());
        assertEquals("KRA01M", order.getShipments().get(0).getCollectionPoint().getCode());
    }
}
