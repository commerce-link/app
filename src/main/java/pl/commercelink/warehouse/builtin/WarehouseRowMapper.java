package pl.commercelink.warehouse.builtin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.web.orders.Money;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns a warehouse item into a list row without reading the item's delivery: the list shows up to a page of items, and
 * the client asked not to pay a delivery read per row for the supplier's name (2026-10-11).
 * <p>
 * The Delivery cell holds what the item itself carries. A delivery id is a link with the short id. An item still waiting
 * for its supplier links to that supplier's delivery planning page (the supplier's label as the link, for an admin only:
 * the planning page is an admin page, so a user reads the supplier as text). An item ordered from a connected supplier
 * without a delivery names that supplier without a link. Anything else ("Unknown" of an item added by hand, a supplier
 * no longer connected, the warehouse itself) is a dash.
 */
class WarehouseRowMapper {

    /** The delivery id of an item added by hand, with no delivery behind it. */
    private static final String UNKNOWN = "Unknown";

    private final MessageSource messages;
    private final Locale locale;
    private final DeliveryRedirectResolver redirects;
    private final UnaryOperator<String> supplierLabel;
    private final Predicate<String> connectedSupplier;
    private final Predicate<String> knownSupplier;
    private final boolean admin;
    private final String back;

    /**
     * connectedSupplier tells a supplier identity of the store's connections from any other delivery id; knownSupplier
     * tells any supplier name (connected or not) from a delivery id.
     */
    WarehouseRowMapper(MessageSource messages, Locale locale, DeliveryRedirectResolver redirects, UnaryOperator<String> supplierLabel,
                       Predicate<String> connectedSupplier, Predicate<String> knownSupplier, boolean admin, String back) {
        this.messages = messages;
        this.locale = locale;
        this.redirects = redirects;
        this.supplierLabel = supplierLabel;
        this.connectedSupplier = connectedSupplier;
        this.knownSupplier = knownSupplier;
        this.admin = admin;
        this.back = back;
    }

    static boolean uncategorized(String category) {
        return StringUtils.isBlank(category) || Categories.UNCATEGORIZED.equals(category);
    }

    static String categoryValue(String category) {
        return uncategorized(category) ? WarehouseListQuery.NO_CATEGORY : category;
    }

    WarehouseItemRow map(WarehouseItem item) {
        List<String> codes = Stream.of(StringUtils.isBlank(item.getEan()) ? null : text("warehouse.row.ean", item.getEan()),
                        StringUtils.trimToNull(item.getManufacturerCode()))
                .filter(Objects::nonNull).toList();
        ItemCondition condition = item.getCondition();
        boolean marked = condition == ItemCondition.OpenBox || condition == ItemCondition.Damaged;
        boolean uncategorized = uncategorized(item.getCategory());
        DeliveryLink link = deliveryLink(item);
        String systemCost = item.hasUnitSystemCost()
                ? text("warehouse.item.system.cost.tooltip", Money.format(item.systemCost().netValue()), Money.format(item.systemCost().grossValue()))
                : null;
        return new WarehouseItemRow(item.getItemId(), ItemBack.itemHref(item.getItemId(), back), item.getName(), codes,
                marked ? text("ItemCondition." + condition.name()) : null,
                condition == ItemCondition.Damaged ? "is-bad" : marked ? "is-warn" : null,
                StringUtils.trimToNull(item.getComment()),
                uncategorized ? text("warehouse.category.none") : item.getCategory(), uncategorized, item.getQty(),
                Money.format(item.unitCost().netValue()), text("warehouse.row.gross", Money.format(item.unitCost().grossValue())),
                systemCost, link.href(), link.text(), link.supplier(),
                StringUtils.isBlank(item.getSerialNo()) ? null : text("warehouse.row.serial", item.getSerialNo()),
                item.getStatus().name(), text(WarehouseStatuses.labelKey(item.getStatus())), WarehouseStatuses.tone(item.getStatus()),
                WarehouseStatuses.selectable(item.getStatus()),
                link.delivery());
    }

    /** delivery tells a link to a delivery of the store, the one thing goods out and shipping can take an item from. */
    private record DeliveryLink(String href, String text, String supplier, boolean delivery) {
        static final DeliveryLink NONE = new DeliveryLink(null, null, null, false);

        static DeliveryLink supplier(String label) {
            return new DeliveryLink(null, null, label, false);
        }
    }

    private DeliveryLink deliveryLink(WarehouseItem item) {
        String id = item.getDeliveryId();
        if (redirects.pointsToPlanning(item)) {
            return admin ? new DeliveryLink(redirects.resolveFor(item), supplierLabel.apply(id), null, false)
                    : DeliveryLink.supplier(supplierLabel.apply(id));
        }
        if (StringUtils.isBlank(id) || UNKNOWN.equalsIgnoreCase(id) || !redirects.pointsToDelivery(item)) {
            return DeliveryLink.NONE;
        }
        if (connectedSupplier.test(id)) {
            return DeliveryLink.supplier(supplierLabel.apply(id));
        }
        if (knownSupplier.test(id)) {
            return DeliveryLink.NONE;
        }
        return new DeliveryLink(redirects.resolveFor(item), item.getShortenedDeliveryId(), null, true);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
