package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductDataResolver {

    private final PimCatalog pimCatalog;
    private final TaxonomyCache taxonomyCache;

    public Optional<ProductData> ofEan(String storeId, String ean) {
        return resolve(pim(storeId).findByGtin(ean), taxonomyCache.findByEan(ean));
    }

    public Optional<ProductData> ofGroup(String storeId, InventoryKey group) {
        return resolve(pim(storeId).findByPimIdOrGtins(group.getId(), group.getProductEans()), taxonomyCache.find(group));
    }

    public Optional<ProductData> ofPimId(String storeId, String pimId) {
        return pim(storeId).findByPimId(pimId)
                .flatMap(entry -> resolve(Optional.of(entry), taxonomyCache.find(InventoryKey.fromPimEntry(entry))));
    }

    private PimView pim(String storeId) {
        return storeId == null ? pimCatalog.withGlobalData() : pimCatalog.withStoreData(storeId);
    }

    private static Optional<ProductData> resolve(Optional<PimEntry> entry, Taxonomy taxonomy) {
        Optional<ProductData> fromTaxonomy = Optional.ofNullable(taxonomy)
                .filter(found -> found != Taxonomy.EMPTY)
                .map(ProductDataResolver::of);
        return entry.map(ProductDataResolver::of)
                .map(fromPim -> fromTaxonomy.map(fromPim::orElse).orElse(fromPim))
                .or(() -> fromTaxonomy);
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
