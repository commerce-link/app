package pl.commercelink.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Bridges operator decisions on a marketplace-originated RMA to the marketplace: publishes the
 * lifecycle event and records the decision in the RMA history. No-op for manual RMAs.
 */
@Component
public class MarketplaceReturnDecisions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceReturnDecisions.class);

    private static final int MAX_REJECTION_REASON_LENGTH = 250;

    private static final ObjectMapper ACTION_MAPPER = new ObjectMapper();

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
        if (rma.hasEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REJECTION_SENT, null))) {
            LOGGER.warn("Refusing to refund RMA {}: a rejection was already sent to the marketplace", rma.getRmaId());
            return;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot publish return acceptance for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return;
        }

        Map<String, OrderItem> orderItemsById = orderItemsRepository.findByOrderId(rma.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getItemId, Function.identity(), (first, second) -> first));

        // Two RMA items can point at one OrderItem (item split), and two order items can share a key
        // (multi-batch fulfilment). Allegro must receive one entry per line item, so merge by key.
        List<MarketplaceReturnAction.Item> items = acceptedItems.stream()
                .collect(Collectors.groupingBy(i -> refundKeyFor(i, orderItemsById),
                        LinkedHashMap::new, Collectors.summingInt(RMAItem::getQty)))
                .entrySet().stream()
                .map(e -> new MarketplaceReturnAction.Item(e.getKey(), e.getValue()))
                .toList();
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), rma.getExternalReturnId(),
                items, refundDelivery, UUID.randomUUID().toString(), null);
        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnAccepted, action);
        rememberAction(rma, OrderLifecycleEventType.ReturnAccepted, action);
        rma.addEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUND_REQUESTED, LocalDateTime.now()));
        rmaRepository.save(rma);
    }

    private static String refundKeyFor(RMAItem rmaItem, Map<String, OrderItem> orderItemsById) {
        OrderItem orderItem = orderItemsById.get(rmaItem.getItemId());
        if (orderItem == null) {
            LOGGER.warn("Accepted RMA item {} has no matching order item; falling back to its stored mfn {}",
                    rmaItem.getRmaItemId(), rmaItem.getMfn());
            return rmaItem.getMfn();
        }
        return keyOf(orderItem);
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
        if (order == null) {
            LOGGER.warn("Cannot publish return rejection for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return;
        }
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), rma.getExternalReturnId(),
                List.of(), false, null, rma.getRejectionReason());
        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnRejected, action);
        rememberAction(rma, OrderLifecycleEventType.ReturnRejected, action);
        rma.addEvent(rejectionSent);
        rmaRepository.save(rma);
    }

    private void rememberAction(RMA rma, OrderLifecycleEventType type, MarketplaceReturnAction action) {
        try {
            rma.setMarketplaceActionType(type.name());
            rma.setMarketplaceActionPayload(ACTION_MAPPER.writeValueAsString(action));
        } catch (JsonProcessingException e) {
            // Never fail the operator's action because the resend hint could not be stored.
            LOGGER.warn("Could not store the marketplace action for RMA {}", rma.getRmaId(), e);
        }
    }

    /** Republishes the last decision with its original commandId; Allegro deduplicates on it. */
    public boolean resendLastDecision(RMA rma) {
        if (!rma.isMarketplaceReturn() || rma.getMarketplaceActionPayload() == null) {
            return false;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot resend the marketplace decision for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return false;
        }
        try {
            MarketplaceReturnAction action =
                    ACTION_MAPPER.readValue(rma.getMarketplaceActionPayload(), MarketplaceReturnAction.class);
            publisher.publishReturnAction(order, rma,
                    OrderLifecycleEventType.valueOf(rma.getMarketplaceActionType()), action);
            return true;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOGGER.warn("Could not resend the marketplace decision for RMA {}", rma.getRmaId(), e);
            return false;
        }
    }

    /** A marketplace rejection is shown to the buyer and must carry a reason (1-250 chars); manual RMAs keep the old free-form rules. */
    public boolean requiresRejectionReason(RMA existing, RMAStatus newStatus, String reason) {
        boolean turnsRejected = newStatus == RMAStatus.Rejected && existing.getStatus() != RMAStatus.Rejected;
        return existing.isMarketplaceReturn() && turnsRejected
                && (reason == null || reason.isBlank() || reason.length() > MAX_REJECTION_REASON_LENGTH);
    }

    /** A refunded return must not also be rejected: the buyer would keep the money and get a rejection notice. */
    public boolean blocksRejectionAfterRefund(RMA existing, RMAStatus newStatus) {
        boolean turnsRejected = newStatus == RMAStatus.Rejected && existing.getStatus() != RMAStatus.Rejected;
        return existing.isMarketplaceReturn() && turnsRejected
                && existing.hasEvent(new Event(EventType.action, MarketplaceReturnImporter.EVENT_REFUND_REQUESTED, null));
    }

    /** True when the RMA items cover the full quantity of every non-service order item not yet returned/replaced. */
    public boolean coversWholeOrder(RMA rma, List<RMAItem> rmaItems) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(rma.getOrderId());
        Map<String, OrderItem> orderItemsById = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getItemId, Function.identity(), (first, second) -> first));

        Map<String, Integer> returned = new HashMap<>();
        for (RMAItem item : rmaItems) {
            OrderItem orderItem = orderItemsById.get(item.getItemId());
            String key = orderItem != null ? keyOf(orderItem) : item.getMfn();
            returned.merge(key, item.getQty(), Integer::sum);
        }

        for (OrderItem orderItem : orderItems) {
            if (orderItem.isService()) {
                continue;
            }
            if (orderItem.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced)) {
                continue;
            }
            String key = keyOf(orderItem);
            int covered = returned.getOrDefault(key, 0);
            if (covered < orderItem.getQty()) {
                return false;
            }
            returned.put(key, covered - orderItem.getQty());
        }
        return true;
    }

    private static String keyOf(OrderItem orderItem) {
        String externalItemId = orderItem.getExternalItemId();
        return isNotBlank(externalItemId) ? externalItemId : orderItem.getManufacturerCode();
    }
}
