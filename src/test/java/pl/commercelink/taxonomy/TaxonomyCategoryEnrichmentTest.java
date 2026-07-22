package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.CategoryMatchedEvent;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryEnrichmentTest {

    private TaxonomyCache cache;
    private TaxonomyCategoryEnrichment enrichment;

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @BeforeEach
    void setUp() {
        Mockito.when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
        enrichment = new TaxonomyCategoryEnrichment(cache,
                new TaxonomyCategoryMatchProperties("Acme, Elko", 100, 2));
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
        cache.add(taxonomy("MFN-1", "CPU", 3));
        Taxonomy incoming = new Taxonomy("1234567890123", "MFN-1", "OtherBrand", "OtherName", "Other", 10, 555, null);

        // when
        Taxonomy result = enrichment.enrich(incoming);

        // then
        assertEquals("CPU", result.category());
        assertEquals(10, result.dataAccuracyScore());
        assertEquals("OtherBrand", result.brand());
        assertEquals(555, result.netWeightInGrams());
    }

    @Test
    void enrichLeavesTaxonomyPendingWhenCacheHasNoCategorizedEntry() {
        // given
        cache.add(taxonomy("MFN-1", "Other", 3));
        Taxonomy incoming = taxonomy("MFN-1", "Other", 10);

        // when / then
        assertEquals("Other", enrichment.enrich(incoming).category());
    }

    @Test
    void pendingEligibleOnlyForAllowlistedSupplier() {
        // given
        Taxonomy taxonomy = taxonomy("MFN-1", "Other", 10);

        // when / then
        assertTrue(enrichment.isPendingEligible("Acme", taxonomy));
        assertFalse(enrichment.isPendingEligible("Morele", taxonomy));
    }

    @Test
    void pendingNotEligibleWhenIdentifiersIncomplete() {
        // given
        Taxonomy noBrand = new Taxonomy("1234567890123", "MFN-1", "", "Name", "Other", 10, null, null);

        // when / then
        assertFalse(enrichment.isPendingEligible("Acme", noBrand));
    }

    @Test
    void pendingNotEligibleAboveCap() {
        // given (cap = 2)
        enrichment.addPending(taxonomy("MFN-1", "Other", 10));
        enrichment.addPending(taxonomy("MFN-2", "Other", 10));

        // when / then
        assertFalse(enrichment.isPendingEligible("Acme", taxonomy("MFN-3", "Other", 10)));
    }

    @Test
    void addPendingCountsEachMfnOnce() {
        // when
        enrichment.addPending(taxonomy("MFN-1", "Other", 10));
        enrichment.addPending(taxonomy("MFN-1", "Other", 5));

        // then
        assertEquals(1, enrichment.pendingCount());
        assertEquals(1, cache.size());
    }

    @Test
    void applyMatchUpdatesCacheAndDecrementsCounter() {
        // given
        enrichment.addPending(taxonomy("MFN-1", "Other", 10));

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("1234567890123", "MFN-1", "CPU", "301", "Procesory", 0.9, "mock"));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void pendingCountDropsWhenAnotherSupplierDeliversCategorizedEntry() {
        // given
        enrichment.addPending(taxonomy("MFN-1", "Other", 10));
        assertEquals(1, enrichment.pendingCount());

        // when
        cache.add(taxonomy("MFN-1", "CPU", 5));

        // then
        assertEquals(0, enrichment.pendingCount());
    }

    @Test
    void applyMatchIgnoresOtherUnknownAndMissingEntries() {
        // given
        enrichment.addPending(taxonomy("MFN-1", "Other", 10));

        // when
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "Other", null, null, null, "mock"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-1", "NotARealCategory", null, null, null, "mock"));
        enrichment.applyMatch(new CategoryMatchedEvent("e", "MFN-GONE", "CPU", null, null, null, "mock"));
        enrichment.applyMatch(null);

        // then
        assertEquals("Other", cache.findByMfn("MFN-1").category());
        assertEquals(1, enrichment.pendingCount());
    }

    private static Taxonomy taxonomy(String mfn, String category, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", category, score, null, null);
    }
}
