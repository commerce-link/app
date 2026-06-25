package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.Taxonomy;

@Component
public class TaxonomyResolver {

    private final TaxonomyCache taxonomyCache;

    public TaxonomyResolver(TaxonomyCache taxonomyCache) {
        this.taxonomyCache = taxonomyCache;
    }

    public ResolvedProduct resolve(String mfn, String fallbackName, String fallbackCategoryKey) {
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);

        if (taxonomy == null) {
            return new ResolvedProduct(mfn, fallbackName, fallbackCategoryKey);
        }

        String name = taxonomy.name() != null && !taxonomy.name().isEmpty()
                ? taxonomy.name()
                : fallbackName;

        String categoryKey = taxonomy.categoryKey() != null && !ProductCategory.Other.name().equals(taxonomy.categoryKey())
                ? taxonomy.categoryKey()
                : fallbackCategoryKey;

        return new ResolvedProduct(mfn, name, categoryKey);
    }

    public record ResolvedProduct(String mfn, String name, String categoryKey) {}
}
