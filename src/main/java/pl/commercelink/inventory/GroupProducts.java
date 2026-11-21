package pl.commercelink.inventory;

import pl.commercelink.products.ProductData;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.ResolvedProduct;

import java.util.List;

/**
 * Product data of inventory groups as one scope sees them: one answer per key, in the keys' order, read in one call
 * for the whole list. An index over hundreds of thousands of groups is described this way, never group by group.
 */
@FunctionalInterface
public interface GroupProducts {

    List<ProductData> of(List<InventoryKey> keys);

    /** {@code storeId} null gives the global scope: global PIM entries and the taxonomy. */
    static GroupProducts of(ProductDataResolver resolver, String storeId) {
        return keys -> resolver.ofGroups(storeId, keys).stream().map(ResolvedProduct::data).toList();
    }
}
