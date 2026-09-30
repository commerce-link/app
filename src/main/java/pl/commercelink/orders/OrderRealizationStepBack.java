package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;

import java.time.LocalDateTime;

/**
 * An operator's correction that leaves a Shipping order with nothing shipped (the last shipped shipment removed, its
 * shipped date cleared, the courier order cancelled) takes the order back to Realization (the user's decision of
 * 2026-09-30, Order#returnToRealizationWhenNothingShipped). The step back is recorded as an action event, which the
 * order history shows and OrderNotificationsService reads: the customer, already told the order went out, is not sent
 * "Zamówienie w realizacji" for it (controller ruling of 2026-09-30).
 */
@Component
@RequiredArgsConstructor
public class OrderRealizationStepBack {

    public static final String EVENT = "RETURNED_TO_REALIZATION";

    private final OrderEventsRepository orderEventsRepository;

    /** Takes the order back when nothing is shipped any more; returns whether it did. Call before saving the order. */
    public boolean apply(Order order) {
        if (!order.returnToRealizationWhenNothingShipped()) {
            return false;
        }
        // recorded before the order is saved: the notification the save publishes must already see it
        orderEventsRepository.save(new OrderEvent(order.getOrderId(), EventType.action, EVENT, LocalDateTime.now()));
        return true;
    }
}
