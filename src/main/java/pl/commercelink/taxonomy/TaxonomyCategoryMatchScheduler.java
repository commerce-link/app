package pl.commercelink.taxonomy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.concurrent.atomic.AtomicInteger;

@Component
class TaxonomyCategoryMatchScheduler {

    private final TaxonomyCache taxonomyCache;
    private final PimCatalog pimCatalog;
    private final TaxonomyCategoryMatchProperties properties;
    private final TaxonomyCategoryEnrichment enrichment;
    private final CategoryMappingCache mappingCache;
    private final AtomicInteger sweepCounter = new AtomicInteger();

    TaxonomyCategoryMatchScheduler(TaxonomyCache taxonomyCache, PimCatalog pimCatalog,
                                   TaxonomyCategoryMatchProperties properties,
                                   TaxonomyCategoryEnrichment enrichment,
                                   CategoryMappingCache mappingCache) {
        this.taxonomyCache = taxonomyCache;
        this.pimCatalog = pimCatalog;
        this.properties = properties;
        this.enrichment = enrichment;
        this.mappingCache = mappingCache;
    }

    @Scheduled(cron = "${taxonomy.category-match.sweep-cron:0 2-57/5 * * * ?}")
    void sweep() {
        int bucket = Math.floorMod(sweepCounter.getAndIncrement(), properties.buckets());
        int pendingTotal = 0;
        int submitted = 0;
        int resolvedFromMapping = 0;
        for (Taxonomy taxonomy : taxonomyCache.getTaxonomies()) {
            if (TaxonomyCache.hasCategory(taxonomy)) {
                continue;
            }
            pendingTotal++;
            if (resolveFromMapping(taxonomy)) {
                resolvedFromMapping++;
                continue;
            }
            if (Math.floorMod(taxonomy.mfn().hashCode(), properties.buckets()) != bucket) {
                continue;
            }
            try {
                pimCatalog.submitCategoryMatch(new CategoryMatchRequest(
                        null, taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(), taxonomy.rawCategory()));
                submitted++;
            } catch (IllegalStateException e) {
                System.out.println("Category match sweep aborted: " + e.getMessage());
                return;
            }
        }
        if (pendingTotal > 0) {
            System.out.println("Category match sweep: bucket=" + bucket
                    + " pending=" + pendingTotal + " submitted=" + submitted
                    + " resolvedFromMapping=" + resolvedFromMapping);
        }
    }

    private boolean resolveFromMapping(Taxonomy taxonomy) {
        if (Math.floorMod(taxonomy.mfn().hashCode(), properties.mappingTrickleEvery()) == 0) {
            return false;
        }
        String supplier = enrichment.supplierOf(taxonomy.mfn());
        if (supplier == null) {
            return false;
        }
        return mappingCache.findActive(supplier, taxonomy.rawCategory())
                .map(mapping -> taxonomyCache.updateCategory(taxonomy.mfn(), mapping.categoryName(), mapping.categoryId()))
                .orElse(false);
    }
}
