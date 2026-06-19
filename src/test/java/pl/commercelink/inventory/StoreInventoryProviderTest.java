package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreInventoryProviderTest {

    @Mock
    private StoreInventoryCache cache;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private InventoryAutoDiscovery autoDiscovery;
    @Mock
    private StoreFeedItemLoader storeFeedItemLoader;
    @Mock
    private ExchangeRates exchangeRates;
    @Mock
    private TaxonomyCache taxonomyCache;

    @InjectMocks
    private StoreInventoryProvider provider;

    private InventoryItem item(String supplier) {
        return new InventoryItem("4711111111111", "MFN", 10.0, "PLN", 1, 1, supplier, true, true, false);
    }

    @Test
    void returnsCachedEntryWithoutBuilding() {
        // given
        StoreInventory cached = new StoreInventory(new LinkedList<>(), LocalDateTime.now());
        when(cache.get("store-1")).thenReturn(Optional.of(cached));

        // when / then
        assertSame(cached, provider.get("store-1"));
        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    void buildsFromOwnItemsThenStores() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of("Wortmann"));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        when(supplierRegistry.getDescriptor("Wortmann")).thenReturn(Optional.of(descriptor));
        when(storeFeedItemLoader.load(eq("store-1"), eq(descriptor), any())).thenReturn(List.of(item("Wortmann")));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(autoDiscovery.run(anyList())).thenReturn(List.of(matched));

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertEquals(List.of(matched), result.items());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class));
        verify(autoDiscovery).run(argThat(list -> list.size() == 1));
    }

    @Test
    void buildsWithEmptyListWhenNoOwnSuppliers() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        when(autoDiscovery.run(anyList())).thenReturn(List.of());

        // when
        provider.get("store-1");

        // then
        verify(autoDiscovery).run(argThat(List::isEmpty));
    }

    @Test
    void usesStoreTtlWhenConfigured() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.of(30));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.run(anyList())).thenReturn(List.of());

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    void usesDefaultTtlWhenStoreHasNone() {
        // given
        provider.defaultTtlMinutes = 60;
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.run(anyList())).thenReturn(List.of());

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)));
    }

    @Test
    void usesDefaultTtlWhenStoreTtlIsZeroOrNegative() {
        // given
        provider.defaultTtlMinutes = 60;
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(storeEntity.getInventoryCacheTtlMinutes()).thenReturn(Optional.of(0));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.run(anyList())).thenReturn(List.of());

        // when
        provider.get("store-1");

        // then
        verify(cache).put(eq("store-1"), any(StoreInventory.class), eq(Duration.ofMinutes(60)));
    }

    @Test
    void buildsEmptyInventoryWhenStoreMissing() {
        // given
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when
        StoreInventory result = provider.get("store-1");

        // then
        assertEquals(0, result.items().size());
        verify(cache).put(eq("store-1"), any(StoreInventory.class), any(Duration.class));
    }

    @Test
    void buildLoadsOnlyOwnSuppliersAndIgnoresGlobal() {
        // given
        Store store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("Asbis"));
        when(store.getOwnSupplierNames()).thenReturn(List.of("Action"));
        when(store.getInventoryCacheTtlMinutes()).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        when(supplierRegistry.getDescriptor("Action")).thenReturn(Optional.of(descriptor));
        InventoryItem ownItem = new InventoryItem("111", "AAA", 100.0, "PLN", 5, 2, "Action", true, true, false);
        when(storeFeedItemLoader.load(eq("store-1"), eq(descriptor), anyMap())).thenReturn(List.of(ownItem));
        when(autoDiscovery.run(anyList())).thenAnswer(inv -> {
            List<InventoryItem> items = inv.getArgument(0);
            return List.of(new MatchedInventory(new InventoryKey("111", "AAA"), items, taxonomyCache, supplierRegistry));
        });

        // when
        StoreInventory result = provider.get("store-1");

        // then
        List<String> loadedSuppliers = result.items().stream()
                .flatMap(m -> m.getSuppliers().stream())
                .toList();
        assertThat(loadedSuppliers).containsExactly("Action");
    }
}
