package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreInventoryProviderTest {

    private final StoreInventoryCache cache = mock(StoreInventoryCache.class);
    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final Inventory inventory = mock(Inventory.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final InventoryAutoDiscovery autoDiscovery = mock(InventoryAutoDiscovery.class);
    private final StoreFeedItemLoader storeFeedItemLoader = mock(StoreFeedItemLoader.class);
    private final ExchangeRates exchangeRates = mock(ExchangeRates.class);

    private final StoreInventoryProvider provider = new StoreInventoryProvider(
            cache, storesRepository, inventory, supplierRegistry, autoDiscovery, storeFeedItemLoader, exchangeRates);

    private InventoryItem item(String supplier) {
        return new InventoryItem("4711111111111", "MFN", 10.0, "PLN", 1, 1, supplier, true, true, false);
    }

    @Test
    void returnsCachedEntryWithoutBuilding() {
        StoreInventory cached = new StoreInventory(new LinkedList<>(), LocalDateTime.now());
        when(cache.get("store-1")).thenReturn(Optional.of(cached));

        assertSame(cached, provider.get("store-1"));
        verify(inventory, never()).globalItemsForSuppliers(any());
        verify(cache, never()).put(any(), any());
    }

    @Test
    void buildsFromGlobalAndOwnItemsThenStores() {
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.canUseGlobalSuppliers()).thenReturn(true);
        when(storeEntity.getGlobalSupplierNames()).thenReturn(List.of("Action"));
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of("Wortmann"));
        when(inventory.globalItemsForSuppliers(List.of("Action"))).thenReturn(List.of(item("Action")));
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        SupplierDescriptor descriptor = mock(SupplierDescriptor.class);
        when(supplierRegistry.getDescriptor("Wortmann")).thenReturn(Optional.of(descriptor));
        when(storeFeedItemLoader.load(eq("store-1"), eq(descriptor), any())).thenReturn(List.of(item("Wortmann")));
        MatchedInventory matched = mock(MatchedInventory.class);
        when(autoDiscovery.run(anyList())).thenReturn(List.of(matched));

        StoreInventory result = provider.get("store-1");

        assertEquals(List.of(matched), result.items());
        verify(cache).put(eq("store-1"), any(StoreInventory.class));
        verify(autoDiscovery).run(argThat(list -> list.size() == 2));
    }

    @Test
    void excludesGlobalItemsWhenStoreCannotUseGlobalSuppliers() {
        when(cache.get("store-1")).thenReturn(Optional.empty());
        Store storeEntity = mock(Store.class);
        when(storesRepository.findById("store-1")).thenReturn(storeEntity);
        when(storeEntity.canUseGlobalSuppliers()).thenReturn(false);
        when(storeEntity.getOwnSupplierNames()).thenReturn(List.of());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of("PLN", 1.0));
        when(autoDiscovery.run(anyList())).thenReturn(List.of());

        provider.get("store-1");

        verify(inventory, never()).globalItemsForSuppliers(any());
        verify(autoDiscovery).run(argThat(List::isEmpty));
    }

    @Test
    void invalidateAllDelegatesToCache() {
        provider.invalidateAll();

        verify(cache).invalidateAll();
    }

    @Test
    void buildsEmptyInventoryWhenStoreMissing() {
        when(cache.get("store-1")).thenReturn(Optional.empty());
        when(storesRepository.findById("store-1")).thenReturn(null);

        StoreInventory result = provider.get("store-1");

        assertEquals(0, result.items().size());
        verify(cache).put(eq("store-1"), any(StoreInventory.class));
    }
}
