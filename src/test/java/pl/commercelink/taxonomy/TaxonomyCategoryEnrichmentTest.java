package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.CategoryMatchedEvent;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryEnrichmentTest {

    private TaxonomyCache cache;
    private TaxonomyCategoryEnrichment enrichment;

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Mock
    private CategoryMappingCache mappingCache;

    @BeforeEach
    void setUp() {
        Mockito.when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
        enrichment = new TaxonomyCategoryEnrichment(cache,
                new TaxonomyCategoryMatchProperties(100, 2), mappingCache, new CategoryMatchAttempts());
    }

    @Test
    void enrichReturnsSameTaxonomyWhenAlreadyCategorized() {
        // given
        Taxonomy taxonomy = taxonomy("MFN-1", "CPU", 10);

        // when / then
        assertEquals(taxonomy, enrichment.enrich(taxonomy));
    }

    @Test
    void enrichAdoptsCategoryFromCacheKeepingOwnScoreAndWeights() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 3, null, null, null, "301"));
        Taxonomy incoming = new Taxonomy("1234567890123", "MFN-1", "OtherBrand", "OtherName", null, 10, 555, null);

        // when
        Taxonomy result = enrichment.enrich(incoming);

        // then
        assertEquals("CPU", result.category());
        assertEquals("301", result.categoryId());
        assertEquals(10, result.dataAccuracyScore());
        assertEquals("OtherBrand", result.brand());
        assertEquals(555, result.netWeightInGrams());
    }

    @Test
    void enrichLeavesTaxonomyPendingWhenCacheHasNoCategorizedEntry() {
        // given
        cache.add(taxonomy("MFN-1", null, 3));
        Taxonomy incoming = taxonomy("MFN-1", null, 10);

        // when / then
        assertNull(enrichment.enrich(incoming).category());
    }

    @Test
    void pendingEligibleForEverySupplier() {
        // given
        Taxonomy taxonomy = taxonomy("MFN-1", null, 10);

        // when / then
        assertTrue(enrichment.isPendingEligible(taxonomy));
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
        // given (cap = 2)
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy("MFN-2", null, 10), "Acme");

        // when / then
        assertFalse(enrichment.isPendingEligible(taxonomy("MFN-3", null, 10)));
    }

    @Test
    void addPendingCountsEachMfnOnce() {
        // when
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy("MFN-1", null, 5), "Acme");

        // then
        assertEquals(1, enrichment.pendingCount());
        assertEquals(1, cache.size());
    }

    @Test
    void addPendingRecordsSupplierForMfn() {
        // when
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy("MFN-2", null, 10), null);

        // then
        assertEquals("Acme", enrichment.supplierOf("MFN-1"));
        assertNull(enrichment.supplierOf("MFN-2"));
    }

    @Test
    void applyMatchUpdatesCacheAndDecrementsCounter() {
        // given
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("1234567890123", "MFN-1", "CPU", "301", 0.9, "mock"));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
        assertEquals("301", cache.findByMfn("MFN-1").categoryId());
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void applyMatchAcceptsArbitraryCategoryName() {
        // given
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "Dowolna Kategoria", "999", null, "mock"));

        // then
        assertEquals("Dowolna Kategoria", cache.findByMfn("MFN-1").category());
        assertEquals("999", cache.findByMfn("MFN-1").categoryId());
    }

    @Test
    void pendingCountDropsWhenAnotherSupplierDeliversCategorizedEntry() {
        // given
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");
        assertEquals(1, enrichment.pendingCount());

        // when
        cache.add(taxonomy("MFN-1", "CPU", 5));

        // then
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void applyMatchIgnoresBlankCategoryMissingEntryAndNull() {
        // given
        enrichment.addPending(taxonomy("MFN-1", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "", null, null, "mock"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-GONE", "CPU", "301", null, "mock"));
        enrichment.applyMatch(null);

        // then
        assertNull(cache.findByMfn("MFN-1").category());
        assertEquals(1, enrichment.pendingCount());
    }

    @Test
    void applyMatchLearnsMappingSampleFromConfidentAnswer() {
        // given
        enrichment.addPending(pendingWithRawCategory("MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchLearnsFromPimIndexAnswerWithoutConfidence() {
        // given
        enrichment.addPending(pendingWithRawCategory("MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", null, "pim-index"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchSkipsLearningBelowConfidenceThreshold() {
        // given
        enrichment.addPending(pendingWithRawCategory("MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.85, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
        assertEquals("GPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void applyMatchSkipsLearningWithoutSupplierRawCategoryOrCategoryId() {
        // given
        enrichment.addPending(pendingWithRawCategory("MFN-NO-SUPPLIER", "Karty graficzne"), null);
        enrichment.addPending(taxonomy("MFN-NO-RAW", null, 10), "Acme");
        enrichment.addPending(pendingWithRawCategory("MFN-NO-CAT-ID", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-NO-SUPPLIER", "GPU", "301", 0.95, "gemini"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-NO-RAW", "GPU", "301", 0.95, "gemini"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-NO-CAT-ID", "GPU", null, 0.95, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchLearnsOnlyOnceForDuplicateEvent() {
        // given
        enrichment.addPending(pendingWithRawCategory("MFN-1", "Karty graficzne"), "Acme");
        CategoryMatchedEvent event = new CategoryMatchedEvent("e", "MFN-1", "GPU", "301", 0.95, "gemini");

        // when
        enrichment.applyMatch(event);
        enrichment.applyMatch(event);

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    private static Taxonomy taxonomy(String mfn, String category, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy pendingWithRawCategory(String mfn, String rawCategory) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 10, null, null, rawCategory);
    }
}
