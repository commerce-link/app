package pl.commercelink.products;

import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Product data of a product known only from the taxonomy, the way {@link ProductDataResolver} answers for it. */
public final class ProductDataFixture {

    private ProductDataFixture() {
    }

    public static ProductData from(Taxonomy taxonomy) {
        return new ProductData(taxonomy.name(), taxonomy.brand(), taxonomy.category(), taxonomy.categoryId(),
                taxonomy.ean(), taxonomy.mfn(), taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams());
    }

    public static ResolvedProduct resolved(Taxonomy taxonomy) {
        return taxonomy == null || taxonomy == Taxonomy.EMPTY
                ? ResolvedProduct.UNKNOWN
                : new ResolvedProduct(from(taxonomy), taxonomy);
    }

    /** A resolver without a PIM: every group is described by what the cache finds for it, one call per list. */
    public static ProductDataResolver resolverOver(TaxonomyCache taxonomyCache) {
        ProductDataResolver resolver = mock(ProductDataResolver.class);
        answerFromTaxonomy(resolver, taxonomyCache);
        return resolver;
    }

    public static void answerFromTaxonomy(ProductDataResolver resolver, TaxonomyCache taxonomyCache) {
        lenient().when(resolver.ofGroups(any(), any())).thenAnswer(invocation -> {
            List<InventoryKey> groups = invocation.getArgument(1);
            return groups.stream().map(group -> resolved(taxonomyCache.find(group))).toList();
        });
    }
}
