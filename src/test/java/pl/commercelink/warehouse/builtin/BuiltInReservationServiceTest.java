package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInReservationServiceTest {

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private WarehouseItemFactory warehouseItemFactory;

    @Test
    void carriesPurchaseCostOntoOrderItemInsteadOfUnitSystemCost() {
        // given
        WarehouseItem warehouseItem = anAvailableItem(20.0);
        warehouseItem.setUnitSystemCost(150.0);
        when(warehouseRepository.findAllByMfnAndStatus(any(), any(), any())).thenReturn(List.of(warehouseItem));

        ReservationItem reservationItem = new ReservationItem(warehouseItem.getItemId(), warehouseItem.getManufacturerCode(), 1);
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        OrderItem orderItem = new OrderItem();

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);
        orderItem.copyFulfilmentFrom(reservationItem.getConfirmations().get(0));

        // then
        assertEquals(20.0, orderItem.getCost());
    }

    @Test
    void reservesExactlyTheRequestedWarehouseItemInsteadOfFifoStock() {
        // given
        WarehouseItem requested = anAvailableItem(20.0);
        when(warehouseRepository.findById("store-1", requested.getItemId())).thenReturn(requested);

        ReservationItem reservationItem = new ReservationItem("order-item-1", "MFN-1", 1, requested.getItemId());
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);

        // then
        assertEquals(1, reservationItem.getConfirmations().size());
        assertEquals(0, requested.getQty());
        verify(warehouseRepository).delete(requested);
        verify(warehouseRepository, never()).findAllByMfnAndStatus(any(), any(), any());
    }

    @Test
    void leavesItemUnfulfilledWhenRequestedWarehouseItemIsNoLongerAvailable() {
        // given
        when(warehouseRepository.findById("store-1", "missing-item")).thenReturn(null);

        ReservationItem reservationItem = new ReservationItem("order-item-1", "MFN-1", 1, "missing-item");
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);

        // then
        assertFalse(reservationItem.hasConfirmations());
        verify(warehouseRepository, never()).findAllByMfnAndStatus(any(), any(), any());
    }

    @Test
    void reservesOnlyAvailableQuantityOfTheRequestedWarehouseItem() {
        // given
        WarehouseItem requested = anAvailableItem(20.0);
        requested.setQty(2);
        when(warehouseRepository.findById("store-1", requested.getItemId())).thenReturn(requested);

        ReservationItem reservationItem = new ReservationItem("order-item-1", "MFN-1", 5, requested.getItemId());
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);

        // then
        assertEquals(2, reservationItem.getConfirmations().get(0).qty());
        assertEquals(3, reservationItem.getRemainingQty());
        verify(warehouseRepository, never()).findAllByMfnAndStatus(any(), any(), any());
    }

    @Test
    void skipsNotSealedItemsWhenReservingByMfn() {
        // given
        WarehouseItem openBox = anAvailableItem(20.0);
        openBox.setCondition(ItemCondition.OpenBox);
        when(warehouseRepository.findAllByMfnAndStatus(any(), any(), any())).thenReturn(List.of(openBox));

        ReservationItem reservationItem = new ReservationItem("order-item-1", "MFN-1", 1);
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);

        // then
        assertFalse(reservationItem.hasConfirmations());
        assertEquals(1, openBox.getQty());
    }

    @Test
    void reservesNotSealedItemWhenRequestedExplicitly() {
        // given
        WarehouseItem openBox = anAvailableItem(20.0);
        openBox.setCondition(ItemCondition.OpenBox);
        when(warehouseRepository.findById("store-1", openBox.getItemId())).thenReturn(openBox);

        ReservationItem reservationItem = new ReservationItem("order-item-1", "MFN-1", 1, openBox.getItemId());
        Reservation reservation = Reservation.internalUse("store-1", List.of(reservationItem));

        // when
        new BuiltInReservationService(deliveriesRepository, warehouseRepository, warehouseItemFactory).create(reservation);

        // then
        assertEquals(1, reservationItem.getConfirmations().size());
        assertEquals(0, openBox.getQty());
    }

    private WarehouseItem anAvailableItem(double unitCost) {
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", unitCost, 1);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
