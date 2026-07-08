package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.Taxonomy;

@Component
public class TaxonomyResolver {

    private final TaxonomyCache taxonomyCache;

    public TaxonomyResolver(TaxonomyCache taxonomyCache) {
        this.taxonomyCache = taxonomyCache;
    }

    public ResolvedProduct resolve(String mfn, String fallbackName, String fallbackCategory) {
        Taxonomy taxonomy = taxonomyCache.findByMfn(mfn);

        if (taxonomy == null) {
            return new ResolvedProduct(mfn, fallbackName, fallbackCategory);
        }

        String name = taxonomy.name() != null && !taxonomy.name().isEmpty()
                ? taxonomy.name()
                : fallbackName;

        String categoryKey = taxonomy.category();
        String category = categoryKey != null && !Categorized.OTHER.equals(categoryKey)
                ? categoryKey
                : fallbackCategory;

        return new ResolvedProduct(mfn, name, category);
    }

    public record ResolvedProduct(String mfn, String name, String category) {}
}
