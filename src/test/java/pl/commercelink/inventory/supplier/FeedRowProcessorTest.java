package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
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
import pl.commercelink.taxonomy.TaxonomyMerge;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    private TaxonomyMerge merge;
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

    private FeedParseStats stats;

    @BeforeEach
    void setUp() {
        stats = mock(FeedParseStats.class);
    }

    @Test
    void processableRowGoesToTheCatalogAndInventory() {
        // given
        givenMerge();
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy, null)).thenReturn(categorizedTaxonomy);

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).containsExactly(sellableItem);
        verify(merge).add(categorizedTaxonomy);
        verify(taxonomyCache).commit(merge);
        verify(stats).markImported();
        verify(stats, never()).markImportedCategorized();
        verify(stats, never()).markInvalid();
    }

    @Test
    void pendingEligibleRowGoesToTheCatalogAndToThePendingTableButNotToInventory() {
        // given
        givenMerge();
        givenPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("Acme");

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(enrichment).addPending(pendingTaxonomy, "Acme");
        verify(merge).add(pendingTaxonomy);
        verify(stats).markCategorizationScheduled();
        verify(stats, never()).markCategorizationPostponed();
        verify(stats, never()).markIncomplete();
        verify(stats, never()).markImportedCategorized();
    }

    @Test
    void pendingEligibleRowScopesTheMappingToTheSupplierTypeForAnOwnConnection() {
        // given
        givenMerge();
        givenPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("Kosatec-k7f3a9c2");

        // when
        processor.process(List.of(row()), 0, stats);

        // then
        verify(enrichment).addPending(pendingTaxonomy, "Kosatec");
    }

    @Test
    void pendingEligibleRowScopesTheMappingToTheFullIdentityForAManualConnection() {
        // given
        givenMerge();
        givenPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("manual-k7f3a9c2");

        // when
        processor.process(List.of(row()), 0, stats);

        // then
        verify(enrichment).addPending(pendingTaxonomy, "manual-k7f3a9c2");
    }

    @Test
    void pendingIneligibleRowWithCompleteDataIsDeferredToNextFeed() {
        // given
        givenMerge();
        givenPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(true);

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(enrichment, never()).addPending(any(), any());
        verify(merge, never()).add(any());
        verify(stats).markCategorizationPostponed();
        verify(stats, never()).markIncomplete();
        verify(stats, never()).markCategorizationScheduled();
    }

    @Test
    void pendingIneligibleRowWithMissingDataIsDropped() {
        // given
        givenMerge();
        givenPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(false);

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(enrichment, never()).addPending(any(), any());
        verify(merge, never()).add(any());
        verify(stats).markIncomplete();
        verify(stats, never()).markCategorizationPostponed();
        verify(stats, never()).markCategorizationScheduled();
    }

    @Test
    void categoryAdoptedFromTheCatalogPutsItemIntoInventory() {
        // given
        givenMerge();
        Taxonomy stored = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 3, null, null);
        when(merge.current("MFN-1")).thenReturn(stored);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy, stored)).thenReturn(categorizedTaxonomy);

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).containsExactly(sellableItem);
        verify(merge).add(categorizedTaxonomy);
        verify(stats).markImportedCategorized();
        verify(stats).markImported();
    }

    @Test
    void adoptedCategoryOnIncompleteRowIsNotCountedAsImportedCategorized() {
        // given
        givenMerge();
        Taxonomy noBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", null, 5, null, null);
        Taxonomy enrichedNoBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", "CPU", 5, null, null);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(noBrand);
        when(enrichment.enrich(noBrand, null)).thenReturn(enrichedNoBrand);
        when(enrichment.isPendingEligible(enrichedNoBrand)).thenReturn(false);
        when(enrichment.hasIdentificationData(enrichedNoBrand)).thenReturn(false);

        // when
        List<InventoryItem> result = processor.process(List.of(row()), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(stats).markIncomplete();
        verify(stats, never()).markImportedCategorized();
        verify(stats, never()).markImported();
    }

    @Test
    void notSellableItemIsDroppedWithoutTouchingTheCatalog() {
        // given
        InventoryItem noQty = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 0, 1, "Acme", true);
        when(dataCorrection.run(noQty)).thenReturn(noQty);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);

        // when
        List<InventoryItem> result = processor.process(List.of(new ParsedRow(noQty, feedProduct)), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(taxonomyCache, never()).startMerge(any());
        verify(taxonomyCache, never()).commit(any());
        verify(enrichment, never()).addPending(any(), any());
        verify(stats).markInvalid();
        verify(stats, never()).markImported();
    }

    @Test
    void taxonomyPenaltyIsAppliedBeforeTheRecordIsStaged() {
        // given
        givenMerge();
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy, null)).thenReturn(categorizedTaxonomy);

        // when
        processor.process(List.of(row()), 1000, stats);

        // then
        verify(merge).add(eq(StoreFeedTaxonomy.deprioritized(categorizedTaxonomy, 1000)));
    }

    @Test
    void wholeChunkIsReadAndWrittenInOneRoundTrip() {
        // given
        givenMerge();
        InventoryItem secondItem =
                new InventoryItem("1234567890124", "MFN-2", 12.0, "PLN", 5, 1, "Acme", true);
        SupplierProduct secondProduct =
                new SupplierProduct("1234567890124", "MFN-2", "Brand", "Name2", 5, null, null);
        Taxonomy secondTaxonomy =
                new Taxonomy("1234567890124", "MFN-2", "Brand", "Name2", "GPU", 5, null, null);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(categorizedTaxonomy);
        when(dataCorrection.run(secondItem)).thenReturn(secondItem);
        when(dataCorrection.run(secondProduct)).thenReturn(secondTaxonomy);
        when(enrichment.enrich(categorizedTaxonomy, null)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrich(secondTaxonomy, null)).thenReturn(secondTaxonomy);

        // when
        List<InventoryItem> result = processor.process(
                List.of(row(), new ParsedRow(secondItem, secondProduct)), 0, stats);

        // then
        assertThat(result).containsExactly(sellableItem, secondItem);
        verify(taxonomyCache, times(1)).startMerge(List.of("MFN-1", "MFN-2"));
        verify(taxonomyCache, times(1)).commit(merge);
    }

    @Test
    void emptyChunkTouchesNothing() {
        // when
        List<InventoryItem> result = processor.process(List.of(), 0, stats);

        // then
        assertThat(result).isEmpty();
        verify(taxonomyCache, never()).startMerge(any());
    }

    private ParsedRow row() {
        return new ParsedRow(sellableItem, feedProduct);
    }

    private void givenMerge() {
        when(taxonomyCache.startMerge(any())).thenReturn(merge);
    }

    private void givenPendingRow() {
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy, null)).thenReturn(pendingTaxonomy);
    }
}
