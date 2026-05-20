package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void single_add_stores_weight() {
        cache.add(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").weightInGrams());
    }

    @Test
    void better_score_record_without_weight_keeps_previous_weight() {
        cache.add(taxonomy("MFN-1", 10, 1300));
        cache.add(taxonomyNamed("MFN-1", 1, null, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1300, result.weightInGrams());
    }

    @Test
    void better_score_record_with_weight_replaces_everything() {
        cache.add(taxonomy("MFN-1", 10, 1300));
        cache.add(taxonomyNamed("MFN-1", 1, 1500, "BetterName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1500, result.weightInGrams());
    }

    @Test
    void worse_score_record_with_weight_fills_missing_weight_only() {
        cache.add(taxonomyNamed("MFN-1", 1, null, "BestName"));
        cache.add(taxonomyNamed("MFN-1", 10, 1300, "WorseName"));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals("BestName", result.name());
        assertEquals(1300, result.weightInGrams());
    }

    @Test
    void two_sources_with_weight_lower_score_wins() {
        cache.add(taxonomy("MFN-1", 10, 1500));
        cache.add(taxonomy("MFN-1", 5, 1300));

        assertEquals(1300, cache.findByMfn("MFN-1").weightInGrams());
    }

    @Test
    void blank_mfn_is_noop() {
        cache.add(taxonomy("", 5, 1300));
        cache.add(taxonomy("   ", 5, 1300));

        assertEquals(0, cache.size());
    }

    @Test
    void concurrent_adds_do_not_drop_weight() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Taxonomy> inputs = IntStream.range(0, 200)
                .mapToObj(i -> taxonomyNamed("MFN-1", 5 + (i % 3), 1000 + (i % 5), "N" + i))
                .toList();

        for (Taxonomy t : inputs) pool.submit(() -> cache.add(t));
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        Taxonomy result = cache.findByMfn("MFN-1");
        assertEquals(1000, result.weightInGrams());
    }

    private static Taxonomy taxonomy(String mfn, int score, Integer weight) {
        return taxonomyNamed(mfn, score, weight, "Name");
    }

    private static Taxonomy taxonomyNamed(String mfn, int score, Integer weight, String name) {
        return new Taxonomy("1234567890123", mfn, "Brand", name,
                ProductCategory.Laptops, score, weight);
    }
}
