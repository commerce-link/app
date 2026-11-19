package pl.commercelink.inventory;

import pl.commercelink.products.ProductData;
import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.products.ResolvedProduct;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

/** Rows built by hand in tests of other packages, described the way an {@link InventoryView} without a PIM would. */
public final class MatchedInventoryFixture {

    /** Differs from any product data a test describes a row with in every field a reader may take. */
    public static final Taxonomy OTHER_TAXONOMY_RECORD = new Taxonomy("5999999999993", "TAXONOMY-MFN", "Taxonomy brand",
            "Taxonomy name", "Taxonomy category", 1, 1, 1, null, "taxonomy-category-id");

    private MatchedInventoryFixture() {
    }

    /** The row reads its product from the cache on first use, as a row found through a view does. */
    public static MatchedInventory readingFrom(TaxonomyCache taxonomyCache, MatchedInventory row) {
        row.readProductFrom(key -> ProductDataFixture.resolved(taxonomyCache.find(key)));
        return row;
    }

    /**
     * The row described by the given product data while its taxonomy record says something else, as for a product
     * the PIM describes: a reader taking a field from the record instead of the data gets {@link #OTHER_TAXONOMY_RECORD}.
     */
    public static MatchedInventory describedAs(ProductData product, MatchedInventory row) {
        row.describeAs(new ResolvedProduct(product, OTHER_TAXONOMY_RECORD));
        return row;
    }
}
