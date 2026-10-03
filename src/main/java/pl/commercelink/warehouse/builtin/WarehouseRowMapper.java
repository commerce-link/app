package pl.commercelink.warehouse.builtin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.Money;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a warehouse item into a list row. "Source" is what DeliveredPredicate compares for the actions that need one
 * delivery or one supplier: the provider of the item's delivery, or the delivery id itself when there is no delivery
 * record (an item still New or in Allocation carries the supplier name there).
 */
class WarehouseRowMapper {

    private final MessageSource messages;
    private final Locale locale;
    private final Function<WarehouseItem, String> deliveryHref;
    private final Map<String, String> providerByDelivery;

    WarehouseRowMapper(MessageSource messages, Locale locale, Function<WarehouseItem, String> deliveryHref,
                       Map<String, String> providerByDelivery) {
        this.messages = messages;
        this.locale = locale;
        this.deliveryHref = deliveryHref;
        this.providerByDelivery = providerByDelivery;
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
                systemCost, systemCostTitle, deliveryHref.apply(item), item.getShortenedDeliveryId(),
                StringUtils.isBlank(item.getSerialNo()) ? null : text("warehouse.row.serial", item.getSerialNo()),
                item.getStatus().name(), text(WarehouseStatuses.labelKey(item.getStatus())), WarehouseStatuses.tone(item.getStatus()),
                WarehouseStatuses.selectable(item.getStatus()),
                providerByDelivery.getOrDefault(item.getDeliveryId(), item.getDeliveryId()));
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
