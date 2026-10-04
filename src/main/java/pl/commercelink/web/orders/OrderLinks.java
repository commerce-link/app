package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Addresses of an order for the one looking at it: a super admin gets the store-scoped variants. */
public record OrderLinks(String base, String storeId, boolean superAdmin) {

    public static OrderLinks of(Order order, boolean superAdmin) {
        return new OrderLinks(detailsOf(order.getStoreId(), order.getOrderId(), superAdmin), order.getStoreId(), superAdmin);
    }

    /** The order page for the one looking at it: a super admin has the store-scoped variant. */
    public static String detailsOf(String storeId, String orderId, boolean superAdmin) {
        return superAdmin
                ? "/dashboard/store/" + storeId + "/orders/" + orderId
                : "/dashboard/orders/" + orderId;
    }

    /**
     * The address a printed order card's QR code carries. Paper outlives routes, so it is a stable address that only
     * redirects (OrderScanController), and it names the store, so it works whoever printed the card.
     */
    public static String scan(String storeId, String orderId) {
        return "/dashboard/scan/orders/" + storeId + "/" + orderId;
    }

    /** scan() on the app's public address (app.domain), as a phone needs it. */
    public static String scanUrl(String appDomain, String storeId, String orderId) {
        return StringUtils.removeEnd(appDomain, "/") + scan(storeId, orderId);
    }

    public String details() {
        return base;
    }

    public String card() {
        return base + "/card";
    }

    public String collection() {
        return base + "/collection";
    }

    /** A link out of the order (delivery, warehouse): as is for the store; for a super admin only where a store-scoped page exists. */
    public String forViewer(String path) {
        if (path == null || !superAdmin) {
            return path;
        }
        return path.startsWith("/dashboard/deliveries/") ? "/dashboard/store/" + storeId + path.substring("/dashboard".length()) : null;
    }

    /**
     * The unpin address with the document in its query, used both for the row's link and for the GET confirmation
     * page it opens, so the two never drift apart. '+' is encoded by hand: a query value reads it back as a space.
     * Unpinning is offered only to a store admin, never a super admin, so the store-scoped form is always right.
     */
    public static String removeDocumentPath(String orderId, DocumentType type, String number) {
        return UriComponentsBuilder.fromPath("/dashboard/orders/" + orderId + "/removeDocument")
                .queryParam("type", type.name())
                .queryParam("number", number)
                .encode().build().toUriString().replace("+", "%2B");
    }

    /** The history of one physical item, by its serial number (the store's page; a super admin has none). */
    public static String itemHistory(String serialNo) {
        return "/dashboard/item/history?serialNo=" + URLEncoder.encode(serialNo, StandardCharsets.UTF_8);
    }

    /** One serial number of an item with the link to its history. */
    public record SerialHistory(String serialNo, String href) {
    }

    /**
     * The history links of an item's serial numbers (a comma-separated list for qty &gt; 1), each once, in the
     * item's order; empty without a serial number.
     */
    public static List<SerialHistory> serialHistory(String serialNo) {
        if (serialNo == null) {
            return List.of();
        }
        return Arrays.stream(serialNo.split(",")).map(String::trim).filter(sn -> !sn.isEmpty()).distinct()
                .map(sn -> new SerialHistory(sn, itemHistory(sn))).toList();
    }
}
