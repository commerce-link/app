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
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
    }

    // Deserialized through the real Jackson ObjectMapper, the same path the scheduler message travels
    // in production, so the test also pins that the configured EventBridge input still parses.
    private static MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload payload() throws Exception {
        return new ObjectMapper().readValue("{\"marketplace\":\"" + MARKETPLACE + "\"}",
                MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload.class);
    }

    @Test
    void schedulerPayloadCarriesOnlyTheMarketplace() throws Exception {
        // given: exactly the input configured on the EventBridge schedule
        String input = "{\"marketplace\":\"Allegro\"}";

        // when
        MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload parsed = new ObjectMapper()
                .readValue(input, MarketplaceReturnsImportEventListener.MarketplaceReturnsImportPayload.class);

        // then
        assertEquals("Allegro", parsed.getMarketplace());
    }

    @Test
    void everyFetchedReturnIsImportedForEachConnectedStore() throws Exception {
        // given
        stubActiveStore();
        when(provider.returns()).thenReturn(Optional.of(returns));
        when(returns.fetchReturns()).thenReturn(List.of(aReturn));

        // when
        listener.handleMessage(payload());

        // then
        verify(marketplaceReturnImporter).importReturn(store, MARKETPLACE, aReturn);
    }

    @Test
    void marketplaceWithoutAReturnsApiIsSkipped() throws Exception {
        // given
        stubActiveStore();
        when(provider.returns()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(payload());

        // then
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void storesWithoutAnActiveIntegrationAreSkipped() throws Exception {
        // given
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(false);

        // when
        listener.handleMessage(payload());

        // then
        verifyNoInteractions(providerFactory);
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void theKillSwitchStopsTheImportBeforeAnyStoreIsRead() throws Exception {
        // given
        ReflectionTestUtils.setField(listener, "returnsEnabled", false);

        // when
        listener.handleMessage(payload());

        // then
        verifyNoInteractions(storesRepository);
        verify(returns, never()).fetchReturns();
    }
}
