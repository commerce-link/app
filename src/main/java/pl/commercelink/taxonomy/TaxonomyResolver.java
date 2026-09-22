package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;

@Component
public class TaxonomyResolver {

    private final TaxonomyCatalog catalog;

    public TaxonomyResolver(TaxonomyCatalog catalog) {
        this.catalog = catalog;
    }

    public ResolvedProduct resolve(String mfn, String fallbackName, String fallbackCategory) {
        Taxonomy taxonomy = catalog.findByMfn(mfn);

        if (taxonomy == null) {
            return new ResolvedProduct(mfn, fallbackName, fallbackCategory, null);
        }

        String name = taxonomy.name() != null && !taxonomy.name().isEmpty()
                ? taxonomy.name()
                : fallbackName;

        String categoryKey = taxonomy.category();
        String category = categoryKey != null && !categoryKey.isBlank()
                ? categoryKey
                : fallbackCategory;

        return new ResolvedProduct(mfn, name, category, taxonomy.categoryId());
    }

    public record ResolvedProduct(String mfn, String name, String category, String categoryId) {}
}
