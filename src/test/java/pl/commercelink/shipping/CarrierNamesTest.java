package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierNamesTest {

    @Test
    void knownCodesGetTheirNamesRegardlessOfCase() {
        // given
        Shipment lower = shipment("dhl", null);
        Shipment upper = shipment("DHL", null);
        Shipment kurier = shipment("inpostpaczkakurierska", null);

        // when / then
        assertThat(CarrierNames.forCustomer(lower)).isEqualTo("DHL");
        assertThat(CarrierNames.forCustomer(upper)).isEqualTo("DHL");
        assertThat(CarrierNames.forCustomer(kurier)).isEqualTo("InPost Kurier");
    }

    @Test
    void unknownCodeStaysAsStored() {
        // given
        Shipment shipment = shipment("my", null);

        // when
        String name = CarrierNames.forCustomer(shipment);

        // then
        assertThat(name).isEqualTo("my");
    }

    @Test
    void blankCarrierGivesNull() {
        // given
        Shipment shipment = shipment(" ", null);

        // when / then
        assertThat(CarrierNames.forCustomer(shipment)).isNull();
    }

    @Test
    void allegroShipmentUsesAllegroNames() {
        // given
        Shipment shipment = shipment("ALLEGRO_ONE_KURIER", ShippingIntegrationChoice.ALLEGRO);

        // when
        String name = CarrierNames.forCustomer(shipment);

        // then
        assertThat(name).isEqualTo("One by Allegro");
    }

    @Test
    void allegroShipmentWithAPlainCarrierCodeFallsBackToTheKnownNames() {
        // given
        Shipment shipment = shipment("dhl", ShippingIntegrationChoice.ALLEGRO);

        // when
        String name = CarrierNames.forCustomer(shipment);

        // then
        assertThat(name).isEqualTo("DHL");
    }

    private static Shipment shipment(String carrier, String provider) {
        Shipment shipment = new Shipment();
        shipment.setCarrier(carrier);
        shipment.setProvider(provider);
        return shipment;
    }
}
