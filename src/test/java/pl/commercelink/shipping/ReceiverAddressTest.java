package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.api.ShipmentAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceiverAddressTest {

    private ShipmentAddress receiverFor(String pointCode) {
        return ShippingService.toReceiverAddress(buyerAddress(), pointCode);
    }

    private ShippingDetails buyerAddress() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Prosta 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setEmail("jan@example.com");
        details.setPhone("500600700");
        return details;
    }

    @Test
    void courierShipmentCarriesTheFullAddress() {
        // when
        ShipmentAddress receiver = receiverFor(null);

        // then
        assertEquals("Prosta 1", receiver.street());
        assertEquals("00-001", receiver.postcode());
        assertEquals("Warszawa", receiver.city());
    }

    @Test
    void pickupPointShipmentDropsTheStreetLevelAddress() {
        // when
        ShipmentAddress receiver = receiverFor("KRA01M");

        // then
        assertNull(receiver.street());
        assertNull(receiver.postcode());
        assertNull(receiver.city());
    }

    @Test
    void pickupPointShipmentKeepsWhoToNotify() {
        // when
        ShipmentAddress receiver = receiverFor("KRA01M");

        // then
        assertEquals("Jan Kowalski", receiver.name());
        assertEquals("jan@example.com", receiver.email());
        assertEquals("500600700", receiver.phone());
        assertEquals("PL", receiver.countryCode());
    }
}
