package pl.commercelink.marketplace.links;

import pl.commercelink.products.Product;

import java.util.List;

/**
 * What the store's catalogs say about a set of EANs (an offer link's, or a marketplace offer's): one product, nothing,
 * or several products that leave the offer to an operator. A product is its EAN: fulfilment looks goods up by it, so
 * catalog products sharing an EAN are the same goods, whatever their manufacturer codes.
 */
public sealed interface CatalogMatch {

    /**
     * The hits share one EAN; {@code product} is the first of them in catalog order, and {@code categoryName} the name
     * of its catalog category.
     */
    record Found(Product product, String categoryName) implements CatalogMatch {
    }

    /** No product of the store's catalogs carries any of the EANs. */
    record NotInCatalog() implements CatalogMatch {
    }

    /** The offer's EANs lead to catalog products with different EANs, in catalog order: different goods. */
    record Ambiguous(List<Product> products) implements CatalogMatch {
    }
}
