package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.Product;

import java.util.List;
import java.util.stream.Collectors;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

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

        /**
         * True when the hits that have a manufacturer code all share it, so filling the missing code in the catalog is
         * what resolves the conflict, not fixing a duplicated EAN.
         */
        public boolean onlyMissingCodes() {
            return distinctCodes(products) <= 1;
        }

        /**
         * True when one EAN alone is carried by catalog products with different (or missing) manufacturer codes: a
         * duplicate in the catalog, which only a catalog fix resolves. False when each EAN leads to one product and only
         * the offer's several EANs point at different ones, which an operator settles by linking the right product.
         */
        public boolean sharedEanInCatalog() {
            return products.stream()
                    .collect(Collectors.groupingBy(product -> String.valueOf(OfferEans.unify(product.getEan()))))
                    .values().stream()
                    .anyMatch(sameEan -> sameEan.stream()
                            .map(product -> StringUtils.defaultString(unifyMfn(product.getManufacturerCode())))
                            .distinct()
                            .count() > 1);
        }

        private static long distinctCodes(List<Product> products) {
            return products.stream()
                    .map(product -> unifyMfn(product.getManufacturerCode()))
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .count();
        }
    }

    /** The hits, in catalog order, have no manufacturer code, which an order line needs as its sku. */
    record NoManufacturerCode(List<Product> products) implements CatalogMatch {
    }
}
