package pl.commercelink.orders;

import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * The key that links an order item to a marketplace offer, in both directions. Current orders store the raw
 * marketplace key in externalItemId. Orders imported before that field existed carry it only in sku,
 * normalised by Basket.setBasketItems (unifyMfn); manufacturerCode is the supplier's part number after
 * fulfilment and is kept as a last resort only. The Allegro adapter mirrors the exact-then-normalised rule.
 */
public final class MarketplaceItemKey {

    private MarketplaceItemKey() {
    }

    /** Raw key sent back to the marketplace when refunding. */
    public static String of(OrderItem orderItem) {
        if (isNotBlank(orderItem.getExternalItemId())) {
            return orderItem.getExternalItemId();
        }
        if (isNotBlank(orderItem.getSku())) {
            return orderItem.getSku();
        }
        return orderItem.getManufacturerCode();
    }

    /** Whether a key received from the marketplace identifies this order item. */
    public static boolean matches(OrderItem orderItem, String marketplaceKey) {
        if (marketplaceKey == null) {
            return false;
        }
        String externalItemId = orderItem.getExternalItemId();
        if (isNotBlank(externalItemId)) {
            return marketplaceKey.equals(externalItemId);
        }
        String normalisedKey = UnifiedProductIdentifiers.unifyMfn(marketplaceKey);
        return normalisedKey.equals(UnifiedProductIdentifiers.unifyMfn(orderItem.getSku()))
                || normalisedKey.equals(UnifiedProductIdentifiers.unifyMfn(orderItem.getManufacturerCode()));
    }
}
