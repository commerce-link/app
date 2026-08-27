package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.MarketplaceReturnAction;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges operator decisions on a marketplace-originated RMA to the marketplace: publishes the
 * lifecycle event and records the decision in the RMA history. No-op for manual RMAs.
 */
@Component
public class MarketplaceReturnDecisions {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private OrderLifecycleEventPublisher publisher;

    /** Called after the warehouse accepted the items; every call is a separate (partial) refund with its own commandId. */
    public void returnAccepted(RMA rma, List<RMAItem> acceptedItems, boolean refundDelivery) {
        if (!rma.isMarketplaceReturn()) {
            return;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        List<MarketplaceReturnAction.Item> items = acceptedItems.stream()
                .map(i -> new MarketplaceReturnAction.Item(i.getMfn(), i.getQty()))
                .toList();
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), rma.getExternalReturnId(),
                items, refundDelivery, UUID.randomUUID().toString(), null);
        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnAccepted, action);
        rma.addEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUND_REQUESTED, LocalDateTime.now()));
        rmaRepository.save(rma);
    }

    public void returnRejected(RMA rma) {
        if (!rma.isMarketplaceReturn()) {
            return;
        }
        Event rejectionSent = new Event(EventType.action, MarketplaceReturnImporter.EVENT_REJECTION_SENT, LocalDateTime.now());
        if (rma.hasEvent(rejectionSent)) {
            return;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), rma.getExternalReturnId(),
                List.of(), false, null, rma.getRejectionReason());
        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnRejected, action);
        rma.addEvent(rejectionSent);
        rmaRepository.save(rma);
    }

    /** A marketplace rejection is shown to the buyer and must carry a reason; manual RMAs keep the old free-form rules. */
    public boolean requiresRejectionReason(RMA existing, RMAStatus newStatus, String reason) {
        boolean turnsRejected = newStatus == RMAStatus.Rejected && existing.getStatus() != RMAStatus.Rejected;
        return existing.isMarketplaceReturn() && turnsRejected && (reason == null || reason.isBlank());
    }

    /** True when the RMA items cover the full quantity of every order item not yet returned/replaced. */
    public boolean coversWholeOrder(RMA rma, List<RMAItem> rmaItems) {
        Map<String, Integer> returned = new HashMap<>();
        for (RMAItem item : rmaItems) {
            returned.merge(item.getMfn(), item.getQty(), Integer::sum);
        }
        for (OrderItem orderItem : orderItemsRepository.findByOrderId(rma.getOrderId())) {
            if (orderItem.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced)) {
                continue;
            }
            int covered = returned.getOrDefault(orderItem.getManufacturerCode(), 0);
            if (covered < orderItem.getQty()) {
                return false;
            }
            returned.put(orderItem.getManufacturerCode(), covered - orderItem.getQty());
        }
        return true;
    }
}
