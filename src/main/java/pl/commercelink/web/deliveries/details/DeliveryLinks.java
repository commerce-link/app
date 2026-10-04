package pl.commercelink.web.deliveries.details;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Addresses of the delivery details page and its actions for one viewer: a store user works on the short paths, a
 * super admin under /dashboard/store/{storeId}. Actions only a store does (receive, documents, payments) have no
 * super admin variant; links into store screens a super admin cannot open are null for him.
 */
public record DeliveryLinks(boolean superAdmin, String storeId, String deliveryId) {

    public static DeliveryLinks of(boolean superAdmin, String storeId, String deliveryId) {
        return new DeliveryLinks(superAdmin, storeId, deliveryId);
    }

    private String base() {
        return superAdmin ? "/dashboard/store/" + storeId + "/deliveries" : "/dashboard/deliveries";
    }

    private String action(String path) {
        return base() + "/" + deliveryId + path;
    }

    public String details() {
        return base() + "/details?deliveryId=" + deliveryId;
    }

    /** The page with one dialog open: where a dialog's opener leads without JavaScript. */
    public String open(String dialog) {
        return details() + "&open=" + dialog + "#" + DeliveryPageModelFactory.dialogId(dialog);
    }

    public String openQty(String mfn) {
        return details() + "&open=qty&mfn=" + encode(mfn) + "#qty-dialog";
    }

    public String saveTerms() {
        return base() + "/details";
    }

    public String receive() {
        return "/dashboard/deliveries/markSelectedAsReceived";
    }

    public String confirmShipment() {
        return base() + "/confirmDropshipShipment";
    }

    public String removeAllocations() {
        return base() + "/deleteSelectedAllocations";
    }

    public String merge() {
        return base() + "/mergeSelectedAllocations";
    }

    public String split() {
        return base() + "/splitSelectedAllocations";
    }

    public String updateItemQty() {
        return base() + "/updateItemQty";
    }

    public String confirm(String action) {
        return action("/confirm/" + action);
    }

    public String refreshOrderId() {
        return action("/refresh-order-id");
    }

    public String retry() {
        return action("/purchase/retry");
    }

    public String reconcile() {
        return action("/purchase/reconcile");
    }

    public String complete() {
        return action("/purchase/complete");
    }

    public String force() {
        return action("/purchase/force");
    }

    public String approval() {
        return "/dashboard/store/" + storeId + "/deliveries/" + deliveryId + "/approval";
    }

    public String reject() {
        return "/dashboard/store/" + storeId + "/deliveries/" + deliveryId + "/reject";
    }

    public String linkInvoices() {
        return "/dashboard/deliveries/link-invoices";
    }

    public String unlinkInvoice(String invoiceId) {
        return confirm("unlink-invoice") + "?invoiceId=" + encode(invoiceId);
    }

    public String syncPreview(String invoiceId) {
        return "/dashboard/deliveries/sync/preview?deliveryId=" + deliveryId + "&invoiceId=" + encode(invoiceId);
    }

    public String addPayment() {
        return "/dashboard/deliveries/" + deliveryId + "/addPayment";
    }

    public String updatePayments() {
        return "/dashboard/deliveries/" + deliveryId + "/updatePayments";
    }

    public String warehouseDocument(String documentId) {
        return (superAdmin ? "/dashboard/store/" + storeId : "/dashboard") + "/warehouse-documents/details?documentId="
                + encode(documentId);
    }

    /** The order screen refuses a super admin (OrdersController), so he gets the number as text. */
    public String order(String orderId) {
        return superAdmin ? null : "/dashboard/orders/" + orderId;
    }

    public String warehouseItem(String itemId) {
        return superAdmin ? null : "/dashboard/warehouse/items/" + itemId;
    }

    public String mfnHistory(String mfn) {
        return superAdmin ? null
                : "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=" + deliveryId + "&mfn=" + encode(mfn);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
