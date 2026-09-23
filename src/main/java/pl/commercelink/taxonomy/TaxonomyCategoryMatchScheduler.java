package pl.commercelink.taxonomy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
class TaxonomyCategoryMatchScheduler {

    private final PendingCategorizationRepository pendingRepository;
    private final TaxonomyCatalog catalog;
    private final PimCatalog pimCatalog;
    private final TaxonomyCategoryMatchProperties properties;
    private final TaxonomyCategoryEnrichment enrichment;
    private final CategoryMappingCache mappingCache;

    TaxonomyCategoryMatchScheduler(PendingCategorizationRepository pendingRepository, TaxonomyCatalog catalog,
                                   PimCatalog pimCatalog, TaxonomyCategoryMatchProperties properties,
                                   TaxonomyCategoryEnrichment enrichment, CategoryMappingCache mappingCache) {
        this.pendingRepository = pendingRepository;
        this.catalog = catalog;
        this.pimCatalog = pimCatalog;
        this.properties = properties;
        this.enrichment = enrichment;
        this.mappingCache = mappingCache;
    }

    @Scheduled(cron = "${taxonomy.category-match.sweep-cron:0 2-57/5 * * * ?}")
    void sweep() {
        List<PendingCategorization> pending = new ArrayList<>(pendingRepository.findAll());
        enrichment.pendingCountIs(pending.size());
        if (pending.isEmpty()) {
            return;
        }

        Map<String, Taxonomy> byMfn = catalog.findByMfns(pending.stream().map(PendingCategorization::getMfn).toList());
        Collections.shuffle(pending);

        int submitted = 0;
        int resolvedFromMapping = 0;
        int alreadyCategorized = 0;
        int givenUp = 0;
        for (PendingCategorization entry : pending) {
            Taxonomy taxonomy = byMfn.get(entry.getMfn());
            if (taxonomy == null || Taxonomy.hasCategory(taxonomy)) {
                enrichment.forget(entry.getMfn());
                alreadyCategorized++;
                continue;
            }
            if (resolveFromMapping(entry, taxonomy)) {
                resolvedFromMapping++;
                continue;
            }
            if (exhausted(entry)) {
                givenUp++;
                continue;
            }
            if (submitted >= properties.maxSubmissionsPerRun()) {
                continue;
            }
            try {
                pimCatalog.submitCategoryMatch(new CategoryMatchRequest(
                        null, taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(), taxonomy.rawCategory()));
                submitted++;
                pendingRepository.recordAttempt(entry.getMfn());
            } catch (IllegalStateException e) {
                log.warn("Category match sweep aborted: {}", e.getMessage());
                return;
            }
        }
        log.info("Category match sweep: pending={} submitted={} resolvedFromMapping={} alreadyCategorized={} givenUp={}",
                pending.size(), submitted, resolvedFromMapping, alreadyCategorized, givenUp);
    }

    private boolean exhausted(PendingCategorization entry) {
        return properties.maxAttempts() > 0 && entry.attemptCount() >= properties.maxAttempts();
    }

    private boolean resolveFromMapping(PendingCategorization entry, Taxonomy taxonomy) {
        if (Math.floorMod(taxonomy.mfn().hashCode(), properties.mapping().trickleEvery()) == 0) {
            return false;
        }
        String supplier = entry.getSupplier();
        if (supplier == null) {
            return false;
        }
        boolean resolved = mappingCache.findActive(supplier, taxonomy.rawCategory())
                .map(mapping -> catalog.updateCategory(taxonomy.mfn(), mapping.categoryName(), mapping.categoryId()))
                .orElse(false);
        if (resolved) {
            enrichment.forget(taxonomy.mfn());
        }
        return resolved;
    }
}
