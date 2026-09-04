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
import pl.commercelink.orders.OrderItemFamily;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.rma.MarketplaceDecision;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Autowired
    private OrderItemFamily orderItemFamily;

    /**
     * Called after the warehouse accepted the items; every call is a separate (partial) refund with its
     * own commandId. Returns false when the decision was refused or could not be published.
     */
    public boolean returnAccepted(RMA rma, List<RMAItem> acceptedItems, boolean refundDelivery) {
        if (!rma.isMarketplaceReturn()) {
            return true;
        }
        if (rma.hasActionEvent(RMA.EVENT_REJECTION_SENT)) {
            LOGGER.warn("Refusing to refund RMA {}: a rejection was already sent to the marketplace", rma.getRmaId());
            return false;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot publish return acceptance for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return false;
        }

        Map<String, OrderItem> orderItemsById = orderItemsById(rma, order, acceptedItems);

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
        action.setExternalReturnReference(rma.getExternalReturnReference());

        // Persist the event and the resend payload BEFORE publishing. If the save happened after the
        // publish and then failed, a real refund would be in flight with no RefundRequested event and no
        // stored payload - every guard here would go blind and the resend button could not help. Publishing
        // after a successful save instead means a publish failure is exactly the case resend exists for.
        rma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);
        rememberAction(rma, OrderLifecycleEventType.ReturnAccepted, action);
        rmaRepository.save(rma);

        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnAccepted, action);
        return true;
    }

    /**
     * Order items by itemId, resolving across the split family (see {@link OrderItemFamily}) only when the
     * order's own items don't already cover every accepted item - the rare case, and the only one worth the
     * extra reads.
     */
    private Map<String, OrderItem> orderItemsById(RMA rma, Order order, List<RMAItem> rmaItems) {
        Map<String, OrderItem> orderItemsById = orderItemsRepository.findByOrderId(rma.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getItemId, Function.identity(), (first, second) -> first));
        boolean allResolved = rmaItems.stream().map(RMAItem::getItemId).allMatch(orderItemsById::containsKey);
        if (!allResolved) {
            for (OrderItem sibling : orderItemFamily.siblingItems(order)) {
                orderItemsById.putIfAbsent(sibling.getItemId(), sibling);
            }
        }
        return orderItemsById;
    }

    private static String refundKeyFor(RMAItem rmaItem, Map<String, OrderItem> orderItemsById) {
        OrderItem orderItem = orderItemsById.get(rmaItem.getItemId());
        if (orderItem != null) {
            return keyOf(orderItem);
        }
        String fallback = rmaItem.getMfn();
        if (isNotBlank(fallback)) {
            LOGGER.warn("Accepted RMA item {} has no matching order item; falling back to its stored mfn {}",
                    rmaItem.getRmaItemId(), fallback);
            return fallback;
        }
        // Collectors.groupingBy throws an NPE on a null key. Failing loudly here - after the warehouse
        // already accepted the items - is still better than silently dropping this item from the refund
        // sent to the marketplace, which would be a partial money movement nobody is told about.
        throw new IllegalStateException("Cannot determine a refund key for RMA item " + rmaItem.getRmaItemId()
                + ": it has no matching order item and no stored mfn");
    }

    public boolean returnRejected(RMA rma) {
        if (!rma.isMarketplaceReturn()) {
            return true;
        }
        if (rma.hasActionEvent(RMA.EVENT_REJECTION_SENT)) {
            return true;
        }
        if (rma.hasActionEvent(RMA.EVENT_REFUND_REQUESTED)) {
            // Mirrors the guard in returnAccepted: a refund and a rejection on the same RMA must never both
            // reach the marketplace - the buyer would keep the money and also get a rejection notice.
            LOGGER.warn("Refusing to reject RMA {}: a refund was already requested to the marketplace", rma.getRmaId());
            return false;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot publish return rejection for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return false;
        }
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), rma.getExternalReturnId(),
                List.of(), false, null, rma.getRejectionReason());

        // Persist first, publish second - see the comment in returnAccepted.
        rma.addActionEvent(RMA.EVENT_REJECTION_SENT);
        rememberAction(rma, OrderLifecycleEventType.ReturnRejected, action);
        rmaRepository.save(rma);

        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnRejected, action);
        return true;
    }

    private void rememberAction(RMA rma, OrderLifecycleEventType type, MarketplaceReturnAction action) {
        try {
            String payload = ACTION_MAPPER.writeValueAsString(action);
            rma.addMarketplaceDecision(new MarketplaceDecision(type.name(), action.getCommandId(), payload, LocalDateTime.now()));
        } catch (JsonProcessingException e) {
            // Never fail the operator's action because the resend record could not be stored.
            LOGGER.error("Could not store the marketplace decision for RMA {}", rma.getRmaId(), e);
        }
    }

    /**
     * Republishes every recorded decision with its original commandId. Allegro deduplicates refunds on
     * commandId and the rejection path gates on live state, so replaying rounds that already succeeded is
     * harmless - while a round that died in the DLQ is the one this exists for.
     */
    public boolean resendDecisions(RMA rma) {
        if (!rma.isMarketplaceReturn() || rma.getMarketplaceDecisions().isEmpty()) {
            return false;
        }
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot resend the marketplace decisions for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return false;
        }
        int published = 0;
        for (MarketplaceDecision decision : rma.getMarketplaceDecisions()) {
            try {
                MarketplaceReturnAction action = ACTION_MAPPER.readValue(decision.getPayload(), MarketplaceReturnAction.class);
                publisher.publishReturnAction(order, rma, OrderLifecycleEventType.valueOf(decision.getType()), action);
                published++;
            } catch (JsonProcessingException | IllegalArgumentException | NullPointerException e) {
                LOGGER.error("Could not resend marketplace decision {} for RMA {}", decision.getCommandId(), rma.getRmaId(), e);
            }
        }
        return published > 0;
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
                && existing.hasActionEvent(RMA.EVENT_REFUND_REQUESTED);
    }

    /**
     * True when the RMA items cover the full quantity of every non-service order item not yet
     * returned/replaced, across the whole split family (see {@link OrderItemFamily}): an item moved to a
     * split-off order is still part of "the whole order" for this purpose, and a split always leaves at
     * least one item on the parent, so checking only the parent's own items could never return true once an
     * order had been split.
     */
    public boolean coversWholeOrder(RMA rma, List<RMAItem> rmaItems) {
        Order order = ordersRepository.findById(rma.getStoreId(), rma.getOrderId());
        if (order == null) {
            LOGGER.warn("Cannot evaluate whole-order coverage for RMA {}: order {} not found", rma.getRmaId(), rma.getOrderId());
            return false;
        }
        List<OrderItem> orderItems = new ArrayList<>(orderItemsRepository.findByOrderId(rma.getOrderId()));
        orderItems.addAll(orderItemFamily.siblingItems(order));
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
        if (isNotBlank(orderItem.getExternalItemId())) {
            return orderItem.getExternalItemId();
        }
        if (isNotBlank(orderItem.getSku())) {
            return orderItem.getSku();
        }
        return orderItem.getManufacturerCode();
    }
}
