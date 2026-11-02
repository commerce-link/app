package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchListenerTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreActivity storeActivity;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private MarketplaceProvider provider;
    @Mock
    private MarketplaceOffers offers;
    @Mock
    private MarketplaceOfferMatching matching;
    @Mock
    private OfferMatchResultStore resultStore;

    @InjectMocks
    private MarketplaceOfferMatchListener listener;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE);
        MarketplaceIntegration integration = new MarketplaceIntegration(EMPIK);
        integration.setLoggedIn(true);
        store.getMarketplaces().add(integration);
        when(storesRepository.findById(STORE)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(true);
        when(providerFactory.get(store, EMPIK)).thenReturn(provider);
        when(provider.offers()).thenReturn(Optional.of(offers));
    }

    private static String message() {
        return new MarketplaceOfferMatchMessage(STORE, EMPIK).toJson();
    }

    @Test
    void savesResultAfterMatching() {
        // given
        OfferMatchResult result = new OfferMatchResult(Instant.parse("2026-11-02T10:00:00Z"), 1,
                List.of(), List.of(), List.of(), List.of(), 1, 0);
        when(matching.match(store, EMPIK, offers)).thenReturn(result);

        // when
        listener.handleMessage(message());

        // then
        verify(resultStore).save(STORE, EMPIK, result);
    }

    @Test
    void skipsInactiveStore() {
        // given
        when(storeActivity.isActive(store)).thenReturn(false);

        // when
        listener.handleMessage(message());

        // then
        verifyNoInteractions(matching, resultStore, providerFactory);
    }

    @Test
    void skipsMarketplaceWithoutOfferListing() {
        // given
        when(provider.offers()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(message());

        // then
        verifyNoInteractions(matching, resultStore);
    }

    @Test
    void skipsStoreWithoutTheMarketplaceConnected() {
        // given
        store.getMarketplaces().clear();

        // when
        listener.handleMessage(message());

        // then
        verifyNoInteractions(matching, resultStore, providerFactory);
    }

    @Test
    void skipsMarketplaceWithoutProvider() {
        // given
        when(providerFactory.get(store, EMPIK)).thenReturn(null);

        // when
        listener.handleMessage(message());

        // then
        verifyNoInteractions(matching, resultStore);
    }

    @Test
    void dropsUnreadableMessage() {
        // when
        listener.handleMessage("not json");

        // then
        verifyNoInteractions(matching, resultStore, storesRepository);
    }

    @Test
    void rethrowsAFailedRunSoTheMessageIsRetried() {
        // given
        when(matching.match(store, EMPIK, offers)).thenThrow(new IllegalStateException("OF21 failed"));

        // when / then
        assertThatThrownBy(() -> listener.handleMessage(message())).isInstanceOf(IllegalStateException.class);
        verify(resultStore, never()).save(any(), any(), any());
    }
}
