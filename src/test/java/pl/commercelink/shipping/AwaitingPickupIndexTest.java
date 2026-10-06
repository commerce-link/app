package pl.commercelink.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AwaitingPickupIndexTest {

    @Mock
    private AwaitingPickupsRepository repository;
    @InjectMocks
    private AwaitingPickupIndex index;

    private static Shipment shipment(String externalId, String trackingNo, ShipmentPickup pickup) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo(trackingNo);
        s.setCarrier("dpd");
        s.setProvider("furgonetka");
        s.setPickUpAddressId("addr-1");
        s.setPickup(pickup);
        return s;
    }

    @Test
    void oneEntryPerExternalId() {
        // given: two parcels of one package and one package handed in at a point
        List<Shipment> shipments = List.of(
                shipment("21480003", "A", ShipmentPickup.awaiting()),
                shipment("21480003", "B", ShipmentPickup.awaiting()),
                shipment("21480004", "C", ShipmentPickup.notRequired()));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AwaitingPickup>> saved = ArgumentCaptor.forClass(List.class);

        // when
        index.add("store-1", ShipmentOwnerType.ORDER, "order-1", shipments);

        // then
        verify(repository).batchSave(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        AwaitingPickup entry = saved.getValue().get(0);
        assertThat(entry.getExternalId()).isEqualTo("21480003");
        assertThat(entry.getTrackingNo()).isEqualTo("A");
        assertThat(entry.getOwnerType()).isEqualTo(ShipmentOwnerType.ORDER);
        assertThat(entry.getPickUpAddressId()).isEqualTo("addr-1");
    }

    @Test
    void shipmentsWithoutProviderOrAddressAreNotIndexed() {
        // given
        Shipment manual = shipment("1", "A", ShipmentPickup.awaiting());
        manual.setProvider(null);
        Shipment fromCustomer = shipment("2", "B", ShipmentPickup.awaiting());
        fromCustomer.setPickUpAddressId(null);

        // when
        index.add("store-1", ShipmentOwnerType.RMA, "rma-1", List.of(manual, fromCustomer));

        // then
        verify(repository, never()).batchSave(any());
    }

    @Test
    void aPackageWhoseCourierTheCarrierBookedIsNotIndexed() {
        // when
        index.add("store-1", ShipmentOwnerType.ORDER, "order-1",
                List.of(shipment("21486850", "A", ShipmentPickup.bookedByCarrier("APP/CRIN/13023761"))));

        // then
        verify(repository, never()).batchSave(any());
    }
}
