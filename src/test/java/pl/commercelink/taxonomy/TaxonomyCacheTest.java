package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyCacheTest {

    private TaxonomyCache cache;

    @BeforeEach
    void setUp() {
        TaxonomyRepository repo = Mockito.mock(TaxonomyRepository.class);
        Mockito.when(repo.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(repo);
        cache.onStartUp();
    }

    @Test
    void singleAddStoresWeight() {
        cache.add(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").netWeightInGrams());
    }

    @Test
    void betterScoreRecordWithoutWeightKeepsPreviousWeight() {
        cache.add(taxonomy("MFN-1", 10, 1300));
        cache.add(taxonomyNamed("MFN-1", 1, null, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void betterScoreRecordWithWeightReplacesEverything() {
        cache.add(taxonomy("MFN-1", 10, 1300));
        cache.add(taxonomyNamed("MFN-1", 1, 1500, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1500, result.netWeightInGrams());
    }

    @Test
    void worseScoreRecordWithWeightFillsMissingWeightOnly() {
        cache.add(taxonomyNamed("MFN-1", 1, null, "BestName"));
        cache.add(taxonomyNamed("MFN-1", 10, 1300, "WorseName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BestName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void twoSourcesWithWeightLowerScoreWins() {
        cache.add(taxonomy("MFN-1", 10, 1500));
        cache.add(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").netWeightInGrams());
    }

    @Test
    void blankMfnIsNoop() {
        cache.add(taxonomy("", 5, 1300));
        cache.add(taxonomy("   ", 5, 1300));

        assertEquals(0, cache.size());
    }

    @Test
    void concurrentAddsDoNotDropWeight() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Taxonomy bestSource = taxonomyNamed("MFN-1", 5, 1000, "Best");
        List<Taxonomy> inputs = IntStream.range(0, 200)
                .mapToObj(i -> taxonomyNamed("MFN-1", 10 + (i % 3), 2000 + (i % 5), "N" + i))
                .toList();

        pool.submit(() -> cache.add(bestSource));
        for (Taxonomy t : inputs) pool.submit(() -> cache.add(t));
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals(1000, result.netWeightInGrams());
        assertEquals("Best", result.name());
    }

    @Test
    void mergesNetFromOneAndGrossFromOtherIndependently() {
        Taxonomy a = new Taxonomy("E", "MFN1", "B", "N", "Other", 5, 100, null);
        Taxonomy b = new Taxonomy("E", "MFN1", "B", "N", "Other", 10, null, 200);

        cache.add(a);
        cache.add(b);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void prefersWeightFromLowestScorePerDimension() {
        Taxonomy lowScoreNet = new Taxonomy("E", "MFN1", "B", "N", "Other", 2, 999, null);
        Taxonomy highScoreBoth = new Taxonomy("E", "MFN1", "B", "N", "Other", 9, 100, 200);

        cache.add(highScoreBoth);
        cache.add(lowScoreNet);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(999);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void incomingWinsTieBreakForBothDimensions() {
        Taxonomy first = new Taxonomy("E", "MFN1", "B", "N", "Other", 5, 100, 200);
        Taxonomy second = new Taxonomy("E", "MFN1", "B", "N", "Other", 5, 150, 250);

        cache.add(first);
        cache.add(second);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(150);
        assertThat(result.grossWeightInGrams()).isEqualTo(250);
    }

    @Test
    void categorizedEntryIsNotClobberedByBetterScoreUncategorized() {
        // given
        cache.add(categorized("MFN-1", "CPU", 10));

        // when
        cache.add(uncategorized("MFN-1", 1));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(10, result.dataAccuracyScore());
    }

    @Test
    void incomingCategorizedEntryReplacesUncategorizedDespiteWorseScore() {
        // given
        cache.add(uncategorized("MFN-1", 1));

        // when
        cache.add(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void blankCategoryIsTreatedLikeOther() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "", 1, null, null));

        // when
        cache.add(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void twoCategorizedEntriesStillMergeByScore() {
        // given
        cache.add(categorized("MFN-1", "CPU", 10));

        // when
        cache.add(categorized("MFN-1", "GPU", 1));

        // then
        assertEquals("GPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void categorizedWinnerStillAdoptsWeightFromUncategorizedEntry() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Other", 1, 777, null));

        // when
        cache.add(categorized("MFN-1", "CPU", 10));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(777, result.netWeightInGrams());
    }

    @Test
    void findPrefersCategorizedEntryOverPendingWithBetterScore() {
        // given
        cache.add(uncategorized("MFN-PENDING", 1));
        cache.add(categorized("MFN-CAT", "CPU", 10));
        InventoryKey key = new InventoryKey(Set.of(), Set.of("MFN-PENDING", "MFN-CAT"));

        // when
        Taxonomy result = cache.find(key);

        // then
        assertEquals("CPU", result.category());
    }

    @Test
    void findReturnsPendingEntryWhenNoCategorizedCandidateExists() {
        // given
        cache.add(uncategorized("MFN-PENDING", 1));
        InventoryKey key = new InventoryKey(Set.of(), Set.of("MFN-PENDING"));

        // when
        Taxonomy result = cache.find(key);

        // then
        assertEquals("MFN-PENDING", result.mfn());
    }

    @Test
    void hasCategoryRejectsNullBlankAndOther() {
        assertFalse(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", null, 1, null, null)));
        assertFalse(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", " ", 1, null, null)));
        assertFalse(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", "Other", 1, null, null)));
        assertTrue(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", "CPU", 1, null, null)));
    }

    @Test
    void updateCategorySetsCategoryOnPendingEntryKeepingScoreAndWeights() {
        // given
        cache.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Other", 7, 100, 200));

        // when
        boolean updated = cache.updateCategory("MFN-1", "CPU");

        // then
        assertTrue(updated);
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(7, result.dataAccuracyScore());
        assertEquals(100, result.netWeightInGrams());
        assertEquals(200, result.grossWeightInGrams());
    }

    @Test
    void updateCategoryIgnoresOtherCategory() {
        // given
        cache.add(uncategorized("MFN-1", 7));

        // when / then
        assertFalse(cache.updateCategory("MFN-1", "Other"));
        assertEquals("Other", cache.findByMfn("MFN-1").category());
    }

    @Test
    void updateCategoryIgnoresUnknownCategoryKey() {
        // given
        cache.add(uncategorized("MFN-1", 7));

        // when / then
        assertFalse(cache.updateCategory("MFN-1", "NotARealCategory"));
        assertEquals("Other", cache.findByMfn("MFN-1").category());
    }

    @Test
    void updateCategoryIgnoresMissingEntry() {
        // when / then
        assertFalse(cache.updateCategory("MFN-GONE", "CPU"));
    }

    @Test
    void updateCategoryDoesNotOverwriteAlreadyCategorizedEntry() {
        // given
        cache.add(categorized("MFN-1", "GPU", 7));

        // when / then
        assertFalse(cache.updateCategory("MFN-1", "CPU"));
        assertEquals("GPU", cache.findByMfn("MFN-1").category());
    }

    private static Taxonomy taxonomy(String mfn, int score, Integer weight) {
        return taxonomyNamed(mfn, score, weight, "Name");
    }

    private static Taxonomy taxonomyNamed(String mfn, int score, Integer weight, String name) {
        return new Taxonomy("1234567890123", mfn, "Brand", name,
                "Laptops", score, weight, null);
    }

    private static Taxonomy categorized(String mfn, String category, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy uncategorized(String mfn, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", "Other", score, null, null);
    }
}
