package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchedEvent;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class TaxonomyCategoryEnrichment {

    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryMatchProperties properties;
    private final CategoryMappingCache mappingCache;
    private final ConcurrentHashMap<String, String> supplierByMfn = new ConcurrentHashMap<>();

    TaxonomyCategoryEnrichment(TaxonomyCache taxonomyCache, TaxonomyCategoryMatchProperties properties,
                               CategoryMappingCache mappingCache) {
        this.taxonomyCache = taxonomyCache;
        this.properties = properties;
        this.mappingCache = mappingCache;
    }

    public Taxonomy enrich(Taxonomy taxonomy) {
        if (TaxonomyCache.hasCategory(taxonomy) || taxonomy.mfn() == null || taxonomy.mfn().isEmpty()) {
            return taxonomy;
        }
        Taxonomy cached = taxonomyCache.findByMfn(taxonomy.mfn());
        if (cached == null || !TaxonomyCache.hasCategory(cached)) {
            return taxonomy;
        }
        return new Taxonomy(taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(),
                cached.category(), taxonomy.dataAccuracyScore(),
                taxonomy.netWeightInGrams(), taxonomy.grossWeightInGrams(),
                taxonomy.rawCategory(), cached.categoryId());
    }

    public boolean isPendingEligible(Taxonomy taxonomy) {
        return isNotBlank(taxonomy.mfn()) && isNotBlank(taxonomy.ean())
                && isNotBlank(taxonomy.brand()) && isNotBlank(taxonomy.name())
                && taxonomyCache.pendingCount() < properties.pendingCap();
    }

    public void addPending(Taxonomy taxonomy, String supplier) {
        if (isNotBlank(supplier) && isNotBlank(taxonomy.mfn())) {
            supplierByMfn.put(taxonomy.mfn(), supplier);
        }
        taxonomyCache.add(taxonomy);
    }

    public String supplierOf(String mfn) {
        return supplierByMfn.get(mfn);
    }

    public void applyMatch(CategoryMatchedEvent event) {
        if (event == null || event.mfn() == null
                || event.category() == null || event.category().isBlank()) {
            return;
        }
        if (taxonomyCache.updateCategory(event.mfn(), event.category(), event.categoryId())) {
            System.out.println("Category match applied: mfn=" + event.mfn()
                    + " category=" + event.category() + " source=" + event.source());
            learnMapping(event);
        }
    }

    private void learnMapping(CategoryMatchedEvent event) {
        String supplier = supplierByMfn.remove(event.mfn());
        if (supplier == null || isBlank(event.categoryId())) {
            return;
        }
        if (event.confidence() != null && event.confidence() < properties.mappingMinConfidence()) {
            return;
        }
        Taxonomy taxonomy = taxonomyCache.findByMfn(event.mfn());
        if (taxonomy == null || isBlank(taxonomy.rawCategory())) {
            return;
        }
        mappingCache.recordSample(supplier, taxonomy.rawCategory(), event.categoryId(), event.category());
    }

    public int pendingCount() {
        return taxonomyCache.pendingCount();
    }
}
