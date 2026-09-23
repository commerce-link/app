package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void singleCommitStoresWeight() {
        store(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").netWeightInGrams());
    }

    @Test
    void betterScoreRecordWithoutWeightKeepsPreviousWeight() {
        store(taxonomy("MFN-1", 10, 1300));
        store(taxonomyNamed("MFN-1", 1, null, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void betterScoreRecordWithWeightReplacesEverything() {
        store(taxonomy("MFN-1", 10, 1300));
        store(taxonomyNamed("MFN-1", 1, 1500, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1500, result.netWeightInGrams());
    }

    @Test
    void worseScoreRecordWithWeightFillsMissingWeightOnly() {
        store(taxonomyNamed("MFN-1", 1, null, "BestName"));
        store(taxonomyNamed("MFN-1", 10, 1300, "WorseName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BestName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void twoSourcesWithWeightLowerScoreWins() {
        store(taxonomy("MFN-1", 10, 1500));
        store(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").netWeightInGrams());
    }

    @Test
    void blankMfnIsNoop() {
        store(taxonomy("", 5, 1300));
        store(taxonomy("   ", 5, 1300));

        assertEquals(0, cache.size());
    }

    @Test
    void concurrentCommitsDoNotDropWeight() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Taxonomy bestSource = taxonomyNamed("MFN-1", 5, 1000, "Best");
        List<Taxonomy> inputs = IntStream.range(0, 200)
                .mapToObj(i -> taxonomyNamed("MFN-1", 10 + (i % 3), 2000 + (i % 5), "N" + i))
                .toList();

        pool.submit(() -> store(bestSource));
        for (Taxonomy t : inputs) pool.submit(() -> store(t));
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals(1000, result.netWeightInGrams());
        assertEquals("Best", result.name());
    }

    @Test
    void mergesNetFromOneAndGrossFromOtherIndependently() {
        Taxonomy a = new Taxonomy("E", "MFN1", "B", "N", null, 5, 100, null);
        Taxonomy b = new Taxonomy("E", "MFN1", "B", "N", null, 10, null, 200);

        store(a);
        store(b);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void prefersWeightFromLowestScorePerDimension() {
        Taxonomy lowScoreNet = new Taxonomy("E", "MFN1", "B", "N", null, 2, 999, null);
        Taxonomy highScoreBoth = new Taxonomy("E", "MFN1", "B", "N", null, 9, 100, 200);

        store(highScoreBoth);
        store(lowScoreNet);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(999);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void incomingWinsTieBreakForBothDimensions() {
        Taxonomy first = new Taxonomy("E", "MFN1", "B", "N", null, 5, 100, 200);
        Taxonomy second = new Taxonomy("E", "MFN1", "B", "N", null, 5, 150, 250);

        store(first);
        store(second);

        Taxonomy result = cache.findByMfn("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(150);
        assertThat(result.grossWeightInGrams()).isEqualTo(250);
    }

    @Test
    void categorizedEntryIsNotClobberedByBetterScoreUncategorized() {
        // given
        store(categorized("MFN-1", "CPU", 10));

        // when
        store(uncategorized("MFN-1", 1));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(10, result.dataAccuracyScore());
    }

    @Test
    void incomingCategorizedEntryReplacesUncategorizedDespiteWorseScore() {
        // given
        store(uncategorized("MFN-1", 1));

        // when
        store(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void blankCategoryIsTreatedAsUncategorized() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "", 1, null, null));

        // when
        store(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void twoCategorizedEntriesStillMergeByScore() {
        // given
        store(categorized("MFN-1", "CPU", 10));

        // when
        store(categorized("MFN-1", "GPU", 1));

        // then
        assertEquals("GPU", cache.findByMfn("MFN-1").category());
    }

    @Test
    void categorizedWinnerStillAdoptsWeightFromUncategorizedEntry() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 1, 777, null));

        // when
        store(categorized("MFN-1", "CPU", 10));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(777, result.netWeightInGrams());
    }

    @Test
    void mergePreservesRawCategoryOfWinningPendingEntry() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 10, null, 500, "First"));

        // when
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 1, 300, null, "Second"));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("Second", result.rawCategory());
    }

    @Test
    void mergePreservesCategoryIdOfWinningEntry() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 10, null, 500, "Raw", "111"));

        // when
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 1, 300, null, "Raw", "999"));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("999", result.categoryId());
    }

    @Test
    void pimCorrectionWithoutIdReplacesPreviouslyResolvedCategoryId() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Karty graficzne", 3, null, null, "Raw", "1613"));

        // when
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Karty graficzne", 0, null, null, "Raw", null));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("Karty graficzne", result.category());
        assertNull(result.categoryId());
    }




    @Test
    void updateCategorySetsCategoryOnPendingEntryKeepingScoreAndWeights() {
        // given
        store(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 7, 100, 200));

        // when
        boolean updated = cache.updateCategory("MFN-1", "CPU", "301");

        // then
        assertTrue(updated);
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals("301", result.categoryId());
        assertEquals(7, result.dataAccuracyScore());
        assertEquals(100, result.netWeightInGrams());
        assertEquals(200, result.grossWeightInGrams());
    }

    @Test
    void updateCategoryAcceptsArbitraryCategory() {
        // given
        store(uncategorized("MFN-1", 7));

        // when / then
        assertTrue(cache.updateCategory("MFN-1", "Cokolwiek", "999"));
        assertEquals("Cokolwiek", cache.findByMfn("MFN-1").category());
    }

    @Test
    void updateCategoryIgnoresBlankMfnAndNullCategory() {
        // given
        store(uncategorized("MFN-1", 7));

        // when / then
        assertFalse(cache.updateCategory("", "CPU", null));
        assertFalse(cache.updateCategory("   ", "CPU", null));
        assertFalse(cache.updateCategory("MFN-1", null, null));
        assertFalse(cache.updateCategory("MFN-1", "", null));
        assertNull(cache.findByMfn("MFN-1").category());
    }

    @Test
    void updateCategoryRejectsResolutionWithoutCategoryIdAndLeavesRowPending() {
        // given
        store(uncategorized("MFN-1", 7));

        // when
        boolean updated = cache.updateCategory("MFN-1", "CPU", null);

        // then
        assertFalse(updated);
        assertFalse(Taxonomy.hasCategory(cache.findByMfn("MFN-1")));
    }

    @Test
    void rejectedResolutionCanBeRetriedLaterWithAnId() {
        // given
        store(uncategorized("MFN-1", 7));
        cache.updateCategory("MFN-1", "CPU", " ");

        // when
        boolean updated = cache.updateCategory("MFN-1", "CPU", "989");

        // then
        assertTrue(updated);
        assertEquals("989", cache.findByMfn("MFN-1").categoryId());
    }

    @Test
    void updateCategoryIgnoresMissingEntry() {
        // when / then
        assertFalse(cache.updateCategory("MFN-GONE", "CPU", "989"));
    }

    @Test
    void updateCategoryDoesNotOverwriteAlreadyCategorizedEntry() {
        // given
        store(categorized("MFN-1", "GPU", 7));

        // when / then
        assertFalse(cache.updateCategory("MFN-1", "CPU", "989"));
        assertEquals("GPU", cache.findByMfn("MFN-1").category());
    }





    @Test
    void commitInternsBrandCategoryAndCategoryIdAcrossEntries() {
        // given
        Taxonomy first = new Taxonomy("1111111111111", "MFN-1", new String("Brand"), "Name",
                new String("Laptops"), 5, null, null, null, new String("301"));
        Taxonomy second = new Taxonomy("2222222222222", "MFN-2", new String("Brand"), "Name2",
                new String("Laptops"), 5, null, null, null, new String("301"));

        // when
        store(first);
        store(second);

        // then
        Taxonomy stored1 = cache.findByMfn("MFN-1");
        Taxonomy stored2 = cache.findByMfn("MFN-2");
        assertThat(stored1.brand()).isSameAs(stored2.brand());
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void onStartUpInternsBrandCategoryAndCategoryIdAcrossSnapshotRows() {
        // given
        Taxonomy first = new Taxonomy("1111111111111", "MFN-1", new String("Brand"), "Name",
                new String("Laptops"), 5, null, null, null, new String("301"));
        Taxonomy second = new Taxonomy("2222222222222", "MFN-2", new String("Brand"), "Name2",
                new String("Laptops"), 5, null, null, null, new String("301"));
        TaxonomyRepository repo = Mockito.mock(TaxonomyRepository.class);
        Mockito.when(repo.loadNewest()).thenReturn(Pair.of("snapshot.csv", List.of(first, second)));
        TaxonomyCache loaded = new TaxonomyCache(repo);

        // when
        loaded.onStartUp();

        // then
        Taxonomy stored1 = loaded.findByMfn("MFN-1");
        Taxonomy stored2 = loaded.findByMfn("MFN-2");
        assertThat(stored1.brand()).isSameAs(stored2.brand());
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void updateCategoryInternsCategoryAndCategoryIdAcrossEntries() {
        // given
        store(uncategorized("MFN-1", 7));
        store(uncategorized("MFN-2", 7));

        // when
        cache.updateCategory("MFN-1", new String("CPU"), new String("301"));
        cache.updateCategory("MFN-2", new String("CPU"), new String("301"));

        // then
        Taxonomy stored1 = cache.findByMfn("MFN-1");
        Taxonomy stored2 = cache.findByMfn("MFN-2");
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void updateCategorySharesPoolWithValuesAddedViaCommit() {
        // given
        store(categorized("MFN-1", new String("CPU"), 10));
        store(uncategorized("MFN-2", 7));

        // when
        cache.updateCategory("MFN-2", new String("CPU"), "301");

        // then
        Taxonomy stored1 = cache.findByMfn("MFN-1");
        Taxonomy stored2 = cache.findByMfn("MFN-2");
        assertThat(stored1.category()).isSameAs(stored2.category());
    }

    @Test
    void poolingHandlesNullBrandCategoryAndCategoryIdWithoutThrowing() {
        // when
        store(new Taxonomy("1234567890123", "MFN-1", null, "Name", null, 1, null, null, null, null));

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertThat(result.brand()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.categoryId()).isNull();
    }

    @Test
    void commitPreservesAllFieldsExactlyIncludingLeadingZeroEan() {
        // given
        Taxonomy input = new Taxonomy("0012345678905", "MFN-ZERO", new String("Brand"), "Name",
                new String("Laptops"), 5, 1300, 1400, "RawCat", new String("301"));

        // when
        store(input);

        // then
        Taxonomy result = cache.findByMfn("MFN-ZERO");
        assertThat(result.ean()).isEqualTo("0012345678905");
        assertThat(result).isEqualTo(input);
    }

    @Test
    void leadingZeroEanRowsAreInternedTooOnceNormalizationMovesToTheBoundary() {
        // given
        Taxonomy first = new Taxonomy("0012345678905", "MFN-ZERO-1", new String("Brand"), "Name",
                new String("Laptops"), 5, null, null, null, new String("301"));
        Taxonomy second = new Taxonomy("1234567890123", "MFN-NORMAL", new String("Brand"), "Name2",
                new String("Laptops"), 5, null, null, null, new String("301"));

        // when
        store(first);
        store(second);

        // then
        Taxonomy stored1 = cache.findByMfn("MFN-ZERO-1");
        Taxonomy stored2 = cache.findByMfn("MFN-NORMAL");
        assertThat(stored1.brand()).isSameAs(stored2.brand());
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void startMergeSkipsBlankProductCodesInsteadOfThrowing() {
        // given
        store(categorized("MFN-1", "CPU", 5));

        // when
        TaxonomyMerge merge = cache.startMerge(Arrays.asList("MFN-1", null, "", "   "));

        // then
        assertEquals("CPU", merge.current("MFN-1").category());
        assertNull(merge.current(null));
    }

    @Test
    void commitKeepsACategoryThatArrivedWhileTheChunkWasInFlight() {
        // given
        store(uncategorized("MFN-1", 5));
        TaxonomyMerge merge = cache.startMerge(List.of("MFN-1"));
        merge.add(uncategorized("MFN-1", 3));

        // when
        cache.updateCategory("MFN-1", "CPU", "301");
        cache.commit(merge);

        // then
        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals("301", result.categoryId());
    }

    @Test
    void commitInternsRepeatedBrandAndCategoryAcrossSeparateChunks() {
        // given
        store(new Taxonomy("1111111111111", "MFN-1", new String("Brand"), "Name",
                new String("Laptops"), 5, null, null, null, new String("301")));

        // when
        store(new Taxonomy("2222222222222", "MFN-2", new String("Brand"), "Name2",
                new String("Laptops"), 5, null, null, null, new String("301")));

        // then
        Taxonomy stored1 = cache.findByMfn("MFN-1");
        Taxonomy stored2 = cache.findByMfn("MFN-2");
        assertThat(stored1.brand()).isSameAs(stored2.brand());
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    private void store(Taxonomy... incoming) {
        TaxonomyMerge merge = cache.startMerge(Arrays.stream(incoming).map(Taxonomy::mfn).toList());
        Arrays.stream(incoming).forEach(merge::add);
        cache.commit(merge);
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
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", null, score, null, null);
    }
}
