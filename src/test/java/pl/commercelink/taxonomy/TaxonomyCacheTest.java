package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import pl.commercelink.inventory.InventoryKey;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyCacheTest {

    private static final String EAN_1 = "5901234567801";
    private static final String EAN_2 = "5901234567802";
    private static final String EAN_3 = "5901234567803";

    private TaxonomyCache cache;

    @BeforeEach
    void setUp() {
        cache = loadedWith(new ArrayList<>());
    }

    @Test
    void betterScoreRecordWithoutWeightKeepsPreviousWeight() {
        cache.add(taxonomy(EAN_1, 10, 1300));
        cache.add(taxonomyNamed(EAN_1, 1, null, "BetterName"));

        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("BetterName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void betterScoreRecordWithWeightReplacesEverything() {
        cache.add(taxonomy(EAN_1, 10, 1300));
        cache.add(taxonomyNamed(EAN_1, 1, 1500, "BetterName"));

        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("BetterName", result.name());
        assertEquals(1500, result.netWeightInGrams());
    }

    @Test
    void worseScoreRecordWithWeightFillsMissingWeightOnly() {
        cache.add(taxonomyNamed(EAN_1, 1, null, "BestName"));
        cache.add(taxonomyNamed(EAN_1, 10, 1300, "WorseName"));

        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("BestName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void blankEanIsNoop() {
        cache.add(taxonomy("", 5, 1300));
        cache.add(taxonomy("   ", 5, 1300));
        cache.add(taxonomy(null, 5, 1300));

        assertEquals(0, cache.size());
    }

    @Test
    void concurrentAddsDoNotDropWeight() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Taxonomy bestSource = taxonomyNamed(EAN_1, 5, 1000, "Best");
        List<Taxonomy> inputs = IntStream.range(0, 200)
                .mapToObj(i -> taxonomyNamed(EAN_1, 10 + (i % 3), 2000 + (i % 5), "N" + i))
                .toList();

        pool.submit(() -> cache.add(bestSource));
        for (Taxonomy t : inputs) pool.submit(() -> cache.add(t));
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals(1000, result.netWeightInGrams());
        assertEquals("Best", result.name());
    }

    @Test
    void mergesNetFromOneAndGrossFromOtherIndependently() {
        Taxonomy a = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 5, 100, null);
        Taxonomy b = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 10, null, 200);

        cache.add(a);
        cache.add(b);

        Taxonomy result = cache.findByEan(EAN_1);
        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void prefersWeightFromLowestScorePerDimension() {
        Taxonomy lowScoreNet = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 2, 999, null);
        Taxonomy highScoreBoth = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 9, 100, 200);

        cache.add(highScoreBoth);
        cache.add(lowScoreNet);

        Taxonomy result = cache.findByEan(EAN_1);
        assertThat(result.netWeightInGrams()).isEqualTo(999);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void incomingWinsTieBreakForBothDimensions() {
        Taxonomy first = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 5, 100, 200);
        Taxonomy second = new Taxonomy(EAN_1, "MFN1", "B", "N", null, 5, 150, 250);

        cache.add(first);
        cache.add(second);

        Taxonomy result = cache.findByEan(EAN_1);
        assertThat(result.netWeightInGrams()).isEqualTo(150);
        assertThat(result.grossWeightInGrams()).isEqualTo(250);
    }

    @Test
    void categorizedEntryIsNotClobberedByBetterScoreUncategorized() {
        // given
        cache.add(categorized(EAN_1, "CPU", 10));

        // when
        cache.add(uncategorized(EAN_1, 1));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("CPU", result.category());
        assertEquals(10, result.dataAccuracyScore());
    }

    @Test
    void incomingCategorizedEntryReplacesUncategorizedDespiteWorseScore() {
        // given
        cache.add(uncategorized(EAN_1, 1));

        // when
        cache.add(categorized(EAN_1, "CPU", 10));

        // then
        assertEquals("CPU", cache.findByEan(EAN_1).category());
    }

    @Test
    void blankCategoryIsTreatedAsUncategorized() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "", 1, null, null));

        // when
        cache.add(categorized(EAN_1, "CPU", 10));

        // then
        assertEquals("CPU", cache.findByEan(EAN_1).category());
    }

    @Test
    void twoCategorizedEntriesStillMergeByScore() {
        // given
        cache.add(categorized(EAN_1, "CPU", 10));

        // when
        cache.add(categorized(EAN_1, "GPU", 1));

        // then
        assertEquals("GPU", cache.findByEan(EAN_1).category());
    }

    @Test
    void categorizedWinnerStillAdoptsWeightFromUncategorizedEntry() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", null, 1, 777, null));

        // when
        cache.add(categorized(EAN_1, "CPU", 10));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("CPU", result.category());
        assertEquals(777, result.netWeightInGrams());
    }

    @Test
    void mergePreservesRawCategoryOfWinningPendingEntry() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", null, 10, null, 500, "First"));

        // when
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", null, 1, 300, null, "Second"));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("Second", result.rawCategory());
    }

    @Test
    void mergePreservesCategoryIdOfWinningEntry() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 10, null, 500, "Raw", "111"));

        // when
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 1, 300, null, "Raw", "999"));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("999", result.categoryId());
    }

    @Test
    void pimCorrectionWithoutIdReplacesPreviouslyResolvedCategoryId() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "Karty graficzne", 3, null, null, "Raw", "1613"));

        // when
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "Karty graficzne", 0, null, null, "Raw", null));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("Karty graficzne", result.category());
        assertNull(result.categoryId());
    }

    @Test
    void rowsOfOneEanMergeIntoOneRecord() {
        // given
        cache.add(new Taxonomy(EAN_1, "SM-G991B", "Samsung", "Galaxy S21", null, 1, null, null));

        // when
        cache.add(new Taxonomy(EAN_1, "SM-G991BZADEUE", "Samsung", "Galaxy S21 EU", "Smartfony", 10, null, null));

        // then
        assertEquals(1, cache.size());
        assertEquals("Smartfony", cache.findByEan(EAN_1).category());
    }

    @Test
    void twoEansOfOneProductKeepTwoRecords() {
        // given
        cache.add(new Taxonomy(EAN_1, "920-002509", "Logitech", "K120 PL", "Klawiatury", 3, null, null));

        // when
        cache.add(new Taxonomy(EAN_2, "920-002509", "Logitech", "K120 DE", "Klawiatury", 1, null, null));

        // then
        assertEquals(2, cache.size());
        assertEquals("K120 PL", cache.findByEan(EAN_1).name());
        assertEquals("K120 DE", cache.findByEan(EAN_2).name());
    }

    @Test
    void groupTaxonomyIsTheBestRecordOfItsEans() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-A", "Brand", "Name A", "CPU", 5, null, null));
        cache.add(new Taxonomy(EAN_2, "MFN-B", "Brand", "Name B", "CPU", 2, null, null));
        cache.add(new Taxonomy(EAN_3, "MFN-C", "Brand", "Name C", null, 1, null, null));
        cache.add(new Taxonomy("4006381333931", "MFN-OTHER", "Other", "Other product", "GPU", 0, null, null));
        InventoryKey key = new InventoryKey(Set.of(EAN_1, EAN_2, EAN_3), Set.of("MFN-OTHER"));

        // when
        Taxonomy result = cache.find(key);

        // then
        assertEquals("Name B", result.name());
    }

    @Test
    void findPrefersCategorizedEntryOverPendingWithBetterScore() {
        // given
        cache.add(uncategorized(EAN_1, 1));
        cache.add(categorized(EAN_2, "CPU", 10));
        InventoryKey key = new InventoryKey(Set.of(EAN_1, EAN_2), Set.of());

        // when
        Taxonomy result = cache.find(key);

        // then
        assertEquals("CPU", result.category());
    }

    @Test
    void findReturnsPendingEntryWhenNoCategorizedCandidateExists() {
        // given
        cache.add(uncategorized(EAN_1, 1));
        InventoryKey key = new InventoryKey(Set.of(EAN_1), Set.of());

        // when
        Taxonomy result = cache.find(key);

        // then
        assertEquals(EAN_1, result.ean());
    }

    @Test
    void findGivesNothingForAKeyWithoutEan() {
        // given
        cache.add(categorized(EAN_1, "CPU", 1));

        // when
        Taxonomy result = cache.find(InventoryKey.fromMfn("MFN-1"));

        // then
        assertThat(result).isSameAs(Taxonomy.EMPTY);
    }

    @Test
    void findByMfnGivesNothingWhenTheCodeHasTwoBrands() {
        // given
        cache.add(new Taxonomy(EAN_1, "920", "Logitech", "Mysz", "Myszki", 1, null, null));
        cache.add(new Taxonomy(EAN_2, "920", "Genesis", "Kabel", "Kable", 1, null, null));

        // when / then
        assertNull(cache.findByMfn("920"));
    }

    @Test
    void findByMfnGivesTheBestRecordWhenTheCodeHasOneBrand() {
        // given
        cache.add(new Taxonomy(EAN_1, "920", "Logitech", "K120 PL", "Klawiatury", 5, null, null));
        cache.add(new Taxonomy(EAN_2, "920", "logitech ", "K120 DE", "Klawiatury", 2, null, null));

        // when
        Taxonomy result = cache.findByMfn("920");

        // then
        assertEquals("K120 DE", result.name());
    }

    @Test
    void findByMfnCountsTheBrandsTheCodeCameWith() {
        // given
        cache.add(new Taxonomy(EAN_1, "920", "Logitech", "K120", "Klawiatury", 1, null, null));
        cache.add(new Taxonomy(EAN_1, "920", "Natec", "K120", null, 9, null, null));

        // when / then
        assertNull(cache.findByMfn("920"));
    }

    @Test
    void rowWithoutBrandIsNotFoundByItsCode() {
        // given
        cache.add(new Taxonomy(EAN_1, "920", null, "K120", "Klawiatury", 1, null, null));

        // when / then
        assertNull(cache.findByMfn("920"));
    }

    @Test
    void findByMfnGivesNothingForAnUnknownOrBlankCode() {
        // given
        cache.add(categorized(EAN_1, "CPU", 1));

        // when / then
        assertNull(cache.findByMfn("UNKNOWN"));
        assertNull(cache.findByMfn(null));
        assertNull(cache.findByMfn(" "));
    }

    @Test
    void fileWithOneRowPerCodeLoadsByEan() {
        // given
        TaxonomyCache loaded = loadedWith(List.of(
                new Taxonomy(EAN_1, "MFN-A", "Brand", "Name A", "CPU", 5, null, null),
                new Taxonomy(EAN_1, "MFN-B", "Brand", "Name B", "CPU", 2, null, null),
                new Taxonomy(EAN_2, "MFN-C", "Brand", "Name C", "GPU", 3, null, null)));

        // when / then
        assertEquals(2, loaded.size());
        assertEquals("Name B", loaded.findByEan(EAN_1).name());
        assertEquals("Name B", loaded.findByMfn("MFN-A").name());
        assertEquals("Name C", loaded.findByMfn("MFN-C").name());
    }

    @Test
    void startUpReplacesWhatWasInMemory() {
        // given
        TaxonomyRepository repo = Mockito.mock(TaxonomyRepository.class);
        Mockito.when(repo.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        TaxonomyCache reloaded = new TaxonomyCache(repo);
        reloaded.onStartUp();
        reloaded.add(categorized(EAN_1, "CPU", 1));
        Mockito.when(repo.loadNewest()).thenReturn(Pair.of("snapshot.csv", List.of(uncategorized(EAN_1, 5))));

        // when
        reloaded.onStartUp();

        // then
        assertNull(reloaded.findByEan(EAN_1).category());
        assertEquals(1, reloaded.pendingCount());
    }

    @Test
    void findByEanPrefersTheRecordWithACategoryAmongTheCodesOfOneEan() {
        // given
        cache.add(new Taxonomy("5901234567890", "SM-G991B", "Samsung", "Galaxy S21", null, 1, null, null));
        cache.add(new Taxonomy("5901234567890", "SM-G991BZADEUE", "Samsung", "Galaxy S21 EU", "Smartfony", 10, null, null));
        cache.add(new Taxonomy("4006381333931", "OTHER-1", "Brand", "Other", "Smartfony", 1, null, null));

        // when
        Taxonomy result = cache.findByEan("5901234567890");

        // then
        assertThat(result.mfn()).isEqualTo("SM-G991BZADEUE");
    }

    @Test
    void findByEanPrefersTheBetterScoreWhenBothHaveACategory() {
        // given
        cache.add(new Taxonomy("5901234567890", "SM-G991B", "Samsung", "Galaxy S21", "Smartfony", 7, null, null));
        cache.add(new Taxonomy("5901234567890", "SM-G991BZADEUE", "Samsung", "Galaxy S21 EU", "Smartfony", 2, null, null));

        // when
        Taxonomy result = cache.findByEan("5901234567890");

        // then
        assertThat(result.mfn()).isEqualTo("SM-G991BZADEUE");
    }

    @Test
    void findByEanMatchesARecordStoredWithLeadingZeros() {
        // given
        cache.add(new Taxonomy("0012345678905", "MFN-ZERO", "Brand", "Name", "Laptops", 5, null, null));

        // when
        Taxonomy result = cache.findByEan("012345678905");

        // then
        assertThat(result.mfn()).isEqualTo("MFN-ZERO");
    }

    @Test
    void findByEanFindsNothingForAnUnknownOrBlankEan() {
        // given
        cache.add(new Taxonomy(null, "MFN-NO-EAN", "Brand", "Name", "Laptops", 5, null, null));

        // when / then
        assertThat(cache.findByEan("4006381333931")).isNull();
        assertThat(cache.findByEan(null)).isNull();
        assertThat(cache.findByEan(" ")).isNull();
    }

    @Test
    void hasCategoryRejectsNullAndBlank() {
        assertFalse(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", null, 1, null, null)));
        assertFalse(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", " ", 1, null, null)));
        assertTrue(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", "Other", 1, null, null)));
        assertTrue(TaxonomyCache.hasCategory(new Taxonomy("E", "M", "B", "N", "CPU", 1, null, null)));
    }

    @Test
    void updateCategorySetsCategoryOnPendingEntryKeepingScoreAndWeights() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", null, 7, 100, 200));

        // when
        boolean updated = cache.updateCategory(EAN_1, "CPU", "301");

        // then
        assertTrue(updated);
        Taxonomy result = cache.findByEan(EAN_1);
        assertEquals("CPU", result.category());
        assertEquals("301", result.categoryId());
        assertEquals(7, result.dataAccuracyScore());
        assertEquals(100, result.netWeightInGrams());
        assertEquals(200, result.grossWeightInGrams());

        // free-text categories are accepted, not limited to a whitelist
        cache.add(uncategorized(EAN_2, 7));
        assertTrue(cache.updateCategory(EAN_2, "Cokolwiek", "999"));
        assertEquals("Cokolwiek", cache.findByEan(EAN_2).category());
    }

    @Test
    void updateCategoryGoesOnlyToTheRecordOfItsEan() {
        // given
        cache.add(new Taxonomy(EAN_1, "920", "Logitech", "Mysz", null, 7, null, null));
        cache.add(new Taxonomy(EAN_2, "920", "Genesis", "Kabel", null, 7, null, null));

        // when
        cache.updateCategory(EAN_1, "Myszki", "301");

        // then
        assertEquals("Myszki", cache.findByEan(EAN_1).category());
        assertNull(cache.findByEan(EAN_2).category());
        assertEquals(1, cache.pendingCount());
    }

    @Test
    void updateCategoryIgnoresBlankEanAndNullCategory() {
        // given
        cache.add(uncategorized(EAN_1, 7));

        // when / then
        assertFalse(cache.updateCategory("", "CPU", null));
        assertFalse(cache.updateCategory("   ", "CPU", null));
        assertFalse(cache.updateCategory(null, "CPU", "301"));
        assertFalse(cache.updateCategory(EAN_1, null, null));
        assertFalse(cache.updateCategory(EAN_1, "", null));
        assertNull(cache.findByEan(EAN_1).category());
    }

    @Test
    void updateCategoryRejectsResolutionWithoutCategoryIdAndLeavesRowPending() {
        // given
        cache.add(uncategorized(EAN_1, 7));

        // when
        boolean updated = cache.updateCategory(EAN_1, "CPU", null);

        // then
        assertFalse(updated);
        assertEquals(1, cache.pendingCount());
        assertFalse(TaxonomyCache.hasCategory(cache.findByEan(EAN_1)));
    }

    @Test
    void rejectedResolutionCanBeRetriedLaterWithAnId() {
        // given
        cache.add(uncategorized(EAN_1, 7));
        cache.updateCategory(EAN_1, "CPU", " ");

        // when
        boolean updated = cache.updateCategory(EAN_1, "CPU", "989");

        // then
        assertTrue(updated);
        assertEquals(0, cache.pendingCount());
        assertEquals("989", cache.findByEan(EAN_1).categoryId());
    }

    @Test
    void updateCategoryIgnoresMissingEntry() {
        // when / then
        assertFalse(cache.updateCategory(EAN_3, "CPU", "989"));
    }

    @Test
    void updateCategoryDoesNotOverwriteAlreadyCategorizedEntry() {
        // given
        cache.add(categorized(EAN_1, "GPU", 7));

        // when / then
        assertFalse(cache.updateCategory(EAN_1, "CPU", "989"));
        assertEquals("GPU", cache.findByEan(EAN_1).category());
    }

    @Test
    void pendingCountTracksEntriesWithoutCategory() {
        // when
        cache.add(uncategorized(EAN_1, 5));
        cache.add(uncategorized(EAN_1, 3));
        cache.add(categorized(EAN_2, "CPU", 5));

        // then
        assertEquals(1, cache.pendingCount());
    }

    @Test
    void pendingCountDropsWhenCategorizedAddOvertakesPendingEntry() {
        // given
        cache.add(uncategorized(EAN_1, 1));
        assertEquals(1, cache.pendingCount());

        // when
        cache.add(categorized(EAN_1, "CPU", 10));

        // then
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void pendingCountDropsOnceForRepeatedUpdateCategory() {
        // given
        cache.add(uncategorized(EAN_1, 7));

        // when
        cache.updateCategory(EAN_1, "CPU", "989");
        cache.updateCategory(EAN_1, "GPU", "170");

        // then
        assertEquals(0, cache.pendingCount());
    }

    @Test
    void pendingCountIncludesSnapshotRowsLoadedWithoutCategory() {
        // when
        TaxonomyCache loaded = loadedWith(List.of(uncategorized(EAN_1, 5), categorized(EAN_2, "CPU", 5)));

        // then
        assertEquals(1, loaded.pendingCount());
    }

    @Test
    void losingRowLeavesTheStoredRecordUntouched() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 1, 100, 200));
        Taxonomy stored = cache.findByEan(EAN_1);
        Taxonomy losing = new Taxonomy(EAN_1, "MFN-2", "Brand", "Other name", "GPU", 5, 300, 400);

        // when
        cache.add(losing);

        // then
        assertThat(cache.findByEan(EAN_1)).isSameAs(stored);
    }

    @Test
    void rowEqualToTheStoredRecordDoesNotReplaceIt() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 1, 100, 200, "Raw", "301"));
        Taxonomy stored = cache.findByEan(EAN_1);

        // when
        cache.add(new Taxonomy(EAN_1, "MFN-1", new String("Brand"), "Name", new String("CPU"), 1, 100, 200,
                "Raw", new String("301")));

        // then
        assertThat(cache.findByEan(EAN_1)).isSameAs(stored);
    }

    @Test
    void storedRecordIsInternedOnlyWhenItWins() {
        // given
        cache.add(new Taxonomy(EAN_1, "MFN-1", "Brand", "Name", "CPU", 1, 100, 200));
        cache.add(new Taxonomy(EAN_1, "MFN-1", new String("Loser"), "Name", "CPU", 5, 100, 200));
        String winnerBrand = new String("Loser");

        // when
        cache.add(new Taxonomy(EAN_2, "MFN-2", winnerBrand, "Name", "CPU", 1, 100, 200));

        // then
        assertThat(cache.findByEan(EAN_2).brand()).isSameAs(winnerBrand);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "add, 1111111111111, 2222222222222, MFN-1, MFN-2, false",
            "onStartUp snapshot rows, 1111111111111, 2222222222222, MFN-1, MFN-2, true",
            "leading-zero EAN, 0012345678905, 1234567890123, MFN-ZERO-1, MFN-NORMAL, false"
    })
    void internsBrandCategoryAndCategoryIdAcrossEntries(String path, String firstEan, String secondEan,
                                                        String firstMfn, String secondMfn, boolean viaStartUp) {
        // given
        Taxonomy first = new Taxonomy(firstEan, firstMfn, new String("Brand"), "Name",
                new String("Laptops"), 5, null, null, null, new String("301"));
        Taxonomy second = new Taxonomy(secondEan, secondMfn, new String("Brand"), "Name2",
                new String("Laptops"), 5, null, null, null, new String("301"));
        TaxonomyCache target = cache;

        // when
        if (viaStartUp) {
            target = loadedWith(List.of(first, second));
        } else {
            cache.add(first);
            cache.add(second);
        }

        // then
        Taxonomy stored1 = target.findByEan(firstEan);
        Taxonomy stored2 = target.findByEan(secondEan);
        assertThat(stored1.brand()).isSameAs(stored2.brand());
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void updateCategoryInternsCategoryAndCategoryIdAcrossEntries() {
        // given
        cache.add(uncategorized(EAN_1, 7));
        cache.add(uncategorized(EAN_2, 7));

        // when
        cache.updateCategory(EAN_1, new String("CPU"), new String("301"));
        cache.updateCategory(EAN_2, new String("CPU"), new String("301"));

        // then
        Taxonomy stored1 = cache.findByEan(EAN_1);
        Taxonomy stored2 = cache.findByEan(EAN_2);
        assertThat(stored1.category()).isSameAs(stored2.category());
        assertThat(stored1.categoryId()).isSameAs(stored2.categoryId());
    }

    @Test
    void updateCategorySharesPoolWithValuesAddedViaAdd() {
        // given
        cache.add(categorized(EAN_1, new String("CPU"), 10));
        cache.add(uncategorized(EAN_2, 7));

        // when
        cache.updateCategory(EAN_2, new String("CPU"), "301");

        // then
        Taxonomy stored1 = cache.findByEan(EAN_1);
        Taxonomy stored2 = cache.findByEan(EAN_2);
        assertThat(stored1.category()).isSameAs(stored2.category());
    }

    @Test
    void poolingHandlesNullBrandCategoryAndCategoryIdWithoutThrowing() {
        // when
        cache.add(new Taxonomy(EAN_1, "MFN-1", null, "Name", null, 1, null, null, null, null));

        // then
        Taxonomy result = cache.findByEan(EAN_1);
        assertThat(result.brand()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.categoryId()).isNull();
    }

    @Test
    void addPreservesAllFieldsExactlyIncludingLeadingZeroEan() {
        // given
        Taxonomy input = new Taxonomy("0012345678905", "MFN-ZERO", new String("Brand"), "Name",
                new String("Laptops"), 5, 1300, 1400, "RawCat", new String("301"));

        // when
        cache.add(input);

        // then
        Taxonomy result = cache.findByEan("0012345678905");
        assertThat(result.ean()).isEqualTo("0012345678905");
        assertThat(result).isEqualTo(input);
    }

    private static TaxonomyCache loadedWith(List<Taxonomy> rows) {
        TaxonomyRepository repo = Mockito.mock(TaxonomyRepository.class);
        Mockito.when(repo.loadNewest()).thenReturn(Pair.of("snapshot.csv", rows));
        TaxonomyCache loaded = new TaxonomyCache(repo);
        loaded.onStartUp();
        return loaded;
    }

    private static Taxonomy taxonomy(String ean, int score, Integer weight) {
        return taxonomyNamed(ean, score, weight, "Name");
    }

    private static Taxonomy taxonomyNamed(String ean, int score, Integer weight, String name) {
        return new Taxonomy(ean, "MFN-1", "Brand", name, "Laptops", score, weight, null);
    }

    private static Taxonomy categorized(String ean, String category, int score) {
        return new Taxonomy(ean, "MFN-1", "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy uncategorized(String ean, int score) {
        return new Taxonomy(ean, "MFN-1", "Brand", "Name", null, score, null, null);
    }
}
