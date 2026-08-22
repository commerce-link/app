package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private WarehouseItem anAvailableItem(double unitCost) {
        WarehouseItem item = new WarehouseItem("store-1", "delivery-1", Categories.UNCATEGORIZED, "Widget", "5901234123457", "MFN-1", unitCost, 1);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }
}
