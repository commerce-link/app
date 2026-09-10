package pl.commercelink.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceOrdersImportEventListenerTest {

    private static final String MARKETPLACE = "Allegro";

    @Mock private StoresRepository storesRepository;
    @Mock private MarketplaceOrderImporter marketplaceOrderImporter;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private Store store;
    @Mock private MarketplaceProvider provider;

    @InjectMocks
    private MarketplaceOrdersImportEventListener listener;

    private void stubActiveStore() {
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
    }

    private void stubOrdersFetched() {
        when(provider.fetchOrders()).thenReturn(List.of(mock(MarketplaceOrder.class)));
    }

    private static MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload() throws Exception {
        return new ObjectMapper().readValue("{\"marketplace\":\"" + MARKETPLACE + "\"}",
                MarketplaceOrdersImportEventListener.MarketplaceOrderPayload.class);
    }

    @Test
    void everyFetchedOrderIsImportedAndTheStoreMarkerIsAdvanced() throws Exception {
        // given
        stubActiveStore();
        stubOrdersFetched();

        // when
        listener.handleMessage(payload());

        // then
        verify(marketplaceOrderImporter).importOrder(eq(store), eq(MARKETPLACE), any());
        verify(store).updateLastFetchedAt(MARKETPLACE);
    }

    @Test
    void aFailedImportDoesNotMarkTheStoreAsFetched() throws Exception {
        // given
        stubActiveStore();
        RuntimeException failure = new RuntimeException("marketplace API unavailable");
        when(provider.fetchOrders()).thenThrow(failure);
        MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload = payload();

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> listener.handleMessage(payload));

        // then
        assertSame(failure, thrown);
        verify(store, never()).updateLastFetchedAt(MARKETPLACE);
    }
}
