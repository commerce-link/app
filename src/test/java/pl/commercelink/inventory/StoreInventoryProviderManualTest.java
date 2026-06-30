package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreInventoryProviderManualTest {

    @Mock StoreInventoryCache cache;
    @Mock StoresRepository storesRepository;
    @Mock SupplierProviderFactory supplierProviderFactory;
    @Mock InventoryAutoDiscovery autoDiscovery;
    @Mock StoreFeedItemLoader storeFeedItemLoader;
    @Mock ExchangeRates exchangeRates;
    @InjectMocks StoreInventoryProvider provider;

    @Captor ArgumentCaptor<SupplierProviderDescriptor> descriptorCaptor;

    @Test
    void loadsManualSupplierFeedIntoOwnIndex() {
        // given
        Store store = new Store();
        store.setStoreId("store-1");
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(
                new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL)));
        store.setFulfilmentConfiguration(config);

        when(cache.get("store-1")).thenReturn(java.util.Optional.empty());
        when(exchangeRates.getCurrentSellRates()).thenReturn(Map.of());
        when(autoDiscovery.run(any())).thenReturn(List.of());
        when(storeFeedItemLoader.load(eq("store-1"), any(SupplierProviderDescriptor.class), any()))
                .thenReturn(List.of());

        // when
        provider.ownIndex(store);

        // then
        verify(storeFeedItemLoader, times(1)).load(eq("store-1"), descriptorCaptor.capture(), any());
        assertInstanceOf(ManualSupplierDescriptor.class, descriptorCaptor.getValue());
        assertEquals("manual:H1", descriptorCaptor.getValue().name());
        verifyNoInteractions(supplierProviderFactory);
    }
}
