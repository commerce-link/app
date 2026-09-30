package pl.commercelink.taxonomy;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class TaxonomyCategoryMatchSweep {

    private final TaxonomyCache taxonomyCache;
    private final PimCatalog pimCatalog;
    private final TaxonomyCategoryMatchProperties properties;
    private final TaxonomyCategoryEnrichment enrichment;
    private final CategoryMappingCache mappingCache;
    private final CategoryMatchAttempts attempts;
    // Per instance on purpose: the queue hands each tick to a single instance, and a local counter still walks
    // that instance through every bucket of its own in-memory pending rows, however the ticks are split.
    private final AtomicInteger sweepCounter = new AtomicInteger();

    void sweep() {
        int bucket = Math.floorMod(sweepCounter.getAndIncrement(), properties.buckets());
        int pendingTotal = 0;
        int submitted = 0;
        int resolvedFromMapping = 0;
        int givenUp = 0;
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
            if (attempts.exhausted(taxonomy.mfn(), properties.maxAttempts())) {
                givenUp++;
                continue;
            }
            try {
                pimCatalog.submitCategoryMatch(new CategoryMatchRequest(
                        null, taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(), taxonomy.rawCategory()));
                submitted++;
                attempts.record(taxonomy.mfn());
            } catch (IllegalStateException e) {
                log.warn("Category match sweep aborted: {}", e.getMessage());
                return;
            }
        }
        if (pendingTotal > 0) {
            log.info("Category match sweep: bucket={} pending={} submitted={} resolvedFromMapping={} givenUp={}",
                    bucket, pendingTotal, submitted, resolvedFromMapping, givenUp);
        }
    }

    private boolean resolveFromMapping(Taxonomy taxonomy) {
        if (Math.floorMod(taxonomy.mfn().hashCode(), properties.mapping().trickleEvery()) == 0) {
            return false;
        }
        String supplier = enrichment.supplierOf(taxonomy.mfn());
        if (supplier == null) {
            return false;
        }
        boolean resolved = mappingCache.findActive(supplier, taxonomy.rawCategory())
                .map(mapping -> taxonomyCache.updateCategory(taxonomy.mfn(), mapping.categoryName(), mapping.categoryId()))
                .orElse(false);
        if (resolved) {
            attempts.clear(taxonomy.mfn());
        }
        return resolved;
    }
}
