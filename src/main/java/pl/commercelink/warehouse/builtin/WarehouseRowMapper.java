package pl.commercelink.warehouse.builtin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.Money;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a warehouse item into a list row. "Source" is what DeliveredPredicate compares for the actions that need one
 * delivery or one supplier: the provider of the item's delivery, or the delivery id itself when there is no delivery
 * record (an item still New or in Allocation carries the supplier name there).
 * <p>
 * The Delivery cell links only where the link leads somewhere: a delivery of the store (short id, the supplier's label
 * under it) or, for an item still waiting for its supplier, that supplier's delivery planning page (the supplier's label
 * as the link). Anything else ("Unknown" of an item added by hand, a removed delivery, the warehouse itself) is a dash.
 */
class WarehouseRowMapper {

    private final MessageSource messages;
    private final Locale locale;
    private final DeliveryRedirectResolver redirects;
    private final Map<String, Delivery> deliveries;
    private final UnaryOperator<String> supplierLabel;

    /** deliveries holds the store's deliveries found for the items' delivery ids; an id missing there has no record. */
    WarehouseRowMapper(MessageSource messages, Locale locale, DeliveryRedirectResolver redirects, Map<String, Delivery> deliveries,
                       UnaryOperator<String> supplierLabel) {
        this.messages = messages;
        this.locale = locale;
        this.redirects = redirects;
        this.deliveries = deliveries;
        this.supplierLabel = supplierLabel;
    }

    static boolean uncategorized(String category) {
        return StringUtils.isBlank(category) || Categories.UNCATEGORIZED.equals(category);
    }

    static String categoryValue(String category) {
        return uncategorized(category) ? WarehouseListQuery.NO_CATEGORY : category;
    }

    WarehouseItemRow map(WarehouseItem item) {
        String codes = Stream.of(StringUtils.isBlank(item.getEan()) ? null : text("warehouse.row.ean", item.getEan()),
                        StringUtils.trimToNull(item.getManufacturerCode()))
                .filter(Objects::nonNull).collect(Collectors.joining(" · "));
        ItemCondition condition = item.getCondition();
        boolean marked = condition == ItemCondition.OpenBox || condition == ItemCondition.Damaged;
        boolean uncategorized = uncategorized(item.getCategory());
        DeliveryLink link = deliveryLink(item);
        String systemCost = null;
        String systemCostTitle = null;
        if (item.hasUnitSystemCost()) {
            systemCost = text("warehouse.row.systemCost", Money.format(item.systemCost().netValue()));
            systemCostTitle = text("warehouse.item.system.cost.tooltip", Money.format(item.systemCost().netValue()),
                    Money.format(item.systemCost().grossValue()));
        }
        return new WarehouseItemRow(item.getItemId(), "/dashboard/warehouse/items/" + item.getItemId(), item.getName(), codes,
                marked ? text("ItemCondition." + condition.name()) : null,
                condition == ItemCondition.Damaged ? "is-bad" : marked ? "is-warn" : null,
                StringUtils.trimToNull(item.getComment()),
                uncategorized ? text("warehouse.category.none") : item.getCategory(), uncategorized, item.getQty(),
                Money.format(item.unitCost().netValue()), text("warehouse.row.gross", Money.format(item.unitCost().grossValue())),
                systemCost, systemCostTitle, link.href(), link.text(), link.supplier(),
                StringUtils.isBlank(item.getSerialNo()) ? null : text("warehouse.row.serial", item.getSerialNo()),
                item.getStatus().name(), text(WarehouseStatuses.labelKey(item.getStatus())), WarehouseStatuses.tone(item.getStatus()),
                WarehouseStatuses.selectable(item.getStatus()),
                provider(item));
    }

    private record DeliveryLink(String href, String text, String supplier) {
        static final DeliveryLink NONE = new DeliveryLink(null, null, null);
    }

    private DeliveryLink deliveryLink(WarehouseItem item) {
        if (redirects.pointsToPlanning(item)) {
            return new DeliveryLink(redirects.resolveFor(item), supplierLabel.apply(item.getDeliveryId()), null);
        }
        Delivery delivery = redirects.pointsToDelivery(item) ? deliveries.get(item.getDeliveryId()) : null;
        if (delivery == null) {
            return DeliveryLink.NONE;
        }
        return new DeliveryLink(redirects.resolveFor(item), item.getShortenedDeliveryId(),
                StringUtils.isBlank(delivery.getProvider()) ? null : supplierLabel.apply(delivery.getProvider()));
    }

    private String provider(WarehouseItem item) {
        Delivery delivery = item.getDeliveryId() == null ? null : deliveries.get(item.getDeliveryId());
        return delivery != null && delivery.getProvider() != null ? delivery.getProvider() : item.getDeliveryId();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
