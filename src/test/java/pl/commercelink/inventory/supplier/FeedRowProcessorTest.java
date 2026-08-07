package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.SupplierProduct;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedRowProcessorTest {

    @Mock
    private DataCorrection dataCorrection;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private TaxonomyCategoryEnrichment enrichment;
    @InjectMocks
    private FeedRowProcessor processor;

    private final InventoryItem sellableItem =
            new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Acme", true);
    private final SupplierProduct feedProduct =
            new SupplierProduct("1234567890123", "MFN-1", "Brand", "Name", 5, null, null);
    private final Taxonomy pendingTaxonomy =
            new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 5, null, null);
    private final Taxonomy categorizedTaxonomy =
            new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 5, null, null);

    @Test
    void processableRowGoesToCacheAndInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertEquals(Optional.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markImported();
        verify(stats, never()).markImportedCategorized();
        verify(stats, never()).markInvalid();
    }

    @Test
    void pendingEligibleRowGoesOnlyToPendingNotToInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        FeedParseStats stats = mock(FeedParseStats.class);
        when(stats.supplierName()).thenReturn("Acme");

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(enrichment).addPending(pendingTaxonomy, "Acme");
        verify(taxonomyCache, never()).add(any());
        verify(stats).markCategorizationScheduled();
        verify(stats, never()).markCategorizationPostponed();
        verify(stats, never()).markIncomplete();
        verify(stats, never()).markImportedCategorized();
    }

    @Test
    void pendingIneligibleRowWithCompleteDataIsDeferredToNextFeed() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(true);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(enrichment, never()).addPending(any(), any());
        verify(taxonomyCache, never()).add(any());
        verify(stats).markCategorizationPostponed();
        verify(stats, never()).markIncomplete();
        verify(stats, never()).markCategorizationScheduled();
    }

    @Test
    void pendingIneligibleRowWithMissingDataIsDropped() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(false);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(enrichment, never()).addPending(any(), any());
        verify(taxonomyCache, never()).add(any());
        verify(stats).markIncomplete();
        verify(stats, never()).markCategorizationPostponed();
        verify(stats, never()).markCategorizationScheduled();
    }

    @Test
    void adoptedCategoryFromCachePutsItemIntoInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertEquals(Optional.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markImportedCategorized();
        verify(stats).markImported();
    }

    @Test
    void adoptedCategoryOnIncompleteRowIsNotCountedAsImportedCategorized() {
        // given
        Taxonomy noBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", null, 5, null, null);
        Taxonomy enrichedNoBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", "CPU", 5, null, null);
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(noBrand);
        when(enrichment.enrich(noBrand)).thenReturn(enrichedNoBrand);
        when(enrichment.isPendingEligible(enrichedNoBrand)).thenReturn(false);
        when(enrichment.hasIdentificationData(enrichedNoBrand)).thenReturn(false);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(stats).markIncomplete();
        verify(stats, never()).markImportedCategorized();
        verify(stats, never()).markImported();
    }

    @Test
    void notSellableItemIsDroppedWithoutTouchingCache() {
        // given
        InventoryItem noQty = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 0, 1, "Acme", true);
        ParsedRow parsed = new ParsedRow(noQty, feedProduct);
        when(dataCorrection.run(noQty)).thenReturn(noQty);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(taxonomyCache, never()).add(any());
        verify(enrichment, never()).addPending(any(), any());
        verify(stats).markInvalid();
        verify(stats, never()).markImported();
    }

    @Test
    void taxonomyPenaltyIsAppliedBeforeCacheAdd() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, feedProduct);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        processor.process(parsed, 1000, stats);

        // then
        verify(taxonomyCache).add(eq(StoreFeedTaxonomy.deprioritized(categorizedTaxonomy, 1000)));
    }
}
