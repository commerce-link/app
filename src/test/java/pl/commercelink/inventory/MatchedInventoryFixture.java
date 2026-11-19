package pl.commercelink.inventory;

import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.taxonomy.TaxonomyCache;

/** Rows built by hand in tests of other packages, described the way an {@link InventoryView} without a PIM would. */
public final class MatchedInventoryFixture {

    private MatchedInventoryFixture() {
    }

    /** The row reads its product from the cache on first use, as a row found through a view does. */
    public static MatchedInventory readingFrom(TaxonomyCache taxonomyCache, MatchedInventory row) {
        row.readProductFrom(key -> ProductDataFixture.resolved(taxonomyCache.find(key)));
        return row;
    }
}
