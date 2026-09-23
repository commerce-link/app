package pl.commercelink.taxonomy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.CategoryMatchedEvent;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryEnrichmentTest {

    private static final int PENDING_CAP = 2;

    @Mock
    private TaxonomyCache cache;

    @Mock
    private PendingCategorizationRepository pendingRepository;

    @Mock
    private CategoryMappingCache mappingCache;

    private TaxonomyCategoryEnrichment enrichment;

    @BeforeEach
    void setUp() {
        enrichment = new TaxonomyCategoryEnrichment(cache, pendingRepository,
                new TaxonomyCategoryMatchProperties(PENDING_CAP, 10), mappingCache);
    }

    @Test
    void enrichReturnsSameTaxonomyWhenAlreadyCategorized() {
        // given
        Taxonomy taxonomy = taxonomy("MFN-1", "CPU", 10);

        // when / then
        assertEquals(taxonomy, enrichment.enrich(taxonomy, taxonomy("MFN-1", "GPU", 1)));
    }

    @Test
    void enrichAdoptsCategoryFromTheCatalogKeepingOwnScoreAndWeights() {
        // given
        Taxonomy stored = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 3, null, null, null, "301");
        Taxonomy incoming = new Taxonomy("1234567890123", "MFN-1", "OtherBrand", "OtherName", null, 10, 555, null);

        // when
        Taxonomy result = enrichment.enrich(incoming, stored);

        // then
        assertEquals("CPU", result.category());
        assertEquals("301", result.categoryId());
        assertEquals(10, result.dataAccuracyScore());
        assertEquals("OtherBrand", result.brand());
        assertEquals(555, result.netWeightInGrams());
    }

    @Test
    void enrichLeavesTaxonomyPendingWhenTheCatalogHasNoCategorizedEntry() {
        // given
        Taxonomy incoming = taxonomy("MFN-1", null, 10);

        // when / then
        assertNull(enrichment.enrich(incoming, taxonomy("MFN-1", null, 3)).category());
        assertNull(enrichment.enrich(incoming, null).category());
    }

    @Test
    void pendingEligibleForEverySupplier() {
        // given
        when(pendingRepository.count()).thenReturn(0);

        // when / then
        assertTrue(enrichment.isPendingEligible(taxonomy("MFN-1", null, 10)));
    }

    @Test
    void pendingNotEligibleWhenIdentifiersIncomplete() {
        // given
        Taxonomy noBrand = new Taxonomy("1234567890123", "MFN-1", "", "Name", null, 10, null, null);

        // when / then
        assertFalse(enrichment.isPendingEligible(noBrand));
    }

    @Test
    void pendingNotEligibleAboveCap() {
        // given
        when(pendingRepository.count()).thenReturn(PENDING_CAP);

        // when / then
        assertFalse(enrichment.isPendingEligible(taxonomy("MFN-3", null, 10)));
    }

    @Test
    void addPendingCountsEachMfnOnce() {
        // given
        when(pendingRepository.count()).thenReturn(0);
        when(pendingRepository.add(eq("MFN-1"), eq("Acme"), any(LocalDateTime.class))).thenReturn(true, false);

        // when
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy("MFN-1", null, 5), "Acme");

        // then
        assertEquals(1, enrichment.pendingCount());
    }

    @Test
    void addPendingIgnoresTaxonomiesWithoutAProductCode() {
        // when
        enrichment.addPending(taxonomy("  ", null, 10), "Acme");

        // then
        verify(pendingRepository, never()).add(anyString(), anyString(), any());
    }

    @Test
    void applyMatchSetsTheCategoryAndClearsThePendingRow() {
        // given
        when(cache.updateCategory("MFN-1", "CPU", "301")).thenReturn(true);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("1234567890123", "MFN-1", "CPU", "301", 0.9, "mock"));

        // then
        verify(cache).updateCategory("MFN-1", "CPU", "301");
        verify(pendingRepository).remove("MFN-1");
    }

    @Test
    void applyMatchAcceptsArbitraryCategoryName() {
        // given
        when(cache.updateCategory("MFN-1", "Dowolna Kategoria", "999")).thenReturn(true);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "Dowolna Kategoria", "999", null, "mock"));

        // then
        verify(pendingRepository).remove("MFN-1");
    }

    @Test
    void applyMatchIgnoresBlankCategoryAndNullEvent() {
        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "", null, null, "mock"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", null, "CPU", "301", null, "mock"));
        enrichment.applyMatch(null);

        // then
        verify(cache, never()).updateCategory(anyString(), anyString(), anyString());
        verify(pendingRepository, never()).remove(anyString());
    }

    @Test
    void applyMatchLeavesThePendingRowWhenTheConditionalWriteIsRefused() {
        // given
        when(cache.updateCategory("MFN-1", "CPU", "301")).thenReturn(false);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "CPU", "301", null, "mock"));

        // then
        verify(pendingRepository, never()).remove(anyString());
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchLearnsMappingSampleFromConfidentAnswer() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true);
        when(cache.findByMfn("MFN-1")).thenReturn(pendingWithRawCategory("MFN-1", "Karty graficzne"));

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchLearnsFromPimIndexAnswerWithoutConfidence() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true);
        when(cache.findByMfn("MFN-1")).thenReturn(pendingWithRawCategory("MFN-1", "Karty graficzne"));

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", null, "pim-index"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchSkipsLearningBelowConfidenceThreshold() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.85, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
        verify(pendingRepository).remove("MFN-1");
    }

    @Test
    void applyMatchSkipsLearningWithoutSupplier() {
        // given
        pendingRowFor("MFN-1", null);
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchSkipsLearningWithoutRawCategory() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true);
        when(cache.findByMfn("MFN-1")).thenReturn(taxonomy("MFN-1", "GPU", 10));

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchSkipsLearningWithoutCategoryId() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", null)).thenReturn(true);

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", null, 0.95, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchLearnsOnlyOnceForDuplicateEvent() {
        // given
        pendingRowFor("MFN-1", "Acme");
        when(cache.updateCategory("MFN-1", "GPU", "301")).thenReturn(true, false);
        when(cache.findByMfn("MFN-1")).thenReturn(pendingWithRawCategory("MFN-1", "Karty graficzne"));
        CategoryMatchedEvent event = new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini");

        // when
        enrichment.applyMatch(event);
        enrichment.applyMatch(event);

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    private void pendingRowFor(String mfn, String supplier) {
        PendingCategorization pending = new PendingCategorization();
        pending.setMfn(mfn);
        pending.setSupplier(supplier);
        when(pendingRepository.find(mfn)).thenReturn(pending);
    }

    private static Taxonomy taxonomy(String mfn, String category, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy pendingWithRawCategory(String mfn, String rawCategory) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 10, null, null, rawCategory);
    }
}
