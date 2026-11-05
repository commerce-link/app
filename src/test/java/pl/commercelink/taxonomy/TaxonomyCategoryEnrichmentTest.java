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

    private static final String EAN_1 = "5901234567801";
    private static final String EAN_2 = "5901234567802";
    private static final String EAN_3 = "5901234567803";

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
        Taxonomy taxonomy = taxonomy(EAN_1, "MFN-1", "CPU", 10);

        // when / then
        assertEquals(taxonomy, enrichment.enrich(taxonomy));
    }

    @Test
    void enrichAdoptsCategoryFromCacheKeepingOwnScoreAndWeights() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 3, null, null, null, "301"));
        Taxonomy incoming = new Taxonomy(EAN_1, "MFN-1", "OtherBrand", "OtherName", null, 10, 555, null);

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
        cache.add(taxonomy(EAN_1, "MFN-1", null, 3));
        Taxonomy incoming = taxonomy(EAN_1, "MFN-1", null, 10);

        // when / then
        assertNull(enrichment.enrich(incoming).category());
    }

    @Test
    void rowWithAnUncategorisedEanTakesTheCategoryOfACompatibleBrand() {
        // given
        cache.add(new Taxonomy(EAN_1, "920-002509", "Logitech", "K120 PL", "Klawiatury", 3, null, null, null, "194"));
        Taxonomy incoming = new Taxonomy(EAN_2, "920-002509", "logitech", "K120 DE", null, 10, null, null);

        // when
        Taxonomy result = enrichment.enrich(incoming);

        // then
        assertEquals("Klawiatury", result.category());
        assertEquals("194", result.categoryId());
        assertEquals(EAN_2, result.ean());
    }

    @Test
    void categoryIsNotTakenFromAnotherBrandUnderTheSameCode() {
        // given
        cache.add(new Taxonomy(EAN_1, "3539", "Bestway", "Basen", "Baseny dla dzieci", 1, null, null, null, "77"));
        Taxonomy incoming = new Taxonomy(EAN_2, "3539", "SIKU", "Betoniarka 1:50", null, 10, null, null);

        // when / then
        assertNull(enrichment.enrich(incoming).category());
    }

    @Test
    void categoryIsNotTakenThroughACodeThatCameWithAnotherBrand() {
        // given
        cache.add(new Taxonomy(EAN_1, "920-002509", "Logitech", "K120", "Klawiatury", 1, null, null, null, "194"));
        cache.add(new Taxonomy(EAN_1, "920", "Natec", "K120", null, 9, null, null));
        Taxonomy incoming = new Taxonomy(EAN_2, "920", "Logitech", "Inny produkt", null, 10, null, null);

        // when / then
        assertNull(enrichment.enrich(incoming).category());
    }

    @Test
    void rowWithoutBrandTakesNoCategoryByCode() {
        // given
        cache.add(new Taxonomy(EAN_1, "3539", "Bestway", "Basen", "Baseny dla dzieci", 1, null, null, null, "77"));
        Taxonomy incoming = new Taxonomy(EAN_2, "3539", null, "Betoniarka 1:50", null, 10, null, null);

        // when / then
        assertNull(enrichment.enrich(incoming).category());
    }

    @Test
    void pendingEligibleForEverySupplier() {
        // given
        Taxonomy taxonomy = taxonomy(EAN_1, "MFN-1", null, 10);

        // when / then
        assertTrue(enrichment.isPendingEligible(taxonomy));
    }

    @Test
    void pendingNotEligibleWhenIdentifiersIncomplete() {
        // given
        Taxonomy noBrand = new Taxonomy(EAN_1, "MFN-1", "", "Name", null, 10, null, null);

        // when / then
        assertFalse(enrichment.isPendingEligible(noBrand));
    }

    @Test
    void pendingNotEligibleAboveCap() {
        // given (cap = 2)
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy(EAN_2, "MFN-2", null, 10), "Acme");

        // when / then
        assertFalse(enrichment.isPendingEligible(taxonomy(EAN_3, "MFN-3", null, 10)));
    }

    @Test
    void addPendingCountsEachEanOnce() {
        // when
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 5), "Acme");

        // then
        assertEquals(1, enrichment.pendingCount());
        assertEquals(1, cache.size());
    }

    @Test
    void addPendingRecordsSupplierForMfn() {
        // when
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy(EAN_2, "MFN-2", null, 10), null);

        // then
        assertEquals("Acme", enrichment.supplierOf("MFN-1"));
        assertNull(enrichment.supplierOf("MFN-2"));
    }

    @Test
    void applyMatchUpdatesCacheAndDecrementsCounter() {
        // given
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");
        enrichment.addPending(taxonomy(EAN_2, "MFN-2", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-1", "CPU", "301", 0.9, "mock"));
        // a free-text category name with a space, no confidence: applyMatch must not restrict it to a known list
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_2, "MFN-2", "Dowolna Kategoria", "999", null, "mock"));

        // then
        assertEquals("CPU", cache.findByEan(EAN_1).category());
        assertEquals("301", cache.findByEan(EAN_1).categoryId());
        assertEquals("Dowolna Kategoria", cache.findByEan(EAN_2).category());
        assertEquals("999", cache.findByEan(EAN_2).categoryId());
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void matchGoesToTheRecordOfItsEan() {
        // given
        enrichment.addPending(new Taxonomy(EAN_1, "3539", "SIKU", "Betoniarka 1:50", null, 10, null, null), "Acme");
        enrichment.addPending(new Taxonomy(EAN_2, "3539", "Bestway", "Basen", null, 10, null, null), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "3539", "Modele pojazdów", "301", 0.95, "gemini"));

        // then
        assertEquals("Modele pojazdów", cache.findByEan(EAN_1).category());
        assertNull(cache.findByEan(EAN_2).category());
    }

    @Test
    void matchForAnUnknownEanChangesNothing() {
        // given
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_2, "MFN-1", "CPU", "301", 0.95, "gemini"));

        // then
        assertNull(cache.findByEan(EAN_1).category());
        assertNull(cache.findByEan(EAN_2));
        assertEquals(1, enrichment.pendingCount());
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void pendingCountDropsWhenAnotherSupplierDeliversCategorizedEntry() {
        // given
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");
        assertEquals(1, enrichment.pendingCount());

        // when
        cache.add(taxonomy(EAN_1, "MFN-1", "CPU", 5));

        // then
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void applyMatchIgnoresBlankCategoryMissingEntryAndNull() {
        // given
        enrichment.addPending(taxonomy(EAN_1, "MFN-1", null, 10), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-1", "", null, null, "mock"));
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_3, "MFN-GONE", "CPU", "301", null, "mock"));
        enrichment.applyMatch(null);

        // then
        assertNull(cache.findByEan(EAN_1).category());
        assertEquals(1, enrichment.pendingCount());
    }

    @Test
    void applyMatchLearnsMappingSampleFromConfidentAnswer() {
        // given
        enrichment.addPending(pendingWithRawCategory(EAN_1, "MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-1", "GPU", "301", 0.95, "gemini"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchLearnsFromPimIndexAnswerWithoutConfidence() {
        // given
        enrichment.addPending(pendingWithRawCategory(EAN_1, "MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-1", "GPU", "301", null, "pim-index"));

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void applyMatchSkipsLearningBelowConfidenceThreshold() {
        // given
        enrichment.addPending(pendingWithRawCategory(EAN_1, "MFN-1", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-1", "GPU", "301", 0.85, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
        assertEquals("GPU", cache.findByEan(EAN_1).category());
    }

    @Test
    void applyMatchSkipsLearningWithoutSupplierRawCategoryOrCategoryId() {
        // given
        enrichment.addPending(pendingWithRawCategory(EAN_1, "MFN-NO-SUPPLIER", "Karty graficzne"), null);
        enrichment.addPending(taxonomy(EAN_2, "MFN-NO-RAW", null, 10), "Acme");
        enrichment.addPending(pendingWithRawCategory(EAN_3, "MFN-NO-CAT-ID", "Karty graficzne"), "Acme");

        // when
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_1, "MFN-NO-SUPPLIER", "GPU", "301", 0.95, "gemini"));
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_2, "MFN-NO-RAW", "GPU", "301", 0.95, "gemini"));
        enrichment.applyMatch(new CategoryMatchedEvent(EAN_3, "MFN-NO-CAT-ID", "GPU", null, 0.95, "gemini"));

        // then
        verify(mappingCache, never()).recordSample(any(), any(), any(), any());
    }

    @Test
    void applyMatchLearnsOnlyOnceForDuplicateEvent() {
        // given
        enrichment.addPending(pendingWithRawCategory(EAN_1, "MFN-1", "Karty graficzne"), "Acme");
        CategoryMatchedEvent event = new CategoryMatchedEvent(EAN_1, "MFN-1", "GPU", "301", 0.95, "gemini");

        // when
        enrichment.applyMatch(event);
        enrichment.applyMatch(event);

        // then
        verify(mappingCache).recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    private static Taxonomy taxonomy(String ean, String mfn, String category, int score) {
        return new Taxonomy(ean, mfn, "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy pendingWithRawCategory(String ean, String mfn, String rawCategory) {
        return new Taxonomy(ean, mfn, "Brand", "Name", null, 10, null, null, rawCategory);
    }
}
