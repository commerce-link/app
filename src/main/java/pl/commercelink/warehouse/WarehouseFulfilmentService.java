package pl.commercelink.warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.warehouse.api.Reservation;
import pl.commercelink.warehouse.api.ReservationConfirmation;
import pl.commercelink.warehouse.api.ReservationItem;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class WarehouseFulfilmentService {

    @Autowired
    private Warehouse warehouse;

    public List<OrderItem> run(Order order, List<OrderItem> orderItems) {
        List<OrderItem> warehouseFulfillableItems = orderItems.stream().filter(OrderItem::isWarehouseFulfilled).toList();

        List<ReservationItem> reservationItems = warehouseFulfillableItems.stream()
                .map(i -> new ReservationItem(i.getItemId(), i.getManufacturerCode(), i.getQty()))
                .collect(Collectors.toList());

        Reservation reservation = Reservation.orderFulfilment(
                order.getStoreId(),
                order.getOrderId(),
                order.getBillingDetails(),
                order.getDocumentByType(DocumentType.Reservation),
                reservationItems
        );

        return process(
                order,
                warehouseFulfillableItems,
                warehouse.reservationService(order.getStoreId()).create(reservation)
        );
    }

    private List<OrderItem> process(Order order, List<OrderItem> orderItems, Reservation reservation) {
        order.addDocumentIfMissing(reservation.getDocument());

        List<OrderItem> fulfilledOrderItems = new LinkedList<>();
        for (OrderItem orderItem : orderItems) {

            ReservationItem reservationItem = reservation.findItemById(orderItem.getItemId());

            if (!reservationItem.hasConfirmations()) {
                orderItem.removeFulfilment();
                fulfilledOrderItems.add(orderItem);
                continue;
            }

            if (reservationItem.getRemainingQty() > 0) {
                fulfilledOrderItems.add(
                        new OrderItem(
                                orderItem.getOrderId(),
                                orderItem.getCategory(),
                                orderItem.getName(),
                                reservationItem.getRemainingQty(),
                                orderItem.getPrice(),
                                orderItem.getSku(),
                                orderItem.isConsolidated()
                        )
                );
            }

            List<ReservationConfirmation> confirmations = reservationItem.getConfirmations();

            orderItem.copyFulfilmentFrom(confirmations.get(0));
            fulfilledOrderItems.add(orderItem);

            for (int i = 1; i < confirmations.size(); i++) {
                fulfilledOrderItems.add(createOrderItemForReservation(orderItem, confirmations.get(i)));
            }
        }

        return fulfilledOrderItems;
    }

    private OrderItem createOrderItemForReservation(OrderItem orderItem, ReservationConfirmation confirmation) {
        OrderItem newItem = new OrderItem(
                orderItem.getOrderId(),
                orderItem.getCategory(),
                orderItem.getName(),
                confirmation.qty(),
                orderItem.getPrice(),
                orderItem.getSku(),
                orderItem.isConsolidated()
        );
        newItem.copyFulfilmentFrom(confirmation);
        return newItem;
    }

}
