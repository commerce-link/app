package pl.commercelink.web.deliveries.details;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.deliveries.AllocationType;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.ActionState;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.AllocationFields;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.AllocationRow;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.ItemsCard;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.MergeTarget;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.ProductRow;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.SelectionBar;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.orders.OrderLabels;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The items card (spec §6): one row per product (allocations grouped by manufacturer code, in the order the delivery
 * lists them, unlike DeliveryItem.groupAndUnify's hash order), its allocations under it, and the selection row.
 */
final class DeliveryItemsFactory {

    private DeliveryItemsFactory() {
    }

    static ItemsCard items(DeliveryPageData data, DeliveryViewer viewer, DeliveryLinks links, Set<Integer> checked) {
        Delivery delivery = data.delivery();
        List<Allocation> all = delivery.getAllocations();
        boolean selectable = DeliveryRules.selectable(viewer, delivery);
        List<ProductRow> products = new ArrayList<>();
        for (List<Integer> indexes : byProduct(all).values()) {
            Allocation first = all.get(indexes.get(0));
            int ordered = 0;
            int received = 0;
            int adjustable = 0;
            List<AllocationRow> rows = new ArrayList<>();
            for (int index : indexes) {
                Allocation allocation = all.get(index);
                ordered += allocation.getQty();
                received += allocation.isInAllocation() ? 0 : allocation.getQty();
                adjustable += allocation.getType() == AllocationType.Warehouse && allocation.isInAllocation() ? allocation.getQty() : 0;
                rows.add(allocationRow(index, allocation, delivery, links, selectable, checked.contains(index)));
            }
            ActionState changeQty = DeliveryRules.changeQty(viewer, delivery, received, ordered);
            String history = links.mfnHistory(first.getMfn());
            products.add(new ProductRow(first.getName(), first.getEan(), first.getMfn(), received, ordered,
                    ordered > 0 && received >= ordered, Money.format(first.getUnitCost() * ordered),
                    Money.format(first.getUnitCost()), Math.max(1, ordered - adjustable), changeQty,
                    links.openQty(first.getMfn()), history, changeQty.visible() || history != null, rows));
        }
        double goods = goodsNet(delivery);
        List<MergeTarget> targets = data.mergeTargets().stream()
                .map(target -> new MergeTarget(target.getDeliveryId(), target.getShortenedDeliveryId(),
                        StringUtils.trimToNull(target.getExternalDeliveryId()), OrderFormats.date(target.getEstimatedDeliveryAt())))
                .toList();
        SelectionBar selection = new SelectionBar(DeliveryRules.receive(viewer, delivery), DeliveryRules.ship(viewer, delivery),
                DeliveryRules.merge(viewer, delivery, !targets.isEmpty()), DeliveryRules.split(viewer, delivery),
                DeliveryRules.remove(viewer, delivery), removeMessageKey(delivery), targets);
        return new ItemsCard(products, selectable,
                delivery.isDropship() ? "deliveries.details.items.column.shipped" : "deliveries.details.items.column.received",
                selection, Money.format(goods), DeliveryRules.grossOrNull(delivery, goods),
                all.size(), pendingIndexes(delivery).size());
    }

    /** Net value of the goods: each product at the unit cost of its first allocation, as the old Items table showed it. */
    static double goodsNet(Delivery delivery) {
        List<Allocation> all = delivery.getAllocations();
        double total = 0;
        for (List<Integer> indexes : byProduct(all).values()) {
            double unit = all.get(indexes.get(0)).getUnitCost();
            total += unit * indexes.stream().mapToInt(index -> all.get(index).getQty()).sum();
        }
        return BigDecimal.valueOf(total).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    static Set<Integer> pendingIndexes(Delivery delivery) {
        Set<Integer> indexes = new LinkedHashSet<>();
        for (int i = 0; i < delivery.getAllocations().size(); i++) {
            if (delivery.getAllocations().get(i).isInAllocation()) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static Map<String, List<Integer>> byProduct(List<Allocation> all) {
        Map<String, List<Integer>> byMfn = new LinkedHashMap<>();
        for (int i = 0; i < all.size(); i++) {
            byMfn.computeIfAbsent(Objects.toString(all.get(i).getMfn(), ""), mfn -> new ArrayList<>()).add(i);
        }
        return byMfn;
    }

    private static AllocationRow allocationRow(int index, Allocation allocation, Delivery delivery, DeliveryLinks links,
                                               boolean selectable, boolean checked) {
        AllocationKey key = allocation.getKey();
        boolean warehouse = allocation.getType() == AllocationType.Warehouse;
        String orderId = key == null ? null : key.getOrderId();
        String itemId = key == null ? null : key.getItemId();
        boolean received = !allocation.isInAllocation();
        String href = warehouse ? (itemId == null ? null : links.warehouseItem(itemId))
                : (orderId == null ? null : links.order(orderId));
        String stateKey = !received ? "deliveries.details.items.state.waiting"
                : delivery.isDropship() ? "deliveries.details.items.state.shipped" : "deliveries.details.items.state.received";
        boolean checkbox = selectable && !received;
        return new AllocationRow(index, warehouse, orderId == null ? null : ConversionUtil.getShortenedId(orderId),
                warehouse || key == null ? null : StringUtils.trimToNull(key.getName()), href, allocation.isDirectToConsumer(),
                allocation.getQty(), received, checkbox, checkbox && checked, stateKey,
                received ? OrderLabels.OK : OrderLabels.NEUTRAL, allocation.getName(),
                new AllocationFields(orderId, itemId, key == null ? null : key.getName(),
                        allocation.getType() == null ? null : allocation.getType().name(), allocation.getName(),
                        allocation.getQty(), allocation.getEan(), allocation.getMfn(), allocation.getDeliveryId(),
                        allocation.isInAllocation(), BigDecimal.valueOf(allocation.getUnitCost()).toPlainString()));
    }

    private static String removeMessageKey(Delivery delivery) {
        if (delivery.isOrderOutcomeUnknown()) {
            return "deliveries.details.remove.message.unknown";
        }
        return delivery.isDropship() ? "deliveries.details.remove.message.dropship" : "deliveries.details.remove.message";
    }
}
