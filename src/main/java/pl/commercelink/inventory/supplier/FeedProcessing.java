package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import pl.commercelink.inventory.SharedManufacturerCodes;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedProcessing {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;
    private final FeedParseStats stats;
    private final int taxonomyPenalty;
    private final List<InventoryItem> sellableOffers = new ArrayList<>();
    private final List<InventoryItem> imported = new ArrayList<>();
    private final List<WaitingRow> waitingForCodeIndex = new ArrayList<>();

    void add(ParsedRow parsed) {
        InventoryItem item = dataCorrection.run(parsed.item());
        if (!item.isSellable()) {
            stats.markInvalid();
            return;
        }
        sellableOffers.add(item);
        Taxonomy corrected = dataCorrection.run(parsed.product(), item);
        Taxonomy taxonomy = enrichment.enrichFromOwnRecord(corrected);
        if (TaxonomyCache.hasCategory(taxonomy)) {
            settle(item, corrected, taxonomy);
        } else {
            waitingForCodeIndex.add(new WaitingRow(item, corrected));
        }
    }

    void skipInvalid() {
        stats.markInvalid();
    }

    List<InventoryItem> finish() {
        SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(sellableOffers);
        for (InventoryItem offer : sharedCodes.offersUnderSharedCode()) {
            taxonomyCache.blockCodeSource(offer.mfn(), offer.brand(), offer.ean());
        }
        for (WaitingRow row : waitingForCodeIndex) {
            Taxonomy taxonomy = sharedCodes.isShared(row.item())
                    ? enrichment.enrichFromOwnRecord(row.corrected())
                    : enrichment.enrich(row.corrected());
            settle(row.item(), row.corrected(), taxonomy);
        }
        stats.log();
        return imported;
    }

    private void settle(InventoryItem item, Taxonomy corrected, Taxonomy taxonomy) {
        Taxonomy deprioritized = StoreFeedTaxonomy.deprioritized(taxonomy, taxonomyPenalty);
        if (taxonomy.isProcessable()) {
            taxonomyCache.add(deprioritized);
            stats.markImported();
            if (!TaxonomyCache.hasCategory(corrected)) {
                stats.markImportedCategorized();
            }
            imported.add(item);
            return;
        }

        if (enrichment.isPendingEligible(taxonomy)) {
            enrichment.addPending(deprioritized, mappingScopeOf(stats.supplierName()));
            stats.markCategorizationScheduled();
        } else if (enrichment.hasIdentificationData(taxonomy)) {
            stats.markCategorizationPostponed();
        } else {
            stats.markIncomplete();
        }
    }

    // Category mappings are learned per adapter type; manual feeds are distinct per connection.
    private static String mappingScopeOf(String identity) {
        return SupplierIdentity.isManual(identity) ? identity : SupplierIdentity.typeOf(identity);
    }

    private record WaitingRow(InventoryItem item, Taxonomy corrected) {
    }
}
