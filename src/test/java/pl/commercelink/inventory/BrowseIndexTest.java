package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrowseIndexTest {

    @Mock private TaxonomyCache taxonomyCache;
    @Mock private SupplierRegistry supplierRegistry;

    @Test
    void entriesAreGroupedByCategoryId() {
        // given
        MatchedInventory gpu = group("5901000000001", "GPU-1", "Gigabyte RTX 4060", "Gigabyte", "11");
        MatchedInventory ssd = group("5901000000002", "SSD-1", "ADATA Legend 800", "ADATA", "12");

        // when
        BrowseIndex index = BrowseIndex.build(7, List.of(gpu, ssd));

        // then
        assertThat(index.version()).isEqualTo(7);
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.inCategories(Set.of("11"))).extracting(BrowseEntry::group).containsExactly(gpu);
        assertThat(index.inCategories(Set.of("11", "12"))).hasSize(2);
        assertThat(index.inCategories(Set.of("99"))).isEmpty();
    }

    @Test
    void blankCategoryIdGoesToUnassigned() {
        // given
        MatchedInventory noId = group("5901000000003", "X-1", "Kabel HDMI", "Goobay", " ");

        // when
        BrowseIndex index = BrowseIndex.build(1, List.of(noId));

        // then
        assertThat(index.inCategories(Set.of(BrowseIndex.UNASSIGNED))).extracting(BrowseEntry::group).containsExactly(noId);
    }

    @Test
    void textMatchesNameBrandEanAndMfnIgnoringCase() {
        // given
        BrowseEntry entry = BrowseIndex.build(1, List.of(group("5901000000004", "MX-3S", "MX Master 3S", "Logitech", "11")))
                .all().get(0);

        // when / then
        assertThat(entry.matchesText("master")).isTrue();
        assertThat(entry.matchesText("LOGI")).isTrue();
        assertThat(entry.matchesText("0000004")).isTrue();
        assertThat(entry.matchesText("mx-3s")).isTrue();
        assertThat(entry.matchesText("razer")).isFalse();
    }

    @Test
    void entryOfFindsTheEntryOfTheSameGroupInstance() {
        // given
        MatchedInventory gpu = group("5901000000001", "GPU-1", "Gigabyte RTX 4060", "Gigabyte", "11");
        BrowseIndex index = BrowseIndex.build(1, List.of(gpu));

        // when / then
        assertThat(index.entryOf(gpu)).isPresent();
        assertThat(index.entryOf(group("5901000000001", "GPU-1", "Gigabyte RTX 4060", "Gigabyte", "11"))).isEmpty();
    }

    private final Map<String, Taxonomy> taxonomies = new HashMap<>();

    @BeforeEach
    void answerTaxonomyByProductCode() {
        when(taxonomyCache.find(any())).thenAnswer(call -> {
            InventoryKey key = call.getArgument(0);
            return key.getProductCodes().stream().map(taxonomies::get).filter(Objects::nonNull).findFirst()
                    .orElse(Taxonomy.EMPTY);
        });
    }

    private MatchedInventory group(String ean, String mfn, String name, String brand, String categoryId) {
        InventoryKey key = new InventoryKey(ean, mfn);
        Taxonomy taxonomy = new Taxonomy(ean, mfn, brand, name, "Kategoria", 1, null, null, null, categoryId);
        key.getProductCodes().forEach(code -> taxonomies.put(code, taxonomy));
        return new MatchedInventory(key, List.of(new InventoryItem(ean, mfn, 100.0, "PLN", 5, 1, "AB")),
                taxonomyCache, supplierRegistry);
    }
}
