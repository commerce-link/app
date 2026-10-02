package pl.commercelink.web.deliveries.approval;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.settings.StoreSettingsCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the platform administrator's approval screen shows about a delivery request besides the supplier's live answer:
 * whose store asked and when, which customer orders the goods serve (plus how much goes to stock), the totals the
 * reject dialog names, and the store-scoped links of the screen.
 */
public record ApprovalPage(String storeId, String deliveryId, String shortDeliveryId, String supplierLabel,
                           String storeName, String storeHref, String requestedAt, boolean dropship,
                           List<RequestLine> requestFor, int warehousePieces, int pieces, String net,
                           ShippingDetails consignee, Shipment pickupShipment, boolean openReject) {

    /** One customer order the request carries goods for. */
    public record RequestLine(String orderLabel, String href, String customer, String source, int qty) {
    }

    public static ApprovalPage of(Delivery delivery, Store store, List<Order> orders, String supplierLabel,
                                  ShippingDetails consignee, Shipment pickupShipment, boolean openReject) {
        String storeId = delivery.getStoreId();
        Map<String, Order> byId = orders.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getOrderId, Function.identity(), (a, b) -> a));
        Map<String, Integer> qtyByOrder = new LinkedHashMap<>();
        int warehousePieces = 0;
        for (Allocation allocation : delivery.getAllocations()) {
            AllocationKey key = allocation.getKey();
            String orderId = key == null ? null : key.getOrderId();
            if (StringUtils.isBlank(orderId)) {
                warehousePieces += allocation.getQty();
            } else {
                qtyByOrder.merge(orderId, allocation.getQty(), Integer::sum);
            }
        }
        List<RequestLine> lines = new ArrayList<>();
        qtyByOrder.forEach((orderId, qty) -> lines.add(line(storeId, orderId, byId.get(orderId), qty)));
        int pieces = delivery.getAllocations().stream().mapToInt(Allocation::getQty).sum();
        double net = delivery.getAllocations().stream().mapToDouble(Allocation::getTotalCost).sum();
        String storeName = store != null && StringUtils.isNotBlank(store.getName()) ? store.getName() : storeId;
        return new ApprovalPage(storeId, delivery.getDeliveryId(), StringUtils.left(delivery.getDeliveryId(), 8),
                supplierLabel, storeName, StoreSettingsCatalog.homeHref(UserRole.SUPER_ADMIN, storeId),
                delivery.getOrderedAt() == null ? null : OrderFormats.dateTime(delivery.getOrderedAt()),
                delivery.isDropship(), List.copyOf(lines), warehousePieces, pieces, Money.format(net),
                consignee, pickupShipment, openReject);
    }

    private static RequestLine line(String storeId, String orderId, Order order, int qty) {
        String shortNumber = order != null && order.getShortenedOrderId() != null
                ? order.getShortenedOrderId() : orderId.split("-")[0];
        String source = order != null && order.getSource() != null ? order.getSource().getName() : null;
        return new RequestLine("#" + shortNumber, "/dashboard/store/" + storeId + "/orders/" + orderId,
                customer(order), source, qty);
    }

    private static String customer(Order order) {
        BillingDetails billing = order == null ? null : order.getBillingDetails();
        if (billing == null) {
            return null;
        }
        return StringUtils.isNotBlank(billing.getName()) ? billing.getName().trim() : StringUtils.trimToNull(billing.getEmail());
    }

    private String base() {
        return "/dashboard/store/" + storeId + "/deliveries/";
    }

    public String detailsHref() {
        return base() + "details?deliveryId=" + deliveryId;
    }

    public String rejectHref() {
        return base() + deliveryId + "/reject";
    }

    public String validateHref() {
        return base() + deliveryId + "/approval/validate";
    }

    public String approveHref() {
        return base() + deliveryId + "/approve";
    }
}
