package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.FeedItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.products.brand.NoNameBrand;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedProcessingTest {

    @Mock
    private DataCorrection dataCorrection;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private TaxonomyCategoryEnrichment enrichment;
    @Mock
    private FeedParseStats stats;

    private static final SupplierInfo ACME = new SupplierInfo("Acme", SupplierType.Distributor, 5, "PL",
            new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));

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
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrichFromOwnRecord(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

        // then
        assertEquals(List.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markImported();
        verify(stats, never()).markImportedCategorized();
        verify(stats, never()).markInvalid();
        verify(stats).log();
    }

    @Test
    void feedRowWithNoNameBrandIsProcessableAndEntersTheInventory() {
        // given
        PimCatalog pimWithoutTheProduct = ScopedPimCatalog.of();
        DataCorrection realCorrection = new DataCorrection(pimWithoutTheProduct, new BrandMapper(pimWithoutTheProduct));
        when(enrichment.enrichFromOwnRecord(any()))
                .thenAnswer(invocation -> invocation.<Taxonomy>getArgument(0).withCategory("Kable", "120"));
        FeedProcessing processing = new FeedProcessing(realCorrection, taxonomyCache, enrichment, stats, 0);

        // when
        processing.add(ParsedRow.of(feedItemWithBrand("No Name"), ACME));
        List<InventoryItem> result = processing.finish();

        // then
        assertEquals(1, result.size());
        assertEquals(NoNameBrand.NAME, result.get(0).brand());
        ArgumentCaptor<Taxonomy> added = ArgumentCaptor.forClass(Taxonomy.class);
        verify(taxonomyCache).add(added.capture());
        assertEquals(NoNameBrand.NAME, added.getValue().brand());
        assertTrue(added.getValue().isProcessable());
        verify(stats).markImported();
        verify(stats, never()).markIncomplete();
    }

    @Test
    void returnedOfferCarriesTheOfferBrand() {
        // given
        InventoryItem branded = new InventoryItem("Logitech", null, "1234567890123", "MFN-1", "Acme",
                10.0, "PLN", 5, 1, true, false, false);
        when(dataCorrection.run(sellableItem)).thenReturn(branded);
        when(dataCorrection.run(feedProduct, branded)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrichFromOwnRecord(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

        // then
        assertEquals("Logitech", result.get(0).brand());
    }

    @Test
    void pendingEligibleRowGoesOnlyToPendingNotToInventory() {
        // given
        givenAPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("Acme");
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

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
    void pendingEligibleRowScopesTheMappingToTheSupplierTypeForAnOwnConnection() {
        // given -- category mappings are learned per adapter type, not per connection instance
        givenAPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("Kosatec-k7f3a9c2");
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        processing.finish();

        // then
        verify(enrichment).addPending(pendingTaxonomy, "Kosatec");
    }

    @Test
    void pendingEligibleRowScopesTheMappingToTheFullIdentityForAManualConnection() {
        // given -- manual feeds are distinct per connection, so each keeps its own mappings
        givenAPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(true);
        when(stats.supplierName()).thenReturn("manual-k7f3a9c2");
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        processing.finish();

        // then
        verify(enrichment).addPending(pendingTaxonomy, "manual-k7f3a9c2");
    }

    @Test
    void pendingIneligibleRowWithCompleteDataIsDeferredToNextFeed() {
        // given
        givenAPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(true);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

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
        givenAPendingRow();
        when(enrichment.isPendingEligible(pendingTaxonomy)).thenReturn(false);
        when(enrichment.hasIdentificationData(pendingTaxonomy)).thenReturn(false);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

        // then
        assertTrue(result.isEmpty());
        verify(enrichment, never()).addPending(any(), any());
        verify(taxonomyCache, never()).add(any());
        verify(stats).markIncomplete();
        verify(stats, never()).markCategorizationPostponed();
        verify(stats, never()).markCategorizationScheduled();
    }

    @Test
    void categoryOfTheOwnRecordPutsItemIntoInventoryWithoutTheCodeIndex() {
        // given
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(pendingTaxonomy);
        when(enrichment.enrichFromOwnRecord(pendingTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

        // then
        assertEquals(List.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markImportedCategorized();
        verify(stats).markImported();
        verify(enrichment, never()).enrich(any());
    }

    @Test
    void rowWaitingForTheCodeIndexEntersTheInventoryOnFinish() {
        // given
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(pendingTaxonomy);
        when(enrichment.enrichFromOwnRecord(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        boolean settledBeforeFinish = !mockingDetails(taxonomyCache).getInvocations().isEmpty();
        List<InventoryItem> result = processing.finish();

        // then
        assertFalse(settledBeforeFinish);
        assertEquals(List.of(sellableItem), result);
        verify(taxonomyCache).add(categorizedTaxonomy);
        verify(stats).markImportedCategorized();
        verify(stats).markImported();
    }

    @Test
    void rowWithASharedCodeTakesNoCategoryFromAnotherEan() {
        // given
        givenRowsAreTakenAsTheyCome();
        when(enrichment.isPendingEligible(any())).thenReturn(true);
        when(stats.supplierName()).thenReturn("Acme");
        FeedProcessing processing = processing(0);

        // when
        processing.add(row("5901234567801", "BRAK", "Lalka"));
        processing.add(row("5901234567802", "BRAK", "Auto"));
        List<InventoryItem> result = processing.finish();

        // then
        assertTrue(result.isEmpty());
        verify(enrichment, never()).enrich(any());
        verify(enrichment, times(2)).addPending(any(), eq("Acme"));
    }

    @Test
    void sharedCodeBlocksItsEansAsCategorySourcesOnFinish() {
        // given
        givenRowsAreTakenAsTheyCome();
        when(enrichment.enrich(any())).then(returnsFirstArg());
        when(enrichment.isPendingEligible(any())).thenReturn(true);
        when(stats.supplierName()).thenReturn("Acme");
        FeedProcessing processing = processing(0);
        processing.add(row("5901234567801", "BRAK", "Lalka"));
        processing.add(row("5901234567802", "BRAK", "Auto"));
        processing.add(row("5901234567803", "G502", "Mysz"));

        // when
        processing.finish();

        // then
        verify(taxonomyCache).blockCodeSource("BRAK", "Brand", "5901234567801");
        verify(taxonomyCache).blockCodeSource("BRAK", "Brand", "5901234567802");
        verify(taxonomyCache, never()).blockCodeSource(eq("G502"), any(), any());
        verify(enrichment).enrich(any());
    }

    @Test
    void adoptedCategoryOnIncompleteRowIsNotCountedAsImportedCategorized() {
        // given
        Taxonomy noBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", null, 5, null, null);
        Taxonomy enrichedNoBrand = new Taxonomy("1234567890123", "MFN-1", null, "Name", "CPU", 5, null, null);
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(noBrand);
        when(enrichment.enrichFromOwnRecord(noBrand)).thenReturn(enrichedNoBrand);
        when(enrichment.isPendingEligible(enrichedNoBrand)).thenReturn(false);
        when(enrichment.hasIdentificationData(enrichedNoBrand)).thenReturn(false);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        List<InventoryItem> result = processing.finish();

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
        when(dataCorrection.run(noQty)).thenReturn(noQty);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(noQty, feedProduct));
        List<InventoryItem> result = processing.finish();

        // then
        assertTrue(result.isEmpty());
        verify(taxonomyCache, never()).add(any());
        verify(enrichment, never()).addPending(any(), any());
        verify(stats).markInvalid();
        verify(stats, never()).markImported();
    }

    @Test
    void taxonomyIsNotCorrectedForAnOfferThatCannotBeSold() {
        // given
        InventoryItem noQty = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 0, 1, "Acme", true);
        when(dataCorrection.run(noQty)).thenReturn(noQty);
        FeedProcessing processing = processing(0);

        // when
        processing.add(new ParsedRow(noQty, feedProduct));
        processing.finish();

        // then
        verify(dataCorrection, never()).run(any(SupplierProduct.class), any(InventoryItem.class));
        verify(enrichment, never()).enrichFromOwnRecord(any());
        verify(enrichment, never()).enrich(any());
    }

    @Test
    void invalidRowIsCounted() {
        // given
        FeedProcessing processing = processing(0);

        // when
        processing.skipInvalid();

        // then
        verify(stats).markInvalid();
    }

    @Test
    void taxonomyPenaltyIsAppliedBeforeCacheAdd() {
        // given
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(categorizedTaxonomy);
        when(enrichment.enrichFromOwnRecord(categorizedTaxonomy)).thenReturn(categorizedTaxonomy);
        FeedProcessing processing = processing(1000);

        // when
        processing.add(new ParsedRow(sellableItem, feedProduct));
        processing.finish();

        // then
        verify(taxonomyCache).add(eq(StoreFeedTaxonomy.deprioritized(categorizedTaxonomy, 1000)));
    }

    private FeedProcessing processing(int taxonomyPenalty) {
        return new FeedProcessing(dataCorrection, taxonomyCache, enrichment, stats, taxonomyPenalty);
    }

    private void givenAPendingRow() {
        when(dataCorrection.run(sellableItem)).thenReturn(sellableItem);
        when(dataCorrection.run(feedProduct, sellableItem)).thenReturn(pendingTaxonomy);
        when(enrichment.enrichFromOwnRecord(pendingTaxonomy)).thenReturn(pendingTaxonomy);
        when(enrichment.enrich(pendingTaxonomy)).thenReturn(pendingTaxonomy);
    }

    private void givenRowsAreTakenAsTheyCome() {
        when(dataCorrection.run(any(InventoryItem.class))).then(returnsFirstArg());
        when(dataCorrection.run(any(SupplierProduct.class), any(InventoryItem.class))).thenAnswer(invocation -> {
            SupplierProduct product = invocation.getArgument(0);
            InventoryItem offer = invocation.getArgument(1);
            return new Taxonomy(offer.ean(), offer.mfn(), offer.brand(), product.name(), null, 5, null, null);
        });
        when(enrichment.enrichFromOwnRecord(any())).then(returnsFirstArg());
    }

    private static FeedItem feedItemWithBrand(String brand) {
        return new FeedItem() {
            public String getEan() { return "5901234567890"; }
            public String getMfn() { return "KAB-HDMI-2M"; }
            public String getBrand() { return brand; }
            public String getName() { return "Kabel HDMI 2 m"; }
            public String getCategory() { return "Kable"; }
            public double getNetPrice() { return 12.50; }
            public String getCurrency() { return "PLN"; }
            public int getQty() { return 3; }
        };
    }

    private static ParsedRow row(String ean, String mfn, String name) {
        InventoryItem offer = new InventoryItem("Brand", null, ean, mfn, "Acme", 10.0, "PLN", 5, 1, true, false, false);
        return new ParsedRow(offer, new SupplierProduct(ean, mfn, "Brand", name, 5, null, null));
    }
}
