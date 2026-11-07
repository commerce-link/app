package pl.commercelink.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.Taxonomy;

/**
 * An order item resolved from its source (pricelist row or matched inventory) but not yet placed
 * in the order: {@link OrdersManager#addOrderItems} assigns the position and persists it.
 */
public record OrderItemDraft(String category, String name, int qty, double price, String sku,
                             boolean service, int estimatedDeliveryDays) {

    public static OrderItemDraft of(AvailabilityAndPrice availabilityAndPrice, int qty) {
        return new OrderItemDraft(
                availabilityAndPrice.getCategory(),
                availabilityAndPrice.getName(),
                qty,
                availabilityAndPrice.getPrice(),
                availabilityAndPrice.getEan(),
                availabilityAndPrice.isService(),
                availabilityAndPrice.getEstimatedDeliveryDays()
        );
    }

    public static OrderItemDraft of(MatchedInventory matchedInventory, String ean, int qty) {
        if (!matchedInventory.hasAnyOffers()) {
            return new OrderItemDraft(Categories.UNCATEGORIZED, "", qty, 0, ean, false,
                    matchedInventory.getEstimatedDeliveryDays());
        }
        Taxonomy taxonomy = matchedInventory.getTaxonomy();
        return new OrderItemDraft(
                StringUtils.isNotBlank(taxonomy.category()) ? taxonomy.category() : Categories.UNCATEGORIZED,
                taxonomy.name(),
                qty,
                matchedInventory.getMedianPrice().grossValue(),
                ean,
                false,
                matchedInventory.getEstimatedDeliveryDays()
        );
    }
}
