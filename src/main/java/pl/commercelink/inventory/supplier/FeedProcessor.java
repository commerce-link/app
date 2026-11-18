package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryMatchingProperties;
import pl.commercelink.inventory.VariantCodes;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class FeedProcessor {

    private final DataCorrection dataCorrection;
    private final TaxonomyCache taxonomyCache;
    private final TaxonomyCategoryEnrichment enrichment;
    private final InventoryMatchingProperties matching;

    FeedProcessing start(String supplierName, int taxonomyPenalty, VariantCodes extraEanOnly) {
        return new FeedProcessing(dataCorrection, taxonomyCache, enrichment, new FeedParseStats(supplierName), taxonomyPenalty,
                matching.ignorePlaceholderEans(), extraEanOnly);
    }
}
