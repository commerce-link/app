package pl.commercelink.taxonomy;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;

import java.util.concurrent.atomic.AtomicInteger;

@Component
class TaxonomyCategoryMatchScheduler {

    private final TaxonomyCache taxonomyCache;
    private final PimCatalog pimCatalog;
    private final TaxonomyCategoryMatchProperties properties;
    private final AtomicInteger sweepCounter = new AtomicInteger();

    TaxonomyCategoryMatchScheduler(TaxonomyCache taxonomyCache, PimCatalog pimCatalog,
                                   TaxonomyCategoryMatchProperties properties) {
        this.taxonomyCache = taxonomyCache;
        this.pimCatalog = pimCatalog;
        this.properties = properties;
    }

    @Scheduled(cron = "${taxonomy.category-match.sweep-cron:0 2-57/5 * * * ?}")
    void sweep() {
        if (!properties.enabled()) {
            return;
        }
        int bucket = Math.floorMod(sweepCounter.getAndIncrement(), properties.buckets());
        int pendingTotal = 0;
        int submitted = 0;
        for (Taxonomy taxonomy : taxonomyCache.getTaxonomies()) {
            if (TaxonomyCache.hasCategory(taxonomy)) {
                continue;
            }
            pendingTotal++;
            if (Math.floorMod(taxonomy.mfn().hashCode(), properties.buckets()) != bucket) {
                continue;
            }
            try {
                pimCatalog.submitCategoryMatch(new CategoryMatchRequest(
                        null, taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(), null));
                submitted++;
            } catch (IllegalStateException e) {
                System.out.println("Category match sweep aborted: " + e.getMessage());
                return;
            }
        }
        if (pendingTotal > 0) {
            System.out.println("Category match sweep: bucket=" + bucket
                    + " pending=" + pendingTotal + " submitted=" + submitted);
        }
    }
}
