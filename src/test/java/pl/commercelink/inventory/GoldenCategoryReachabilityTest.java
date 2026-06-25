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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden surface (F3): reachability of categoryKey through findAllByProductCategory after the
 * read-path retype enum -> String.
 *
 * Invariant: every taxonomy that is isProcessable() must be reachable via
 * findAllByProductCategory(taxonomy.categoryKey()). Behavior for enum keys is PRESERVED (the
 * String key == enum.name()); the ONLY new reach is the additive Other -> resolved case: a
 * non-enum key (e.g. "Cables356k") that carries enum Other was previously unreachable when the
 * read-path keyed on the enum, and is now reachable by its String key.
 */
@ExtendWith(MockitoExtension.class)
class GoldenCategoryReachabilityTest {

    @Mock
    private TaxonomyRepository taxonomyRepository;
    @Mock
    private SupplierRegistry supplierRegistry;

    private static final String CPU_MFN = "CPU-MFN";
    private static final String GPU_MFN = "GPU-MFN";
    private static final String CABLE_MFN = "CAB-MFN";
    private static final String OTHER_MFN = "OTH-MFN";

    private TaxonomyCache seededCache() {
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.add(new Taxonomy("E-CPU", CPU_MFN, "Intel", "i7", ProductCategory.CPU, 1));
        cache.add(new Taxonomy("E-GPU", GPU_MFN, "NVidia", "RTX", ProductCategory.GPU, 1));
        // non-enum, isProcessable key: enum is Other but categoryKey is a free string.
        cache.add(new Taxonomy("E-CAB", CABLE_MFN, "Acme", "Cable", ProductCategory.Other, 1, null, null, "Cables356k"));
        // Other / not processable: stays "Other".
        cache.add(new Taxonomy("E-OTH", OTHER_MFN, "B", "N", ProductCategory.Other, 1));
        return cache;
    }

    private MatchedInventory groupWithCode(TaxonomyCache cache, String code) {
        return new MatchedInventory(new InventoryKey(Set.of(), Set.of(code)), cache, supplierRegistry);
    }

    private InventoryView viewOverAll(TaxonomyCache cache) {
        List<MatchedInventory> groups = List.of(
                groupWithCode(cache, CPU_MFN),
                groupWithCode(cache, GPU_MFN),
                groupWithCode(cache, CABLE_MFN),
                groupWithCode(cache, OTHER_MFN));
        return new InventoryView(InventoryIndex.of(groups), InventoryIndex.of(List.of()), cache, supplierRegistry);
    }

    private Set<Set<String>> codeSetsOf(Collection<MatchedInventory> results) {
        return results.stream()
                .map(m -> Set.copyOf(m.getInventoryKey().getProductCodes()))
                .collect(Collectors.toSet());
    }

    @Test
    void everyProcessableKeyIsReachableByItsStringKey() {
        // given
        TaxonomyCache cache = seededCache();
        InventoryView view = viewOverAll(cache);

        // when / then
        for (Taxonomy taxonomy : cache.getTaxonomies()) {
            if (!taxonomy.isProcessable()) {
                continue;
            }
            Collection<MatchedInventory> reached = view.findAllByProductCategory(taxonomy.categoryKey());
            assertThat(codeSetsOf(reached))
                    .as("isProcessable key '%s' must be reachable", taxonomy.categoryKey())
                    .contains(Set.of(taxonomy.mfn()));
        }
    }

    @Test
    void nonEnumKeyIsReachableWhileEnumBehaviorIsPreserved() {
        // given
        TaxonomyCache cache = seededCache();
        InventoryView view = viewOverAll(cache);

        // when / then
        // additive win: the non-enum key reaches its group (was unreachable on the enum read-path).
        assertThat(codeSetsOf(view.findAllByProductCategory("Cables356k")))
                .containsExactly(Set.of(CABLE_MFN));
        // preserved: enum keys reach exactly their own groups, by their String name.
        assertThat(codeSetsOf(view.findAllByProductCategory("CPU")))
                .containsExactly(Set.of(CPU_MFN));
        assertThat(codeSetsOf(view.findAllByProductCategory("GPU")))
                .containsExactly(Set.of(GPU_MFN));
    }
}
