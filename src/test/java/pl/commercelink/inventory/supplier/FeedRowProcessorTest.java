package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.Taxonomy;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
    private final Taxonomy pendingTaxonomy =
            new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Other", 5, null, null);
    private final Taxonomy categorizedTaxonomy =
            new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 5, null, null);

    @Test
    void processableRowGoesToCacheAndInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, categorizedTaxonomy);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, "Acme", 0, stats);

        // then
        assertEquals(Optional.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verifyNoInteractions(stats);
    }

    @Test
    void pendingEligibleRowGoesOnlyToPendingNotToInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, pendingTaxonomy);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.isPendingEligible("Acme", pendingTaxonomy)).thenReturn(true);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, "Acme", 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(enrichment).addPending(pendingTaxonomy);
        verify(taxonomyCache, never()).add(any());
        verify(stats).markPendingAdded();
        verify(stats, never()).markDropped();
        verify(stats, never()).markAdopted();
    }

    @Test
    void pendingIneligibleRowIsDroppedLikeToday() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, pendingTaxonomy);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.isPendingEligible("Acme", pendingTaxonomy)).thenReturn(false);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, "Acme", 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(enrichment, never()).addPending(any());
        verify(taxonomyCache, never()).add(any());
        verify(stats).markDropped();
        verify(stats, never()).markPendingAdded();
    }

    @Test
    void adoptedCategoryFromCachePutsItemIntoInventory() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, pendingTaxonomy);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, "Acme", 0, stats);

        // then
        assertEquals(Optional.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markAdopted();
    }

    @Test
    void notSellableItemIsDroppedWithoutTouchingCache() {
        // given
        InventoryItem noQty = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 0, 1, "Acme", true);
        ParsedRow parsed = new ParsedRow(noQty, categorizedTaxonomy);
        when(dataCorrection.run(noQty)).thenReturn(noQty);
        when(dataCorrection.run(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        Optional<InventoryItem> result = processor.process(parsed, "Acme", 0, stats);

        // then
        assertTrue(result.isEmpty());
        verify(taxonomyCache, never()).add(any());
        verify(enrichment, never()).addPending(any());
        verifyNoInteractions(stats);
    }

    @Test
    void taxonomyPenaltyIsAppliedBeforeCacheAdd() {
        // given
        ParsedRow parsed = new ParsedRow(sellableItem, categorizedTaxonomy);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedParseStats stats = mock(FeedParseStats.class);

        // when
        processor.process(parsed, "Acme", 1000, stats);

        // then
        verify(taxonomyCache).add(eq(StoreFeedTaxonomy.deprioritized(categorizedTaxonomy, 1000)));
    }
}
