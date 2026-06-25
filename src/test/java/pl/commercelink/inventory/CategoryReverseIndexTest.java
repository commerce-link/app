package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.SignalCategoryResolver;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CategoryReverseIndexTest {

    private final TaxonomyCache cache = new TaxonomyCache(mock(TaxonomyRepository.class));
    private final CategoryReverseIndex index = new CategoryReverseIndex(new SignalCategoryResolver());

    private MatchedInventory group(String code) {
        return new MatchedInventory(new InventoryKey(Set.of(), Set.of(code)), cache, null);
    }

    @Test
    void indexesItemUnderItsEnumCategoryKey() {
        // given
        cache.add(new Taxonomy("E", "M1", "Intel", "i7", ProductCategory.CPU, 1));
        MatchedInventory item = group("M1");

        // when
        index.rebuild(List.of(item));

        // then
        assertThat(index.keysFor("CPU")).contains(item.getInventoryKey());
    }

    @Test
    void indexesNonEnumCategoryKey() {
        // given
        cache.add(new Taxonomy("E", "M1", "Acme", "Cable", ProductCategory.Other, 1, null, null, "Cables356k"));
        MatchedInventory item = group("M1");

        // when
        index.rebuild(List.of(item));

        // then
        assertThat(index.keysFor("Cables356k")).contains(item.getInventoryKey());
    }

    @Test
    void resolvesCategoryFromSignalsWhenKeyIsOther() {
        // given: enum Other, no explicit key, but a vendor-category signal that resolves to Case.
        cache.add(new Taxonomy("E", "M1", "Acme", "Big Tower", ProductCategory.Other, 1, null, null, null,
                List.of("VENDOR_CATEGORY:Obudowa")));
        MatchedInventory item = group("M1");

        // when
        index.rebuild(List.of(item));

        // then
        assertThat(index.keysFor("Case")).contains(item.getInventoryKey());
        assertThat(index.keysFor("Other")).doesNotContain(item.getInventoryKey());
    }

    @Test
    void rebuildReplacesPreviousIndexSoNewFeedsResubscribeAutomatically() {
        // given
        cache.add(new Taxonomy("E", "M1", "Intel", "i7", ProductCategory.CPU, 1));
        cache.add(new Taxonomy("E", "M2", "NVidia", "RTX", ProductCategory.GPU, 1));
        index.rebuild(List.of(group("M1")));

        // when: a fresh load cycle brings a different feed
        MatchedInventory gpu = group("M2");
        index.rebuild(List.of(gpu));

        // then: the index reflects only the latest cycle (old key gone, new key present)
        assertThat(index.keysFor("CPU")).isEmpty();
        assertThat(index.keysFor("GPU")).contains(gpu.getInventoryKey());
    }

    @Test
    void unknownCategoryReturnsEmptySet() {
        // when / then
        assertThat(index.keysFor("Nope")).isEmpty();
    }
}
