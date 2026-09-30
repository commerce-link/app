package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.receipts.ReceiptLock;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.warehouse.api.ItemCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One row of the items table. The five scope flags are the predicates the bulk actions use on the server
 * (OrdersManager), written into data-* attributes so the selection bar can say "2 of 3" before anything is posted.
 */
public record OrderItemRow(String itemId, int index, String name, String category, String mfn, String sku,
                           String serialNo, String serialHref, String conditionKey, String conditionTone,
                           boolean consolidated, boolean service, String comment, int qty, String unitPrice,
                           String unitCost, String statusKey, String statusTone, String deliveryLabel, String deliveryHref,
                           boolean readyForAllocation, boolean allocatedProduct, boolean deliveredProduct, boolean movable,
                           boolean removable, List<ItemAction.State> actions, String editHref, double price, double tax,
                           boolean group, String viewHref) {

    /**
     * {@code superAdmin} matters on its own besides {@code readOnly}: a closed order is read-only too, but only the
     * store's own users have a route to the item page. dropshipLocked: the order has items in a dropship delivery, which
     * greys "Do alokacji" as it greyed the selection row's entry before it moved into the item menu.
     */
    public record Context(Order order, boolean readOnly, boolean superAdmin, SupplierLabelMap labels,
                          Function<OrderItem, String> deliveryHref, Function<String, String> serialHref,
                          ReceiptLock receiptLock, boolean dropshipLocked) {

        /** receiptLock: why the order's e-receipt locks it, if it does (ReceiptOrderState#receiptLock). */
        public Context(Order order, boolean readOnly, boolean superAdmin, SupplierLabelMap labels,
                       Function<OrderItem, String> deliveryHref, Function<String, String> serialHref,
                       ReceiptLock receiptLock) {
            this(order, readOnly, superAdmin, labels, deliveryHref, serialHref, receiptLock, false);
        }

        public Context(Order order, boolean readOnly, boolean superAdmin, SupplierLabelMap labels,
                       Function<OrderItem, String> deliveryHref, Function<String, String> serialHref) {
            this(order, readOnly, superAdmin, labels, deliveryHref, serialHref, ReceiptLock.NONE);
        }
    }

    public static OrderItemRow of(OrderItem item, int index, Context context) {
        String itemHref = "/dashboard/orders/" + context.order().getOrderId() + "/items/" + item.getItemId();
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        String deliveryLabel = deliveryLabel(item, context.labels());
        String serial = StringUtils.trimToNull(item.getSerialNo());
        boolean showCondition = item.getCondition() != null && !item.isSealed();
        return new OrderItemRow(
                item.getItemId(), index, item.getName(), StringUtils.trimToNull(item.getCategory()),
                StringUtils.trimToNull(item.getManufacturerCode()), item.isNew() ? StringUtils.trimToNull(item.getSku()) : null,
                serial, serial == null ? null : context.serialHref().apply(serial.split(",")[0].trim()),
                showCondition ? OrderLabels.condition(item.getCondition()) : null,
                item.getCondition() == ItemCondition.Damaged ? OrderLabels.BAD : OrderLabels.WARN,
                item.isConsolidated(), item.isService(), StringUtils.trimToNull(item.getComment()), item.getQty(),
                Money.format(item.getPrice()), Money.format(item.getCost()),
                OrderLabels.itemStatus(item.getStatus()), OrderLabels.tone(item.getStatus()),
                deliveryLabel, deliveryId == null ? null : context.deliveryHref().apply(item),
                item.isReadyForAllocation(), item.isProduct() && item.isAllocated(), item.isProduct() && item.isDelivered(),
                item.canBeMovedToAnotherOrder(), item.isNew() || item.isService(),
                context.readOnly() ? List.of() : actions(item, context.order(), context.receiptLock(), context.dropshipLocked()),
                context.readOnly() ? null : itemHref,
                item.getPrice(), item.getTax(), item.isGroup(),
                // without the item menu a closed order would have no way to its item page (EAN, VAT, delivery dates)
                context.readOnly() && !context.superAdmin() ? itemHref : null);
    }

    /**
     * The fulfilment cell's second line on its own (the item page's header): the label and link of the table row, and
     * whether the value is a supplier delivery's id rather than a supplier.
     */
    public record Delivery(String label, String href, boolean delivery) {
    }

    /** A store connection by its label; anything else (a delivery id, a typed supplier name) by its short form. */
    public static String deliveryLabel(OrderItem item, SupplierLabelMap labels) {
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        if (deliveryId == null) {
            return null;
        }
        return labels.has(deliveryId) ? labels.of(deliveryId) : item.getShortenedDeliveryId();
    }

    /** The SKU is printed only when it says more than the manufacturer code beside it. */
    public boolean skuShown() {
        return sku != null && !sku.equals(mfn);
    }

    /** The markers after the name: condition and service pills and the comment toggle. */
    public boolean hasMarkers() {
        return conditionKey != null || service || comment != null;
    }

    /** Any part of the codes line under the name: category, MFN, SKU, SN. */
    public boolean hasCodes() {
        return category != null || mfn != null || skuShown() || serialNo != null;
    }

    /**
     * The conditions of the old order-details item menu, plus: consolidation and splitting a set stop at the closing
     * invoice.
     */
    public static List<ItemAction.State> actions(OrderItem item, Order order) {
        return actions(item, order, false);
    }

    /**
     * receiptLocked: an e-receipt is being issued, so merging on the invoice and splitting a set are locked as once the
     * invoice is issued (the same refusals as OrdersController#toggleConsolidation and #splitGroupItem).
     */
    public static List<ItemAction.State> actions(OrderItem item, Order order, boolean receiptLocked) {
        return actions(item, order, ReceiptLock.of(receiptLocked));
    }

    /** The same, worded after why the e-receipt locks the order (still issuing, or fiscalised and being attached). */
    public static List<ItemAction.State> actions(OrderItem item, Order order, ReceiptLock receiptLock) {
        return actions(item, order, receiptLock, false);
    }

    /**
     * dropshipLocked: the order has items in a dropship delivery. "Do alokacji" posts this one item to the bulk
     * endpoint (OrdersManager#moveItemsToAllocation), which moves only a New item with its allocation data (supplier,
     * EAN, manufacturer code): the entry is greyed with the reason whenever the post would change nothing.
     */
    public static List<ItemAction.State> actions(OrderItem item, Order order, ReceiptLock receiptLock, boolean dropshipLocked) {
        List<ItemAction.State> states = new ArrayList<>();
        // an item that already has a supplier (Allocation) is released first, then reassigned
        String assignReason = item.isGroup() ? "order.item.unavailable.group"
                : item.isNew() ? null
                : item.isReleasable() ? "order.item.unavailable.clear.first"
                : "order.item.unavailable.not.new";
        states.add(ItemAction.State.of(ItemAction.ASSIGN_SKU, assignReason, null));
        states.add(ItemAction.State.of(ItemAction.ASSIGN_SUPPLIER, assignReason, null));
        // a marketplace-routed order keeps the supplier the marketplace chose; the server refuses the warehouse too
        String warehouseReason = assignReason != null ? assignReason
                : order.isBoundToExternalSupplier() ? "order.item.unavailable.routed" : null;
        states.add(ItemAction.State.of(ItemAction.ASSIGN_WAREHOUSE, warehouseReason, null));
        states.add(ItemAction.State.of(ItemAction.ALLOCATE, allocateReason(item, dropshipLocked), null));
        String clearReason = item.isGroup() ? "order.item.unavailable.group"
                : item.isClaimed() ? "order.item.unavailable.claimed"
                : StringUtils.isBlank(item.getDeliveryId()) ? "order.item.unavailable.no.supplier"
                : item.isReleasable() ? null : "order.item.unavailable.fulfilled";
        states.add(ItemAction.State.of(ItemAction.CLEAR_SUPPLIER, clearReason,
                item.isClaimed() ? ConversionUtil.getShortenedId(item.getClaimedDeliveryId()) : null));
        // "Split set" only exists for a set; for any other item it is not greyed out but absent
        if (item.isGroup()) {
            // splitting rewrites the lines the sale document lists (OrdersController#splitGroupItem refuses the same)
            ItemSaleLock saleLock = ItemSaleLock.of(order, receiptLock);
            String splitReason = item.isService() ? "order.item.unavailable.service"
                    : !item.isNew() ? "order.item.unavailable.not.new"
                    : saleLock != null ? saleLock.menuReasonKey() : null;
            states.add(ItemAction.State.of(ItemAction.SPLIT_GROUP, splitReason, null));
        }
        boolean invoiced = order.isInvoiced();
        String consolidateReason = invoiced ? "order.item.unavailable.invoiced"
                : receiptLock.locks() ? receiptLock.key("order.item.unavailable.receipt") : null;
        states.add(new ItemAction.State(ItemAction.CONSOLIDATE,
                item.isConsolidated() ? "order.item.menu.deconsolidate" : "order.item.menu.consolidate",
                consolidateReason == null, consolidateReason, null));
        states.add(ItemAction.State.of(ItemAction.EDIT, null, null));
        return states;
    }

    /** Why "Do alokacji" would change nothing for the item (Item#isReadyForAllocation), or null. */
    private static String allocateReason(OrderItem item, boolean dropshipLocked) {
        if (dropshipLocked) {
            return "order.item.unavailable.dropship";
        }
        if (!item.isNew()) {
            return "order.item.unavailable.not.new";
        }
        if (StringUtils.isBlank(item.getDeliveryId())) {
            return "order.item.unavailable.allocation.supplier";
        }
        return item.isReadyForAllocation() ? null : "order.item.unavailable.allocation.codes";
    }
}
