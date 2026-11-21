package pl.commercelink.products;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ProductDataResolver {

    private final PimCatalog pimCatalog;
    private final TaxonomyCache taxonomyCache;
    private final boolean skipUnapprovedGlobalEntries;

    public ProductDataResolver(PimCatalog pimCatalog, TaxonomyCache taxonomyCache) {
        this(pimCatalog, taxonomyCache, true);
    }

    @Autowired
    public ProductDataResolver(PimCatalog pimCatalog, TaxonomyCache taxonomyCache,
                               @Value("${product-data.skip-unapproved-global-entries:true}") boolean skipUnapprovedGlobalEntries) {
        this.pimCatalog = pimCatalog;
        this.taxonomyCache = taxonomyCache;
        this.skipUnapprovedGlobalEntries = skipUnapprovedGlobalEntries;
    }

    public Optional<ProductData> ofEan(String storeId, String ean) {
        return resolve(pim(storeId).findByGtin(ean), taxonomyCache.findByEan(ean));
    }

    public Optional<ProductData> ofGroup(String storeId, InventoryKey group) {
        return resolve(entryOf(pim(storeId), group), taxonomyCache.find(group));
    }

    /**
     * One answer per group, in the groups' order, with the taxonomy read once for all of them. A group nothing is
     * known about gives {@link ResolvedProduct#UNKNOWN}.
     */
    public List<ResolvedProduct> ofGroups(String storeId, List<InventoryKey> groups) {
        PimView pim = pim(storeId);
        List<Taxonomy> taxonomies = taxonomyCache.findAll(groups);
        List<ResolvedProduct> resolved = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            Taxonomy taxonomy = taxonomies.get(i);
            resolved.add(resolve(entryOf(pim, groups.get(i)), taxonomy)
                    .map(data -> new ResolvedProduct(data, taxonomy))
                    .orElse(ResolvedProduct.UNKNOWN));
        }
        return resolved;
    }

    public Optional<ProductData> ofPimId(String storeId, String pimId) {
        return pim(storeId).findByPimId(pimId)
                .flatMap(entry -> resolve(Optional.of(entry), taxonomyCache.find(InventoryKey.fromPimEntry(entry))));
    }

    private static Optional<PimEntry> entryOf(PimView pim, InventoryKey group) {
        return pim.findByPimIdOrGtins(group.getId(), group.getProductEans());
    }

    private PimView pim(String storeId) {
        return storeId == null ? pimCatalog.withGlobalData() : pimCatalog.withStoreData(storeId);
    }

    private Optional<ProductData> resolve(Optional<PimEntry> entry, Taxonomy taxonomy) {
        Optional<ProductData> fromTaxonomy = Optional.ofNullable(taxonomy)
                .filter(found -> found != Taxonomy.EMPTY)
                .map(ProductDataResolver::of);
        return entry.filter(this::trusted)
                .map(ProductDataResolver::of)
                .map(fromPim -> fromTaxonomy.map(fromPim::orElse).orElse(fromPim))
                .or(() -> fromTaxonomy);
    }

    // Client decision (pending): a global entry nobody has reviewed in the PIM is not trusted, and the taxonomy answers
    // as if the entry were not there. A store's own entry is never skipped: the PIM marks it reviewed when the store
    // creates it, so for those entries the flag says nothing about the data.
    private boolean trusted(PimEntry entry) {
        return !skipUnapprovedGlobalEntries || !entry.global() || entry.approved();
    }

    private static ProductData of(PimEntry entry) {
        return new ProductData(entry.name(), entry.brand(), entry.category(), entry.categoryId(),
                entry.primaryGtin().orElse(null), entry.primaryMpn().orElse(null),
                entry.netWeightInGrams(), entry.grossWeightInGrams());
    }

    private static ProductData of(Taxonomy taxonomy) {
        return new ProductData(taxonomy.name(), taxonomy.brand(), taxonomy.category(), taxonomy.categoryId(),
                taxonomy.ean(), taxonomy.mfn(), taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams());
    }
}
