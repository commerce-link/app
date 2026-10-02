package pl.commercelink.web.orders;

import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.SerialNumbers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Addresses of an order for the one looking at it: a super admin gets the store-scoped variants. */
public record OrderLinks(String base, String storeId, boolean superAdmin) {

    public static OrderLinks of(Order order, boolean superAdmin) {
        String base = superAdmin
                ? "/dashboard/store/" + order.getStoreId() + "/orders/" + order.getOrderId()
                : "/dashboard/orders/" + order.getOrderId();
        return new OrderLinks(base, order.getStoreId(), superAdmin);
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
        return SerialNumbers.parse(serialNo).stream().map(sn -> new SerialHistory(sn, itemHistory(sn))).toList();
    }
}
