package pl.commercelink.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceReturnsImportEventListenerTest {

    private static final String MARKETPLACE = "Allegro";
    private static final String STORE_ID = "store-1";

    @Mock private StoresRepository storesRepository;
    @Mock private MarketplaceReturnImporter marketplaceReturnImporter;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private Store store;
    @Mock private MarketplaceProvider provider;
    @Mock private MarketplaceReturns returns;

    @InjectMocks
    private MarketplaceReturnsImportEventListener listener;

    private final MarketplaceReturn aReturn = new MarketplaceReturn("r-1", "cf-1", null,
            MarketplaceReturnStatus.DECLARED, LocalDateTime.now(), List.of(), List.of());

    private void stubActiveStore() {
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(store.getStoreId()).thenReturn(STORE_ID);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
    }

    private static MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload payload(String json) throws Exception {
        return new ObjectMapper().readValue(json, MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload.class);
    }

    private static MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload addressedPayload() throws Exception {
        return payload("{\"marketplace\":\"" + MARKETPLACE + "\",\"storeId\":\"" + STORE_ID + "\"}");
    }

    @Test
    void schedulerPayloadCarriesTheMarketplaceAndTheStore() throws Exception {
        // when
        MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload parsed =
                payload("{\"marketplace\":\"Allegro\",\"storeId\":\"store-1\"}");

        // then
        assertEquals("Allegro", parsed.getMarketplace());
        assertEquals("store-1", parsed.getStoreId());
    }

    @Test
    void everyFetchedReturnIsImportedForTheAddressedStore() throws Exception {
        // given
        stubActiveStore();
        when(provider.returns()).thenReturn(Optional.of(returns));
        when(returns.fetchReturns()).thenReturn(List.of(aReturn));

        // when
        listener.handleMessage(addressedPayload());

        // then
        verify(marketplaceReturnImporter).importReturn(store, MARKETPLACE, aReturn);
        verify(storesRepository, never()).findAll();
    }

    @Test
    void marketplaceWithoutAReturnsApiIsSkipped() throws Exception {
        // given
        stubActiveStore();
        when(provider.returns()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(addressedPayload());

        // then
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void aStoreWithoutAnActiveIntegrationIsSkipped() throws Exception {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(false);

        // when
        listener.handleMessage(addressedPayload());

        // then
        verifyNoInteractions(providerFactory);
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void anUnknownStoreIsSkipped() throws Exception {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when
        listener.handleMessage(payload("{\"marketplace\":\"Allegro\",\"storeId\":\"missing\"}"));

        // then
        verifyNoInteractions(providerFactory);
    }

    @Test
    void aMessageWithoutAStoreIsRejectedInsteadOfImportingEveryStore() throws Exception {
        // when
        listener.handleMessage(payload("{\"marketplace\":\"Allegro\"}"));

        // then
        verifyNoInteractions(storesRepository);
        verifyNoInteractions(providerFactory);
    }

    @Test
    void theKillSwitchStopsTheImportBeforeAnyStoreIsRead() throws Exception {
        // given
        ReflectionTestUtils.setField(listener, "returnsEnabled", false);

        // when
        listener.handleMessage(addressedPayload());

        // then
        verifyNoInteractions(storesRepository);
        verify(returns, never()).fetchReturns();
    }
}
