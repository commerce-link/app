package pl.commercelink.products;

import pl.commercelink.taxonomy.Taxonomy;

/**
 * What {@link ProductDataResolver} knows about one inventory group: the resolved data and the taxonomy record read
 * along with it. The record is carried only for readers that still work on taxonomy names (the catalog filters, until
 * the client decides which names they filter by), so they do not read the taxonomy product by product.
 */
public record ResolvedProduct(ProductData data, Taxonomy taxonomy) {

    public static final ResolvedProduct UNKNOWN = new ResolvedProduct(ProductData.UNKNOWN, Taxonomy.EMPTY);
}
