package pl.commercelink.marketplace.links;

import pl.commercelink.products.Product;

import java.util.List;

/**
 * What the store's catalogs say about a set of EANs (an offer link's, or a marketplace offer's): one product, nothing,
 * or a conflict that leaves the offer to an operator.
 */
public sealed interface CatalogMatch {

    /**
     * The hits share one manufacturer code; {@code product} is the first of them in catalog order, and
     * {@code categoryName} the name of its catalog category.
     */
    record Found(Product product, String categoryName) implements CatalogMatch {
    }

    /** No product of the store's catalogs carries any of the EANs. */
    record NotInCatalog() implements CatalogMatch {
    }

    /** The hits, in catalog order, carry more than one manufacturer code (a missing code counts as one of them). */
    record Ambiguous(List<Product> products) implements CatalogMatch {
    }

    /** The hits, in catalog order, have no manufacturer code, which an order line needs as its sku. */
    record NoManufacturerCode(List<Product> products) implements CatalogMatch {
    }
}
