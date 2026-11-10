package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedProcessor {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;

    FeedProcessing start(String supplierName, int taxonomyPenalty) {
        return new FeedProcessing(dataCorrection, taxonomyCache, enrichment, new FeedParseStats(supplierName), taxonomyPenalty);
    }
}
