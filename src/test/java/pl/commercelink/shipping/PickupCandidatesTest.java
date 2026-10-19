package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The number on the orders list's "Zamów odbiór": the packages the pickup page would list, from the list's orders. */
@ExtendWith(MockitoExtension.class)
class PickupCandidatesTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private RMARepository rmaRepository;

    private PickupCandidates candidates;

    @BeforeEach
    void setUp() {
        candidates = new PickupCandidates(ordersRepository, rmaRepository);
    }

    @Test
    void countsThePackagesOfOrdersAndRmasThatWaitForACourier() {
        // given
        Shipment failed = awaiting("3");
        failed.setPickup(ShipmentPickup.awaiting().failed("Brak kuriera"));
        Order first = order("order-1", awaiting("1"), failed);
        Order second = order("order-2", awaiting("2"));
        when(rmaRepository.findAllByStoreId("store-1")).thenReturn(List.of(rma(awaiting("4"))));

        // when
        int waiting = candidates.count("store-1", List.of(first, second));

        // then
        assertThat(waiting).isEqualTo(4);
        verify(ordersRepository, never()).findByStoreAndStatuses(anyString(), any());
    }

    @Test
    void packagesWhosePickupIsOrderedOrBeingOrderedAndCustomerReturnsAreNotCounted() {
        // given
        Shipment pending = awaiting("1");
        pending.setPickup(ShipmentPickup.pending("cmd-1", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)));
        Shipment ordered = awaiting("2");
        ordered.setPickup(ShipmentPickup.pending("cmd-1", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));
        Shipment customerReturn = awaiting("3");
        customerReturn.setPickUpAddressId(null);
        when(rmaRepository.findAllByStoreId("store-1")).thenReturn(List.of(rma(customerReturn)));

        // when
        int waiting = candidates.count("store-1", List.of(order("order-1", pending, ordered)));

        // then
        assertThat(waiting).isZero();
    }

    @Test
    void aPackageListedTwiceIsCountedOnce() {
        // given: every parcel row of a multi-parcel package carries the package's externalId
        Order order = order("order-1", awaiting("1"), awaiting("1"));
        when(rmaRepository.findAllByStoreId("store-1")).thenReturn(List.of());

        // when / then
        assertThat(candidates.count("store-1", List.of(order))).isEqualTo(1);
    }

    private static Shipment awaiting(String externalId) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo("T-" + externalId);
        s.setProvider("furgonetka");
        s.setCarrier("dpd");
        s.setPickUpAddressId("addr-1");
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    private static Order order(String orderId, Shipment... shipments) {
        Order order = new Order("store-1");
        order.setOrderId(orderId);
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    private static RMA rma(Shipment... shipments) {
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setShipments(new ArrayList<>(List.of(shipments)));
        return rma;
    }
}
