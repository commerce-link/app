package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.Optional;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedRowProcessor {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;

    Optional<InventoryItem> process(ParsedRow parsed, String supplierName, int taxonomyPenalty, FeedParseStats stats) {
        InventoryItem item = dataCorrection.run(parsed.item());
        Taxonomy corrected = dataCorrection.run(parsed.taxonomy());
        if (item == null || corrected == null || !item.isSellable()) {
            return Optional.empty();
        }

        Taxonomy taxonomy = enrichment.enrich(corrected);
        if (!TaxonomyCache.hasCategory(corrected) && TaxonomyCache.hasCategory(taxonomy)) {
            stats.markAdopted();
        }

        Taxonomy deprioritized = StoreFeedTaxonomy.deprioritized(taxonomy, taxonomyPenalty);
        if (taxonomy.isProcessable()) {
            taxonomyCache.add(deprioritized);
            return Optional.of(item);
        }

        if (enrichment.isPendingEligible(supplierName, taxonomy)) {
            enrichment.addPending(deprioritized);
            stats.markPendingAdded();
        } else {
            stats.markDropped();
        }
        return Optional.empty();
    }
}
