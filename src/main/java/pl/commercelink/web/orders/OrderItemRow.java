package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
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
                           boolean group) {

    public record Context(Order order, boolean admin, boolean readOnly, SupplierLabelMap labels,
                          Function<OrderItem, String> deliveryHref, Function<String, String> serialHref) {
    }

    public static OrderItemRow of(OrderItem item, int index, Context context) {
        String deliveryId = StringUtils.trimToNull(item.getDeliveryId());
        String deliveryLabel = deliveryId == null ? null
                : context.labels().has(deliveryId) ? context.labels().of(deliveryId) : item.getShortenedDeliveryId();
        String serial = StringUtils.trimToNull(item.getSerialNo());
        boolean showCondition = item.getCondition() != null && !item.isSealed();
        return new OrderItemRow(
                item.getItemId(), index, item.getName(), StringUtils.trimToNull(item.getCategory()),
                StringUtils.trimToNull(item.getManufacturerCode()), item.isNew() ? StringUtils.trimToNull(item.getSku()) : null,
                serial, serial == null ? null : context.serialHref().apply(serial.split(",")[0].trim()),
                showCondition ? OrderLabels.condition(item.getCondition()) : null,
                item.getCondition() == ItemCondition.Damaged ? OrderLabels.BAD : OrderLabels.WARN,
                item.isConsolidated(), item.isService(), StringUtils.trimToNull(item.getComment()), item.getQty(),
                Money.format(item.getPrice()), context.admin() ? Money.format(item.getCost()) : null,
                OrderLabels.itemStatus(item.getStatus()), OrderLabels.tone(item.getStatus()),
                deliveryLabel, deliveryId == null ? null : context.deliveryHref().apply(item),
                item.isReadyForAllocation(), item.isProduct() && item.isAllocated(), item.isProduct() && item.isDelivered(),
                item.canBeMovedToAnotherOrder(), item.isNew() || item.isService(),
                context.readOnly() ? List.of() : actions(item, context.order()),
                context.readOnly() ? null : "/dashboard/orders/" + context.order().getOrderId() + "/items/" + item.getItemId(),
                item.getPrice(), item.getTax(), item.isGroup());
    }

    /** The conditions of the old order-details item menu, plus: consolidation stops at the closing invoice. */
    public static List<ItemAction.State> actions(OrderItem item, Order order) {
        List<ItemAction.State> states = new ArrayList<>();
        String assignReason = item.isGroup() ? "order.item.unavailable.group"
                : item.isNew() ? null : "order.item.unavailable.not.new";
        states.add(ItemAction.State.of(ItemAction.ASSIGN_SKU, assignReason, null));
        states.add(ItemAction.State.of(ItemAction.ASSIGN_SUPPLIER, assignReason, null));
        states.add(ItemAction.State.of(ItemAction.ASSIGN_WAREHOUSE, assignReason, null));
        String clearReason = item.isGroup() ? "order.item.unavailable.group"
                : item.isClaimed() ? "order.item.unavailable.claimed"
                : item.isReleasable() ? null : "order.item.unavailable.fulfilled";
        states.add(ItemAction.State.of(ItemAction.CLEAR_SUPPLIER, clearReason,
                item.isClaimed() ? ConversionUtil.getShortenedId(item.getClaimedDeliveryId()) : null));
        String splitReason = !item.isGroup() ? "order.item.unavailable.not.group"
                : item.isService() ? "order.item.unavailable.service"
                : item.isNew() ? null : "order.item.unavailable.not.new";
        states.add(ItemAction.State.of(ItemAction.SPLIT_GROUP, splitReason, null));
        boolean invoiced = order.isInvoiced();
        states.add(new ItemAction.State(ItemAction.CONSOLIDATE,
                item.isConsolidated() ? "order.item.menu.deconsolidate" : "order.item.menu.consolidate",
                !invoiced, invoiced ? "order.item.unavailable.invoiced" : null, null));
        states.add(ItemAction.State.of(ItemAction.EDIT, null, null));
        return states;
    }
}
