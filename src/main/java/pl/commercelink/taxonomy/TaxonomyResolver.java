package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;

@Component
public class TaxonomyResolver {

    private final TaxonomyCache taxonomyCache;

    public TaxonomyResolver(TaxonomyCache taxonomyCache) {
        this.taxonomyCache = taxonomyCache;
    }

    public ResolvedProduct resolve(String ean, String fallbackName, String fallbackCategory) {
        Taxonomy taxonomy = taxonomyCache.findByEan(ean);

        if (taxonomy == null) {
            return new ResolvedProduct(fallbackName, fallbackCategory, null);
        }

        String name = taxonomy.name() != null && !taxonomy.name().isEmpty()
                ? taxonomy.name()
                : fallbackName;

        String categoryKey = taxonomy.category();
        String category = categoryKey != null && !categoryKey.isBlank()
                ? categoryKey
                : fallbackCategory;

        return new ResolvedProduct(name, category, taxonomy.categoryId());
    }

    public record ResolvedProduct(String name, String category, String categoryId) {}
}
