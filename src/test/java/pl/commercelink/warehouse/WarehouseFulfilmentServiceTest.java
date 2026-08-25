package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationConfirmation;
import pl.commercelink.warehouse.api.ReservationItem;
import pl.commercelink.warehouse.api.ReservationService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseFulfilmentServiceTest {

    @Mock
    private Warehouse warehouse;

    @Mock
    private ReservationService reservationService;

    @Mock
    private Order order;

    @InjectMocks
    private WarehouseFulfilmentService warehouseFulfilmentService;

    @Test
    void overwritesInventoryPriceWithPurchaseCostOfTheReservedItem() {
        // given
        OrderItem orderItem = new OrderItem("order-1", Categories.UNCATEGORIZED, "Widget", 1, 199.0, "SKU-1", false);
        orderItem.setManufacturerCode("MFN-1");
        orderItem.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        orderItem.setCost(150.0);

        when(order.getStoreId()).thenReturn("store-1");
        when(order.getDocumentByType(DocumentType.Reservation)).thenReturn(Optional.empty());
        when(warehouse.reservationService("store-1")).thenReturn(reservationService);
        when(reservationService.create(any())).thenAnswer(invocation -> confirmAt(invocation.getArgument(0), 20.0));

        // when
        List<OrderItem> fulfilledItems = warehouseFulfilmentService.run(order, List.of(orderItem));

        // then
        assertEquals(1, fulfilledItems.size());
        assertEquals(20.0, fulfilledItems.get(0).getCost());
    }

    @Test
    void passesRequestedWarehouseItemIdToTheReservation() {
        // given
        OrderItem orderItem = new OrderItem("order-1", Categories.UNCATEGORIZED, "Widget", 1, 199.0, "SKU-1", false);
        orderItem.setManufacturerCode("MFN-1");
        orderItem.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        orderItem.requestWarehouseItem("warehouse-item-1");

        when(order.getStoreId()).thenReturn("store-1");
        when(order.getDocumentByType(DocumentType.Reservation)).thenReturn(Optional.empty());
        when(warehouse.reservationService("store-1")).thenReturn(reservationService);
        when(reservationService.create(any())).thenAnswer(invocation -> confirmAt(invocation.getArgument(0), 20.0));

        // when
        warehouseFulfilmentService.run(order, List.of(orderItem));

        // then
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationService).create(captor.capture());
        assertEquals("warehouse-item-1", captor.getValue().getItems().get(0).getWarehouseItemId());
    }

    private Reservation confirmAt(Reservation reservation, double unitCost) {
        ReservationItem reservationItem = reservation.getItems().get(0);
        reservationItem.add(new ReservationConfirmation(
                "delivery-1",
                "5901234123457",
                "MFN-1",
                Price.fromNet(unitCost),
                reservationItem.getRemainingQty(),
                true,
                null
        ));
        return reservation;
    }
}
