package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.inventory.SharedManufacturerCodes;
import pl.commercelink.inventory.VariantCodes;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedProcessing {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;
    private final FeedParseStats stats;
    private final int taxonomyPenalty;
    private final boolean ignorePlaceholderEans;
    private final VariantCodes extraEanOnly;
    private final List<ParsedRow> rows = new ArrayList<>();
    private final List<InventoryItem> sellableOffers = new ArrayList<>();
    private final List<InventoryItem> imported = new ArrayList<>();
    private final List<WaitingRow> waitingForCodeIndex = new ArrayList<>();

    // A placeholder EAN is known only once the whole feed is read, so rows wait for finish() only while that guard is on.
    void add(ParsedRow parsed) {
        if (ignorePlaceholderEans) {
            rows.add(parsed);
        } else {
            process(parsed, PlaceholderEans.none());
        }
    }

    private boolean process(ParsedRow parsed, PlaceholderEans placeholderEans) {
        InventoryItem raw = parsed.item();
        boolean placeholder = placeholderEans.contains(raw.ean());
        // Dropping the number lets the PIM supply the real one from the manufacturer code.
        InventoryItem item = dataCorrection.run(placeholder ? raw.withBrandAndEan(raw.brand(), null) : raw, extraEanOnly);
        if (!item.isSellable() || placeholderEans.contains(item.ean())) {
            stats.markInvalid();
            return false;
        }
        sellableOffers.add(item);
        Taxonomy corrected = dataCorrection.run(parsed.product(), item);
        Taxonomy taxonomy = enrichment.enrichFromOwnRecord(corrected);
        if (TaxonomyCache.hasCategory(taxonomy)) {
            settle(item, corrected, taxonomy);
        } else {
            waitingForCodeIndex.add(new WaitingRow(item, corrected));
        }
        return true;
    }

    void skipInvalid() {
        stats.markInvalid();
    }

    List<InventoryItem> finish() {
        PlaceholderEans placeholderEans = PlaceholderEans.of(rows.stream().map(ParsedRow::item).toList());
        int rejectedUnderPlaceholder = 0;
        for (ParsedRow row : rows) {
            if (!process(row, placeholderEans) && placeholderEans.contains(row.item().ean())) {
                rejectedUnderPlaceholder++;
            }
        }
        if (placeholderEans.size() > 0) {
            log.warn("Feed of {} has {} EANs shared by {} or more manufacturer codes; {} rows under them were skipped",
                    stats.supplierName(), placeholderEans.size(), PlaceholderEans.MIN_CODES, rejectedUnderPlaceholder);
        }
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
