package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;
import pl.commercelink.taxonomy.TaxonomyMerge;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedRowProcessor {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;

    List<InventoryItem> process(List<ParsedRow> rows, int taxonomyPenalty, FeedParseStats stats) {
        List<Candidate> candidates = correct(rows, stats);
        if (candidates.isEmpty()) {
            return List.of();
        }

        TaxonomyMerge merge = taxonomyCache.openMerge(candidates.stream().map(c -> c.product().mfn()).toList());
        List<InventoryItem> accepted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Taxonomy record = StoreFeedTaxonomy.deprioritized(
                    enrichment.enrich(candidate.product(), merge.latest(candidate.product().mfn())), taxonomyPenalty);

            if (record.isProcessable()) {
                merge.apply(record);
                stats.markImported();
                if (!Taxonomy.hasCategory(candidate.product())) {
                    stats.markImportedCategorized();
                }
                accepted.add(candidate.item());
            } else if (enrichment.isPendingEligible(record)) {
                merge.apply(record);
                enrichment.addPending(record, mappingScopeOf(stats.supplierName()));
                stats.markCategorizationScheduled();
            } else if (enrichment.hasIdentificationData(record)) {
                stats.markCategorizationPostponed();
            } else {
                stats.markIncomplete();
            }
        }
        taxonomyCache.commit(merge);
        return accepted;
    }

    private List<Candidate> correct(List<ParsedRow> rows, FeedParseStats stats) {
        List<Candidate> candidates = new ArrayList<>(rows.size());
        for (ParsedRow parsed : rows) {
            InventoryItem item = dataCorrection.run(parsed.item());
            Taxonomy product = dataCorrection.run(parsed.product());
            if (item == null || product == null || !item.isSellable()) {
                stats.markInvalid();
                continue;
            }
            candidates.add(new Candidate(item, product));
        }
        return candidates;
    }

    // Category mappings are learned per adapter type; manual feeds are distinct per connection.
    private static String mappingScopeOf(String identity) {
        return SupplierIdentity.isManual(identity) ? identity : SupplierIdentity.typeOf(identity);
    }

    private record Candidate(InventoryItem item, Taxonomy product) {
    }
}
