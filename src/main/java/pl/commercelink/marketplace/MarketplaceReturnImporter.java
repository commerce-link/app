package pl.commercelink.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Turns marketplace customer returns into RMAs. Idempotent by (storeId, externalReturnId): a known return only
 * has its marketplace status refreshed; an unknown open return creates an RMA in WaitingForItems (the buyer
 * ships the parcel on their own, so the label/approval step of the manual flow is skipped).
 */
@Component
public class MarketplaceReturnImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketplaceReturnImporter.class);

    public static final String EVENT_REFUND_REQUESTED = "RefundRequested";
    public static final String EVENT_REJECTION_SENT = "RejectionSent";
    public static final String EVENT_REFUNDED_BY_MARKETPLACE = "RefundedByMarketplace";

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private StoresRepository storesRepository;

    public void importReturn(Store store, String marketplace, MarketplaceReturn ret) {
        RMA existing = rmaRepository.findByExternalReturnId(store.getStoreId(), ret.externalReturnId());
        if (existing != null) {
            refreshExternalStatus(store, marketplace, existing, ret);
            return;
        }
        if (ret.status().isClosed()) {
            LOGGER.info("Skipping closed {} return {} without an RMA in store {}", marketplace, ret.externalReturnId(),
                    store.getStoreId());
            return;
        }
        createRma(store, marketplace, ret);
    }

    private void refreshExternalStatus(Store store, String marketplace, RMA rma, MarketplaceReturn ret) {
        if (rma.getExternalReturnStatus() == ret.status()) {
            return;
        }
        rma.setExternalReturnStatus(ret.status());
        if (ret.status() == MarketplaceReturnStatus.REFUNDED && !appDecidedRefund(rma)) {
            Event refundedByMarketplace = new Event(EventType.action, EVENT_REFUNDED_BY_MARKETPLACE, LocalDateTime.now());
            if (!rma.hasEvent(refundedByMarketplace)) {
                rma.addEvent(refundedByMarketplace);
                store.getNotifications().add(new StoreNotification(
                        StoreNotificationSeverity.WARNING,
                        StoreNotificationType.MARKETPLACE_RETURN_REFUNDED,
                        rma.getRmaId(),
                        marketplace + " refunded the buyer for return " + referenceOf(rma)
                                + " without a decision in the application"));
                storesRepository.save(store);
            }
        }
        rmaRepository.save(rma);
    }

    private static boolean appDecidedRefund(RMA rma) {
        return rma.hasEvent(new Event(EventType.action, EVENT_REFUND_REQUESTED, null))
                || rma.hasEvent(new Event(EventType.action, EVENT_REFUNDED_BY_MARKETPLACE, null));
    }

    private static String referenceOf(RMA rma) {
        return rma.getExternalReturnReference() != null ? rma.getExternalReturnReference() : rma.getExternalReturnId();
    }

    private void createRma(Store store, String marketplace, MarketplaceReturn ret) {
        Order order = ordersRepository.findByStoreIdAndExternalOrderId(store.getStoreId(), ret.externalOrderId());
        if (order == null) {
            LOGGER.warn("Skipping {} return {}: order {} not found in store {}", marketplace, ret.externalReturnId(),
                    ret.externalOrderId(), store.getStoreId());
            notifyUnmatched(store, marketplace, ret);
            return;
        }
        if (order.getStatus() == OrderStatus.Cancelled) {
            LOGGER.warn("Skipping {} return {}: order {} is cancelled", marketplace, ret.externalReturnId(), order.getOrderId());
            notifyUnmatched(store, marketplace, ret);
            return;
        }

        RMA rma = new RMA(store.getStoreId());
        List<RMAItem> rmaItems = matchItems(rma.getRmaId(), order, ret, marketplace);
        if (rmaItems.isEmpty()) {
            LOGGER.warn("Skipping {} return {}: none of its items match order {}", marketplace, ret.externalReturnId(),
                    order.getOrderId());
            notifyUnmatched(store, marketplace, ret);
            return;
        }

        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setOrderId(order.getOrderId());
        rma.setEmail(order.getEmail());
        rma.setShippingDetails(order.getShippingDetails());
        rma.setShipments(toShipments(ret));
        rma.setEmailNotificationsEnabled(false);
        rma.setShippingInsurance(RMAItem.computeTotalPrice(rmaItems));
        if (ret.createdAt() != null) {
            rma.setCreatedAt(ret.createdAt());
        }
        rma.setMarketplace(marketplace);
        rma.setExternalReturnId(ret.externalReturnId());
        rma.setExternalReturnReference(ret.referenceNumber());
        rma.setExternalReturnStatus(ret.status());

        rmaItemsRepository.batchSave(rmaItems);
        rmaRepository.save(rma);
        LOGGER.info("Created RMA {} from {} return {} for order {}", rma.getRmaId(), marketplace, ret.externalReturnId(),
                order.getOrderId());
    }

    private void notifyUnmatched(Store store, String marketplace, MarketplaceReturn ret) {
        StoreNotification notification = new StoreNotification(
                StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED,
                ret.externalReturnId(),
                marketplace + " return " + referenceOf(ret)
                        + " could not be matched to an order in the application — handle it in the marketplace panel");
        if (!store.getNotifications().contains(notification)) {
            store.getNotifications().add(notification);
            storesRepository.save(store);
        }
    }

    private static String referenceOf(MarketplaceReturn ret) {
        return ret.referenceNumber() != null ? ret.referenceNumber() : ret.externalReturnId();
    }

    private List<RMAItem> matchItems(String rmaId, Order order, MarketplaceReturn ret, String marketplace) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        Set<String> used = new HashSet<>();
        List<RMAItem> result = new ArrayList<>();
        for (MarketplaceReturn.Item item : ret.items()) {
            OrderItem match = orderItems.stream()
                    .filter(oi -> !used.contains(oi.getItemId()))
                    .filter(oi -> item.manufacturerCode() != null && item.manufacturerCode().equals(keyOf(oi)))
                    .filter(oi -> !oi.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                LOGGER.warn("{} return {}: no order item with key {} in order {}", marketplace,
                        ret.externalReturnId(), item.manufacturerCode(), order.getOrderId());
                continue;
            }
            used.add(match.getItemId());
            int qty = Math.min(item.quantity(), match.getQty());
            if (qty < item.quantity()) {
                LOGGER.warn("{} return {}: quantity {} of {} clamped to ordered {}", marketplace, ret.externalReturnId(),
                        item.quantity(), item.manufacturerCode(), match.getQty());
            }
            RMAItem draft = new RMAItem();
            draft.setItemId(match.getItemId());
            draft.setDesiredResolution(RMAResolutionType.Return);
            draft.setReason(item.reason());
            draft.setQty(qty);
            result.add(new RMAItem(rmaId, match, draft));
        }
        return result;
    }

    private static String keyOf(OrderItem orderItem) {
        String externalItemId = orderItem.getExternalItemId();
        return isNotBlank(externalItemId) ? externalItemId : orderItem.getManufacturerCode();
    }

    private static List<Shipment> toShipments(MarketplaceReturn ret) {
        List<Shipment> shipments = new ArrayList<>();
        for (MarketplaceReturn.Parcel parcel : ret.parcels()) {
            Shipment shipment = new Shipment(ShipmentType.Courier);
            shipment.setTrackingNo(parcel.trackingNo());
            shipment.setCarrier(parcel.carrierId());
            shipments.add(shipment);
        }
        return shipments;
    }
}
