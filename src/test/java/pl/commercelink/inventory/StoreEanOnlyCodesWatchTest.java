package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.information.PimIndexRefreshed;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEanOnlyCodesWatchTest {

    private static final VariantCodes SHINE10 = PimEanOnlyCodes.ofStore("s1", List.of(flagged("SHINE10", "s1")));
    private static final VariantCodes SHINE10_AND_11 = PimEanOnlyCodes.ofStore("s1",
            List.of(flagged("SHINE10", "s1"), flagged("SHINE11", "s1")));
    private static final VariantCodes SHINE20 = PimEanOnlyCodes.ofStore("s2", List.of(flagged("SHINE20", "s2")));

    @Mock
    private StoreEanOnlyCodes storeEanOnlyCodes;
    @Mock
    private StoreInventoryCache storeInventoryCache;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PimCatalog pimCatalog;

    @Test
    void firstEventRemembersWithoutEvicting() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10);
        when(storeEanOnlyCodes.of("s2")).thenReturn(VariantCodes.none());

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(storeInventoryCache);
    }

    @Test
    void noChangeEvictsNothing() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10);
        when(storeEanOnlyCodes.of("s2")).thenReturn(VariantCodes.none());
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(storeInventoryCache);
    }

    @Test
    void changedStoreIsEvictedAlone() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10).thenReturn(SHINE10_AND_11);
        when(storeEanOnlyCodes.of("s2")).thenReturn(SHINE20);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(storeInventoryCache).evict("s1");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void removedFlagEvictsTheStore() {
        // given -- SHINE11 lost its flag
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10_AND_11).thenReturn(SHINE10);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(storeInventoryCache).evict("s1");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void deletedEntryEvictsTheStore() {
        // given -- the store's only flagged entry (or clone) was deleted, so its pairs are gone
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10).thenReturn(VariantCodes.none());
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(storeInventoryCache, times(1)).evict("s1");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void newStoreWithFlagIsEvicted() {
        // given -- s2 did not exist at the first event
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1")).thenReturn(List.of("s1", "s2"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(VariantCodes.none());
        when(storeEanOnlyCodes.of("s2")).thenReturn(SHINE20);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(storeInventoryCache).evict("s2");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void storeListFailureKeepsTheMapAndRetries() {
        // given
        when(storesRepository.findAllStoreIds())
                .thenReturn(List.of("s1"))
                .thenThrow(new IllegalStateException("DynamoDB down"))
                .thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10).thenReturn(SHINE10_AND_11);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then -- the failed event changed nothing, so the next one still sees SHINE10 as the previous pairs
        verify(storeInventoryCache, times(1)).evict("s1");
    }

    @Test
    void failedEvictIsRetriedOnTheNextEvent() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10).thenReturn(SHINE10_AND_11);
        when(storeEanOnlyCodes.of("s2")).thenReturn(VariantCodes.none()).thenReturn(SHINE20);
        doThrow(new IllegalStateException("redis down")).doNothing().when(storeInventoryCache).evict("s1");
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then -- s1 failed once and was retried once; s2 succeeded the first time and is not evicted again
        verify(storeInventoryCache, times(2)).evict("s1");
        verify(storeInventoryCache, times(1)).evict("s2");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void switchOffDoesNothing() {
        // given
        InventoryMatchingProperties storeOff = new InventoryMatchingProperties(true, true, true, true, true, false);
        InventoryMatchingProperties globalOff = new InventoryMatchingProperties(true, true, true, true, false, true);

        // when
        watch(storeOff).onPimIndexRefreshed(new PimIndexRefreshed());
        watch(globalOff).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(storeEanOnlyCodes, storeInventoryCache, storesRepository);
    }

    @Test
    void readyEventRecordsTheBaselineWithoutEvicting() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());

        // when
        watch.onApplicationReady();
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then -- the first refresh sees the same pairs as the baseline
        verifyNoInteractions(storeInventoryCache);
    }

    @Test
    void changeBetweenReadyAndFirstRefreshIsEvicted() {
        // given
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10).thenReturn(SHINE10_AND_11);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onApplicationReady();

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(storeInventoryCache).evict("s1");
        verifyNoMoreInteractions(storeInventoryCache);
    }

    @Test
    void failedBaselineOnReadyFallsBackToTheFirstRefresh() {
        // given
        when(storesRepository.findAllStoreIds())
                .thenThrow(new IllegalStateException("DynamoDB down"))
                .thenReturn(List.of("s1"));
        when(storeEanOnlyCodes.of("s1")).thenReturn(SHINE10);
        StoreEanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());

        // when
        watch.onApplicationReady();
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then -- the first refresh only recorded the baseline
        verifyNoInteractions(storeInventoryCache);
    }

    @Test
    void readyEventWithSwitchOffDoesNothing() {
        // given
        InventoryMatchingProperties storeOff = new InventoryMatchingProperties(true, true, true, true, true, false);
        InventoryMatchingProperties globalOff = new InventoryMatchingProperties(true, true, true, true, false, true);

        // when
        watch(storeOff).onApplicationReady();
        watch(globalOff).onApplicationReady();

        // then
        verifyNoInteractions(storeEanOnlyCodes, storeInventoryCache, storesRepository);
    }

    @Test
    void globalFlagChangeIsLeftToEanOnlyCodesWatch() {
        // given -- between two refreshes only the global entry gets its flag; the store has no entries of its own
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1"));
        when(pimCatalog.withStoreData("s1"))
                .thenReturn(indexOf(global(false)).forStore("s1"))
                .thenReturn(indexOf(global(true)).forStore("s1"));
        StoreEanOnlyCodes realCodes = new StoreEanOnlyCodes(pimCatalog, InventoryMatchingProperties.allEnabled());
        StoreEanOnlyCodesWatch watch = new StoreEanOnlyCodesWatch(realCodes, storeInventoryCache,
                InventoryMatchingProperties.allEnabled(), storesRepository);
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(storeInventoryCache);
    }

    private StoreEanOnlyCodesWatch watch(InventoryMatchingProperties properties) {
        return new StoreEanOnlyCodesWatch(storeEanOnlyCodes, storeInventoryCache, properties, storesRepository);
    }

    private static PimEntryIndex indexOf(PimEntry... entries) {
        return new PimEntryIndex(List.of(entries), UnaryOperator.identity(), UnaryOperator.identity());
    }

    private static PimEntry global(boolean eanOnly) {
        return new PimEntry("pim-SHINE10", List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, eanOnly);
    }

    private static PimEntry flagged(String mpn, String ownerStoreId) {
        return new PimEntry("clone-" + mpn, List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier(mpn, PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, ownerStoreId, "pim-" + mpn, true);
    }
}
