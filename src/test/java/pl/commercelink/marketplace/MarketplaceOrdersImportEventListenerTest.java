package pl.commercelink.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrdersImportEventListenerTest {

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private MarketplaceOrderImporter marketplaceOrderImporter;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private MarketplaceProvider provider;
    @Mock
    private MarketplaceOrder order;

    @InjectMocks
    private MarketplaceOrdersImportEventListener listener;

    private Store storeWithIntegration(String storeId, String marketplace, boolean loggedIn) {
        Store store = new Store();
        store.setStoreId(storeId);
        MarketplaceIntegration integration = new MarketplaceIntegration(marketplace);
        integration.setLoggedIn(loggedIn);
        store.getMarketplaces().add(integration);
        return store;
    }

    @Test
    void importsOnlyTheAddressedStoreWhenPayloadCarriesStoreId() {
        // given
        Store addressed = storeWithIntegration("store-1", "Allegro", true);
        Store other = storeWithIntegration("store-2", "Allegro", true);
        when(storesRepository.findById("store-1")).thenReturn(addressed);
        when(storesRepository.findAll()).thenReturn(List.of(addressed, other));
        when(providerFactory.get(addressed, "Allegro")).thenReturn(provider);
        when(provider.fetchOrders()).thenReturn(List.of(order));

        // when
        listener.handleMessage(new MarketplaceOrdersImportEventListener.MarketplaceOrderPayload("Allegro", "store-1"));

        // then
        verify(marketplaceOrderImporter).importOrder(addressed, "Allegro", order);
        verify(storesRepository).save(addressed);
        verify(storesRepository, never()).findAll();
        verify(providerFactory, never()).get(other, "Allegro");
    }

    @Test
    void skipsAddressedStoreWithoutActiveIntegration() {
        // given
        Store loggedOut = storeWithIntegration("store-1", "Allegro", false);
        when(storesRepository.findById("store-1")).thenReturn(loggedOut);

        // when
        listener.handleMessage(new MarketplaceOrdersImportEventListener.MarketplaceOrderPayload("Allegro", "store-1"));

        // then
        verify(providerFactory, never()).get(any(), anyString());
        verify(storesRepository, never()).save(any());
    }

    @Test
    void skipsUnknownAddressedStore() {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when
        listener.handleMessage(new MarketplaceOrdersImportEventListener.MarketplaceOrderPayload("Allegro", "missing"));

        // then
        verify(providerFactory, never()).get(any(), anyString());
    }

    @Test
    void importsForEveryActiveStoreWhenPayloadHasNoStoreId() {
        // given
        Store first = storeWithIntegration("store-1", "Allegro", true);
        Store loggedOut = storeWithIntegration("store-2", "Allegro", false);
        Store otherMarketplace = storeWithIntegration("store-3", "Empik", true);
        when(storesRepository.findAll()).thenReturn(List.of(first, loggedOut, otherMarketplace));
        when(providerFactory.get(first, "Allegro")).thenReturn(provider);
        when(provider.fetchOrders()).thenReturn(List.of(order));

        // when
        listener.handleMessage(new MarketplaceOrdersImportEventListener.MarketplaceOrderPayload("Allegro", null));

        // then
        verify(marketplaceOrderImporter).importOrder(first, "Allegro", order);
        verify(providerFactory, never()).get(loggedOut, "Allegro");
        verify(providerFactory, never()).get(otherMarketplace, "Allegro");
        verify(storesRepository, never()).findById(anyString());
    }

    @Test
    void schedulerPayloadWithoutStoreIdImportsEveryActiveStoreAndAdvancesTheMarker() throws Exception {
        // given
        Store store = storeWithIntegration("store-1", "Allegro", true);
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(providerFactory.get(store, "Allegro")).thenReturn(provider);
        when(provider.fetchOrders()).thenReturn(List.of(order));
        MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload = new ObjectMapper().readValue(
                "{\"marketplace\":\"Allegro\"}", MarketplaceOrdersImportEventListener.MarketplaceOrderPayload.class);

        // when
        listener.handleMessage(payload);

        // then
        verify(marketplaceOrderImporter).importOrder(store, "Allegro", order);
        verify(storesRepository).save(store);
        assertThat(store.getMarketplaceIntegration("Allegro").getLastFetchedAt()).isNotNull();
    }

    @Test
    void aFailedImportDoesNotMarkTheStoreAsFetched() {
        // given
        Store store = storeWithIntegration("store-1", "Allegro", true);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(providerFactory.get(store, "Allegro")).thenReturn(provider);
        RuntimeException failure = new RuntimeException("marketplace API unavailable");
        when(provider.fetchOrders()).thenThrow(failure);
        MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload =
                new MarketplaceOrdersImportEventListener.MarketplaceOrderPayload("Allegro", "store-1");

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.handleMessage(payload));

        // then
        assertSame(failure, thrown);
        assertThat(store.getMarketplaceIntegration("Allegro").getLastFetchedAt()).isNull();
        verify(storesRepository, never()).save(any());
    }
}
