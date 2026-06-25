package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.CategoryReverseIndex;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.SignalCategoryResolver;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the signal carried at parse time survives the LIVE reconstruction chain
 * (deprioritized -> cache -> rebuild) and actually drives CategoryReverseIndex resolution.
 * This exercises the real path, not a Taxonomy handed straight to the index.
 */
class SignalLivePathTest {

    private static final String OBUDOWA_SIGNAL = SignalCategoryResolver.VENDOR_CATEGORY + "Obudowa";

    @Test
    void signalSurvivesDeprioritizeCacheRebuildAndDrivesReverseIndex() {
        // given: a source taxonomy as a parser would emit it (a signal, enum Other, no explicit key)
        TaxonomyCache cache = new TaxonomyCache(mock(TaxonomyRepository.class));
        CategoryReverseIndex index = new CategoryReverseIndex(new SignalCategoryResolver());
        Taxonomy source = new Taxonomy("E", "M1", "Acme", "Big Tower",
                ProductCategory.Other, 1, null, null, null, List.of(OBUDOWA_SIGNAL));

        // when: the loader deprioritises store feeds (CsvProductFeedLoader), then it lands in the cache and is indexed
        Taxonomy reconstructed = StoreFeedTaxonomy.deprioritized(source, 5);
        cache.add(reconstructed);
        MatchedInventory item = new MatchedInventory(new InventoryKey(Set.of(), Set.of("M1")), cache, null);
        index.rebuild(List.of(item));

        // then: the signal resolved to Case and the item is reachable under it
        assertThat(index.keysFor("Case")).contains(item.getInventoryKey());
    }

    @Test
    void deprioritizedPreservesSignals() {
        // given
        Taxonomy source = new Taxonomy("E", "M1", "B", "N",
                ProductCategory.Other, 1, null, null, null, List.of(OBUDOWA_SIGNAL));

        // when
        Taxonomy deprioritized = StoreFeedTaxonomy.deprioritized(source, 5);

        // then
        assertThat(deprioritized.signals()).containsExactly(OBUDOWA_SIGNAL);
    }

    @Test
    void dataCorrectionRunPreservesFeedSignals() {
        // given
        PimCatalog pimCatalog = mock(PimCatalog.class);
        BrandMapper brandMapper = mock(BrandMapper.class);
        when(brandMapper.unifyBrand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.empty());
        DataCorrection dataCorrection = new DataCorrection(pimCatalog, brandMapper);
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "Brand", "Name",
                ProductCategory.Other, 5, null, null, null, List.of(OBUDOWA_SIGNAL));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.signals()).containsExactly(OBUDOWA_SIGNAL);
    }
}
