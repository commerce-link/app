package pl.commercelink.web.dtos;

import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.taxonomy.Taxonomy;

/** The product the "add items" dialog lists for a typed EAN, with the name, price and EAN its item gets. */
public record ItemAddInventoryRow(String ean, String mfn, String brand, String name, String category,
                                  double price, long qty, int estimatedDeliveryDays) {

    public static ItemAddInventoryRow of(MatchedInventory matched) {
        Taxonomy taxonomy = matched.getTaxonomy();
        return new ItemAddInventoryRow(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                taxonomy.category(), matched.getMedianPrice().grossValue(), matched.getTotalAvailableQty(),
                matched.getEstimatedDeliveryDays());
    }
}
