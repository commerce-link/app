package pl.commercelink.inventory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class ProductIdentifiers {

    private final Set<String> ids;
    private final Set<String> eans;
    private final Set<String> productCodes;

    private ProductIdentifiers(Set<String> ids, Set<String> eans, Set<String> productCodes) {
        this.ids = ids;
        this.eans = eans;
        this.productCodes = productCodes;
    }

    static ProductIdentifiers of(Collection<MatchedInventory> groups) {
        Set<String> ids = new HashSet<>();
        Set<String> eans = new HashSet<>();
        Set<String> productCodes = new HashSet<>();
        for (MatchedInventory group : groups) {
            InventoryKey key = group.getInventoryKey();
            if (key.getId() != null) {
                ids.add(key.getId());
            }
            eans.addAll(key.getProductEans());
            productCodes.addAll(key.getProductCodes());
        }
        return new ProductIdentifiers(ids, eans, productCodes);
    }

    boolean contains(InventoryKey key) {
        return (key.getId() != null && ids.contains(key.getId()))
                || key.getProductEans().stream().anyMatch(eans::contains)
                || key.getProductCodes().stream().anyMatch(productCodes::contains);
    }
}
