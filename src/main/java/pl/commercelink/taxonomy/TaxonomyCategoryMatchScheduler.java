package pl.commercelink.taxonomy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
class TaxonomyCategoryMatchScheduler {

    private static final Comparator<PendingCategorization> LEAST_TRIED_FIRST =
            Comparator.comparingInt(PendingCategorization::attemptCount)
                    .thenComparing(PendingCategorization::getAddedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(PendingCategorization::getMfn);

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
        List<PendingCategorization> pending = pendingRepository.findAll();
        if (pending.isEmpty()) {
            enrichment.pendingCountIs(0);
            return;
        }

        Map<String, Taxonomy> byMfn = catalog.findByMfns(pending.stream().map(PendingCategorization::getMfn).toList());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minus(properties.retryExhaustedAfter());
        LocalDateTime cooledDownBefore = now.minus(properties.retryAfter());

        List<PendingCategorization> candidates = new ArrayList<>();
        int resolvedFromMapping = 0;
        int alreadyCategorized = 0;
        int awaitingFeed = 0;
        int givenUp = 0;
        int evicted = 0;
        int coolingDown = 0;

        for (PendingCategorization entry : pending) {
            Taxonomy taxonomy = byMfn.get(entry.getMfn());
            if (taxonomy == null) {
                if (addedBefore(entry, staleBefore)) {
                    enrichment.forget(entry.getMfn());
                    evicted++;
                } else {
                    awaitingFeed++;
                }
                continue;
            }
            if (Taxonomy.hasCategory(taxonomy)) {
                enrichment.forget(entry.getMfn());
                alreadyCategorized++;
                continue;
            }
            if (resolveFromMapping(entry, taxonomy)) {
                resolvedFromMapping++;
                continue;
            }
            if (exhausted(entry)) {
                if (addedBefore(entry, staleBefore)) {
                    enrichment.forget(entry.getMfn());
                    evicted++;
                } else {
                    givenUp++;
                }
                continue;
            }
            if (attemptedAfter(entry, cooledDownBefore)) {
                coolingDown++;
                continue;
            }
            candidates.add(entry);
        }

        enrichment.pendingCountIs(candidates.size() + awaitingFeed + coolingDown);
        candidates.sort(LEAST_TRIED_FIRST);

        int submitted = 0;
        for (PendingCategorization entry : candidates) {
            if (submitted >= properties.maxSubmissionsPerRun()) {
                break;
            }
            int attemptsBeforeClaim = entry.attemptCount();
            if (!pendingRepository.claimAttempt(entry.getMfn(), attemptsBeforeClaim, now)) {
                continue;
            }
            Taxonomy taxonomy = byMfn.get(entry.getMfn());
            try {
                pimCatalog.submitCategoryMatch(new CategoryMatchRequest(
                        null, taxonomy.ean(), taxonomy.mfn(), taxonomy.brand(), taxonomy.name(), taxonomy.rawCategory()));
                submitted++;
            } catch (IllegalStateException e) {
                pendingRepository.releaseAttempt(entry.getMfn(), attemptsBeforeClaim);
                log.warn("Category match sweep aborted: {}", e.getMessage());
                return;
            }
        }
        log.info("Category match sweep: pending={} submitted={} resolvedFromMapping={} alreadyCategorized={}"
                        + " awaitingFeed={} coolingDown={} givenUp={} evicted={}",
                pending.size(), submitted, resolvedFromMapping, alreadyCategorized, awaitingFeed, coolingDown,
                givenUp, evicted);
    }

    private boolean exhausted(PendingCategorization entry) {
        return properties.maxAttempts() > 0 && entry.attemptCount() >= properties.maxAttempts();
    }

    private static boolean addedBefore(PendingCategorization entry, LocalDateTime moment) {
        return entry.getAddedAt() == null || entry.getAddedAt().isBefore(moment);
    }

    private static boolean attemptedAfter(PendingCategorization entry, LocalDateTime moment) {
        return entry.getLastAttemptAt() != null && entry.getLastAttemptAt().isAfter(moment);
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
