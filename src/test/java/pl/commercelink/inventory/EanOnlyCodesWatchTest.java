package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.information.PimIndexRefreshed;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EanOnlyCodesWatchTest {

    private static final VariantCodes SHINE10 = PimEanOnlyCodes.of(List.of(flagged("SHINE10")));
    private static final VariantCodes SHINE10_AND_11 = PimEanOnlyCodes.of(List.of(flagged("SHINE10"), flagged("SHINE11")));

    @Mock
    private Inventory inventory;
    @Mock
    private StoreInventoryCache storeInventoryCache;
    @Mock
    private EanOnlyCodes eanOnlyCodes;
    @Mock
    private StoresRepository storesRepository;

    private final GlobalMatchedInventory globalInventory = new GlobalMatchedInventory();

    @Test
    void unchangedFlagsDoNotRegroup() {
        // given
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10);
        when(eanOnlyCodes.refresh()).thenReturn(PimEanOnlyCodes.of(List.of(flagged("SHINE10"))));

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(inventory, storeInventoryCache, storesRepository);
        verify(storeInventoryCache, never()).evict(any());
    }

    @Test
    void changedFlagsRegroupOnceThenEvictEveryStore() {
        // given
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10);
        when(eanOnlyCodes.refresh()).thenReturn(SHINE10_AND_11);
        regroupsWith(SHINE10_AND_11);
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        InOrder order = inOrder(inventory, storeInventoryCache);
        order.verify(inventory).regroup();
        order.verify(storeInventoryCache).evict("s1");
        order.verify(storeInventoryCache).evict("s2");
        order.verifyNoMoreInteractions();
    }

    @Test
    void flagTurnedOnAndOffBetweenRefreshesDoesNothing() {
        // given -- the flag was set and removed again in PIM between two refreshes of this instance
        globalInventory.replace(List.of(), VariantCodes.none(), VariantCodes.none());
        when(eanOnlyCodes.refresh()).thenReturn(PimEanOnlyCodes.of(List.of()));

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(inventory, storeInventoryCache);
    }

    @Test
    void nothingHappensBeforeTheFirstGlobalLoad() {
        // given
        when(eanOnlyCodes.refresh()).thenReturn(SHINE10);

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(inventory, storeInventoryCache);
    }

    @Test
    void switchOffDoesNothing() {
        // given
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10);
        when(eanOnlyCodes.refresh()).thenReturn(VariantCodes.none());

        // when
        watch(new InventoryMatchingProperties(true, true, true, true, false, true)).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verifyNoInteractions(inventory, storeInventoryCache);
    }

    @Test
    void oneFailingEvictDoesNotStopTheOthers() {
        // given
        globalInventory.replace(List.of(), VariantCodes.none(), VariantCodes.none());
        when(eanOnlyCodes.refresh()).thenReturn(SHINE10);
        regroupsWith(SHINE10);
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2", "s3"));
        doThrow(new IllegalStateException("redis down")).when(storeInventoryCache).evict("s2");

        // when
        watch(InventoryMatchingProperties.allEnabled()).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(inventory).regroup();
        verify(storeInventoryCache).evict("s1");
        verify(storeInventoryCache).evict("s3");
    }

    @Test
    void evictsWhenAFeedReloadAlreadyRegroupedWithNewFlags() {
        // given -- the first event only observes the pool; a feed reload then groups with the newly flagged pair
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10);
        when(eanOnlyCodes.refresh()).thenReturn(SHINE10).thenReturn(SHINE10_AND_11);
        when(storesRepository.findAllStoreIds()).thenReturn(List.of("s1", "s2"));
        EanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10_AND_11);

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(inventory, never()).regroup();
        verify(storeInventoryCache, times(1)).evict("s1");
        verify(storeInventoryCache, times(1)).evict("s2");
    }

    @Test
    void retriesEvictionWhenTheStoreLookupFailed() {
        // given
        globalInventory.replace(List.of(), VariantCodes.none(), SHINE10);
        when(eanOnlyCodes.refresh()).thenReturn(SHINE10_AND_11);
        regroupsWith(SHINE10_AND_11);
        when(storesRepository.findAllStoreIds())
                .thenThrow(new IllegalStateException("DynamoDB down"))
                .thenReturn(List.of("s1"));
        EanOnlyCodesWatch watch = watch(InventoryMatchingProperties.allEnabled());
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // when
        watch.onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        verify(inventory, times(1)).regroup();
        verify(storeInventoryCache, times(1)).evict("s1");
    }

    @Test
    void storeFlagDoesNotRegroupTheGlobalPool() {
        // given: the index holds the unflagged global Mova entry and a store's flagged clone of it
        PimEntry global = new PimEntry("pim-SHINE10", List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null);
        PimEntry flaggedClone = new PimEntry("clone-SHINE10", List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, "s1", "pim-SHINE10", true);
        EanOnlyCodes realCodes = new EanOnlyCodes(ScopedPimCatalog.of(global, flaggedClone),
                InventoryMatchingProperties.allEnabled());
        globalInventory.replace(List.of(), VariantCodes.none(), VariantCodes.none());
        long version = globalInventory.version();

        // when
        new EanOnlyCodesWatch(realCodes, globalInventory, inventory, storeInventoryCache,
                InventoryMatchingProperties.allEnabled(), storesRepository).onPimIndexRefreshed(new PimIndexRefreshed());

        // then
        assertThat(realCodes.current().size()).isZero();
        assertThat(globalInventory.version()).isEqualTo(version);
        verifyNoInteractions(inventory, storeInventoryCache, storesRepository);
    }

    // the mocked regroup does what Inventory.regroup does to the pool: groups again with the given pairs
    private void regroupsWith(VariantCodes pairs) {
        doAnswer(invocation -> {
            globalInventory.replace(List.of(), VariantCodes.none(), pairs);
            return null;
        }).when(inventory).regroup();
    }

    private EanOnlyCodesWatch watch(InventoryMatchingProperties properties) {
        return new EanOnlyCodesWatch(eanOnlyCodes, globalInventory, inventory, storeInventoryCache, properties,
                storesRepository);
    }

    private static PimEntry flagged(String mpn) {
        return new PimEntry("pim-" + mpn, List.of(
                new PimIdentifier("5901234567801", PimIdentifierType.GTIN),
                new PimIdentifier(mpn, PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true);
    }
}
