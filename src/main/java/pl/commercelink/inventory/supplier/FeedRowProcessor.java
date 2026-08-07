package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.Optional;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedRowProcessor {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;

    Optional<InventoryItem> process(ParsedRow parsed, int taxonomyPenalty, FeedParseStats stats) {
        InventoryItem item = dataCorrection.run(parsed.item());
        Taxonomy corrected = dataCorrection.run(parsed.product());
        if (item == null || corrected == null || !item.isSellable()) {
            stats.markInvalid();
            return Optional.empty();
        }

        Taxonomy taxonomy = enrichment.enrich(corrected);
        Taxonomy deprioritized = StoreFeedTaxonomy.deprioritized(taxonomy, taxonomyPenalty);
        if (taxonomy.isProcessable()) {
            taxonomyCache.add(deprioritized);
            stats.markImported();
            if (!TaxonomyCache.hasCategory(corrected)) {
                stats.markImportedCategorized();
            }
            return Optional.of(item);
        }

        if (enrichment.isPendingEligible(taxonomy)) {
            enrichment.addPending(deprioritized, stats.supplierName());
            stats.markCategorizationScheduled();
        } else if (enrichment.hasIdentificationData(taxonomy)) {
            stats.markCategorizationPostponed();
        } else {
            stats.markIncomplete();
        }
        return Optional.empty();
    }
}
