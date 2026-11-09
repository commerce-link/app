package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the deliberate asymmetry of the marketplace item key: outbound refunds send the raw key
 * (externalItemId, else sku), inbound matching is exact on externalItemId but tolerant (unifyMfn) on sku
 * for orders imported before externalItemId existed.
 */
class MarketplaceItemKeyTest {

    private static OrderItem orderItem(String externalItemId, String sku) {
        OrderItem item = new OrderItem();
        item.setExternalItemId(externalItemId);
        item.setSku(sku);
        return item;
    }

    @Test
    void ofPrefersExternalItemIdThenSku() {
        // given / when / then
        assertEquals("ext", MarketplaceItemKey.of(orderItem("ext", "SKU")));
        assertEquals("SKU", MarketplaceItemKey.of(orderItem(null, "SKU")));
    }

    @Test
    void requiresAnExactMatchOnExternalItemId() {
        // given: current orders carry the raw marketplace key; a normalised variant must NOT match
        OrderItem item = orderItem("abc123", "ABC123");

        // when / then
        assertTrue(MarketplaceItemKey.matches(item, "abc123"));
        assertFalse(MarketplaceItemKey.matches(item, "ABC123"));
        assertFalse(MarketplaceItemKey.matches(item, null));
    }
}
