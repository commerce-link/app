package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden surface: 3 - Coupling findAllByProductCategory <-> TaxonomyCache.find.
 *
 * Freezes the first-seen membership contract of
 * InventoryView.findAllByProductCategory: the set returned for a category is
 * exactly the listed keys whose taxonomyCache.find(key).category() == category.
 * Because find uses a STRICT `<` tie-break starting from Taxonomy.EMPTY
 * (Integer.MAX_VALUE / ProductCategory.Other), a key carrying two product codes
 * resolves to the category of the LOWEST-score taxonomy and therefore belongs to
 * that category only, never to the loser of the tie-break.
 */
@ExtendWith(MockitoExtension.class)
class GoldenInventoryViewListingsTest {

    private static final String CPU_MFN = "CPU-MFN";
    private static final String GPU_MFN = "GPU-MFN";
    private static final String TIE_LOW_CPU_MFN = "TIE-CPU";
    private static final String TIE_HIGH_GPU_MFN = "TIE-GPU";

    @Mock
    private TaxonomyRepository taxonomyRepository;
    @Mock
    private SupplierRegistry supplierRegistry;

    private TaxonomyCache seededCache() {
        // Real TaxonomyCache: constructor only stores the repository; onStartUp()
        // is never called, so the mock repository is never touched. Seed via add(...).
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.add(new Taxonomy("E-CPU", CPU_MFN, "Intel", "i7", ProductCategory.CPU, 1));
        cache.add(new Taxonomy("E-GPU", GPU_MFN, "NVidia", "RTX", ProductCategory.GPU, 1));
        // Tie-break key carries both codes; CPU has the LOWER score so strict `<` keeps it.
        cache.add(new Taxonomy("E-TIE-CPU", TIE_LOW_CPU_MFN, "Intel", "i9", ProductCategory.CPU, 2));
        cache.add(new Taxonomy("E-TIE-GPU", TIE_HIGH_GPU_MFN, "NVidia", "RTX2", ProductCategory.GPU, 9));
        return cache;
    }

    private MatchedInventory groupWithCodes(TaxonomyCache cache, Set<String> codes) {
        return new MatchedInventory(new InventoryKey(Set.of(), codes), cache, supplierRegistry);
    }

    private InventoryView viewOver(TaxonomyCache cache, List<MatchedInventory> globalGroups) {
        InventoryIndex globalIndex = InventoryIndex.of(globalGroups);
        InventoryIndex ownIndex = InventoryIndex.of(List.of());
        // Package-visible ctor; zero InventorySources -> assemble() merges nothing,
        // so the returned key keeps exactly the listed codes.
        return new InventoryView(globalIndex, ownIndex, cache, supplierRegistry);
    }

    private Set<Set<String>> codeSetsOf(Collection<MatchedInventory> results) {
        return results.stream()
                .map(m -> Set.copyOf(m.getInventoryKey().getProductCodes()))
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void findAllByProductCategoryReturnsExactlyKeysResolvingToThatCategory() {
        // given
        TaxonomyCache cache = seededCache();
        MatchedInventory cpuGroup = groupWithCodes(cache, Set.of(CPU_MFN));
        MatchedInventory gpuGroup = groupWithCodes(cache, Set.of(GPU_MFN));
        InventoryView view = viewOver(cache, List.of(cpuGroup, gpuGroup));

        // when
        Collection<MatchedInventory> cpuResult = view.findAllByProductCategory(ProductCategory.CPU);
        Collection<MatchedInventory> gpuResult = view.findAllByProductCategory(ProductCategory.GPU);

        // then
        assertThat(codeSetsOf(cpuResult)).containsExactly(Set.of(CPU_MFN));
        assertThat(codeSetsOf(gpuResult)).containsExactly(Set.of(GPU_MFN));
    }

    @Test
    void tieBreakKeyBelongsToLowestScoreCategoryAndNotTheLoser() {
        // given
        TaxonomyCache cache = seededCache();
        MatchedInventory cpuGroup = groupWithCodes(cache, Set.of(CPU_MFN));
        // This group's key carries BOTH tie codes: CPU score=2 beats GPU score=9 via strict `<`.
        MatchedInventory tieGroup = groupWithCodes(cache, Set.of(TIE_LOW_CPU_MFN, TIE_HIGH_GPU_MFN));
        InventoryView view = viewOver(cache, List.of(cpuGroup, tieGroup));

        // sanity: find resolves the tie key to CPU (the lower score), confirming the tie-break.
        Taxonomy resolved = cache.find(tieGroup.getInventoryKey());
        assertThat(resolved.category()).isEqualTo(ProductCategory.CPU);

        // when
        Collection<MatchedInventory> cpuResult = view.findAllByProductCategory(ProductCategory.CPU);
        Collection<MatchedInventory> gpuResult = view.findAllByProductCategory(ProductCategory.GPU);

        // then
        // The tie key joins CPU (its find-winner) alongside the plain CPU group...
        assertThat(codeSetsOf(cpuResult))
                .containsExactlyInAnyOrder(Set.of(CPU_MFN), Set.of(TIE_LOW_CPU_MFN, TIE_HIGH_GPU_MFN));
        // ...and is ABSENT from GPU even though it carries a GPU code, because find's
        // strict `<` tie-break resolved it to CPU.
        assertThat(gpuResult).isEmpty();
    }

    @Test
    void keyWithUnknownCodesResolvesToOtherViaEmptyAndIsAbsentFromKnownCategories() {
        // given
        TaxonomyCache cache = seededCache();
        MatchedInventory cpuGroup = groupWithCodes(cache, Set.of(CPU_MFN));
        // No taxonomy seeded for this code -> find never replaces EMPTY -> category() == Other.
        MatchedInventory unknownGroup = groupWithCodes(cache, Set.of("UNSEEDED-MFN"));
        InventoryView view = viewOver(cache, List.of(cpuGroup, unknownGroup));

        // sanity: the unknown key resolves to the EMPTY fallback (Other).
        Taxonomy resolved = cache.find(unknownGroup.getInventoryKey());
        assertThat(resolved.category()).isEqualTo(ProductCategory.Other);

        // when
        Collection<MatchedInventory> cpuResult = view.findAllByProductCategory(ProductCategory.CPU);
        Collection<MatchedInventory> otherResult = view.findAllByProductCategory(ProductCategory.Other);

        // then
        assertThat(codeSetsOf(cpuResult)).containsExactly(Set.of(CPU_MFN));
        assertThat(codeSetsOf(otherResult)).containsExactly(Set.of("UNSEEDED-MFN"));
    }
}
