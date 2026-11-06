package pl.commercelink.marketplace.offermatching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchSweepTest {

    private static final Instant NOW = Instant.parse("2026-11-04T21:00:00Z");
    private static final Duration DELAY = Duration.ofSeconds(321);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private StoreActivity storeActivity;
    @Mock
    private MarketplaceProviderFactory providerFactory;
    @Mock
    private OfferMatchResultStore resultStore;
    @Mock
    private MarketplaceOfferMatchPublisher publisher;
    @Mock
    private MarketplaceProviderDescriptor listingMarketplace;
    @Mock
    private MarketplaceProviderDescriptor otherListingMarketplace;
    @Mock
    private MarketplaceProviderDescriptor marketplaceWithoutListing;

    private MarketplaceOfferMatchSweep sweep;

    @BeforeEach
    void setUp() {
        sweep = new MarketplaceOfferMatchSweep(storesRepository, storeActivity, providerFactory, resultStore, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), fixedRandom(DELAY.toSeconds()));
        when(listingMarketplace.supportsOfferListing()).thenReturn(true);
        when(otherListingMarketplace.supportsOfferListing()).thenReturn(true);
        when(marketplaceWithoutListing.supportsOfferListing()).thenReturn(false);
        when(providerFactory.getDescriptor("MarketA")).thenReturn(listingMarketplace);
        when(providerFactory.getDescriptor("MarketB")).thenReturn(otherListingMarketplace);
        when(providerFactory.getDescriptor("NoListing")).thenReturn(marketplaceWithoutListing);
        when(storeActivity.isActive(any(Store.class))).thenReturn(true);
        when(resultStore.progress(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void ordersMatchingForEveryScheduledConnectionOfEveryMarketplaceThatListsOffers() {
        // given
        givenStores(store("s1", scheduled("MarketA"), scheduled("MarketB")), store("s2", scheduled("MarketB")));

        // when
        sweep.sweep();

        // then
        verify(publisher).order("s1", "MarketA", DELAY);
        verify(publisher).order("s1", "MarketB", DELAY);
        verify(publisher).order("s2", "MarketB", DELAY);
    }

    @Test
    void skipsAMarketplaceThatCannotListOffers() {
        // given
        givenStores(store("s1", scheduled("NoListing"), scheduled("MarketA")));

        // when
        sweep.sweep();

        // then
        verify(publisher, never()).order(eq("s1"), eq("NoListing"), any());
        verify(publisher).order("s1", "MarketA", DELAY);
    }

    @Test
    void skipsAMarketplaceWhoseAdapterIsNotInstalled() {
        // given
        givenStores(store("s1", scheduled("Uninstalled")));

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(publisher);
    }

    @Test
    void skipsAConnectionWithDailyMatchingSwitchedOff() {
        // given
        MarketplaceIntegration switchedOff = scheduled("MarketA");
        switchedOff.setOfferMatchingScheduled(false);
        givenStores(store("s1", switchedOff, new MarketplaceIntegration("MarketB")));

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(publisher);
    }

    @Test
    void skipsAnInactiveStore() {
        // given
        Store inactive = store("s1", scheduled("MarketA"));
        givenStores(inactive, store("s2", scheduled("MarketA")));
        when(storeActivity.isActive(inactive)).thenReturn(false);

        // when
        sweep.sweep();

        // then
        verify(publisher, never()).order(eq("s1"), anyString(), any());
        verify(publisher).order("s2", "MarketA", DELAY);
    }

    @Test
    void skipsAConnectionThatLostItsAuthorization() {
        // given
        MarketplaceIntegration lost = scheduled("MarketA");
        lost.setLoggedIn(false);
        givenStores(store("s1", lost, scheduled("MarketB")));

        // when
        sweep.sweep();

        // then
        verify(publisher, never()).order(eq("s1"), eq("MarketA"), any());
        verify(publisher).order("s1", "MarketB", DELAY);
    }

    @Test
    void skipsAConnectionWhoseRunIsStillGoingOn() {
        // given
        givenStores(store("s1", scheduled("MarketA"), scheduled("MarketB")));
        when(resultStore.progress("s1", "MarketA"))
                .thenReturn(Optional.of(OfferMatchProgress.start("run-1", NOW.minus(Duration.ofMinutes(5)))));

        // when
        sweep.sweep();

        // then
        verify(publisher, never()).order(eq("s1"), eq("MarketA"), any());
        verify(publisher).order("s1", "MarketB", DELAY);
    }

    @Test
    void ordersAgainWhereTheLastRunWentStaleOrFailed() {
        // given
        givenStores(store("s1", scheduled("MarketA"), scheduled("MarketB")));
        when(resultStore.progress("s1", "MarketA")).thenReturn(Optional.of(OfferMatchProgress.start("run-1",
                NOW.minus(OfferMatchProgress.STALE_AFTER).minusSeconds(1))));
        when(resultStore.progress("s1", "MarketB")).thenReturn(Optional.of(OfferMatchProgress.start("run-2", NOW)
                .failed(NOW, OfferMatchError.INTERNAL)));

        // when
        sweep.sweep();

        // then
        verify(publisher).order("s1", "MarketA", DELAY);
        verify(publisher).order("s1", "MarketB", DELAY);
    }

    @Test
    void aFailingStoreDoesNotStopTheOthers() {
        // given
        Store broken = store("s1", scheduled("MarketA"));
        givenStores(broken, store("s2", scheduled("MarketA")));
        when(storeActivity.isActive(broken)).thenThrow(new IllegalStateException("boom"));

        // when
        sweep.sweep();

        // then
        verify(publisher).order("s2", "MarketA", DELAY);
    }

    @Test
    void spreadsTheStartsOverAtMostFifteenMinutes() {
        // given
        RecordingRandom random = new RecordingRandom();
        sweep = new MarketplaceOfferMatchSweep(storesRepository, storeActivity, providerFactory, resultStore, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), random);
        givenStores(store("s1", scheduled("MarketA")));

        // when
        sweep.sweep();

        // then
        ArgumentCaptor<Duration> delay = ArgumentCaptor.forClass(Duration.class);
        verify(publisher).order(eq("s1"), eq("MarketA"), delay.capture());
        assertThat(random.bound).isEqualTo(901);
        assertThat(delay.getValue()).isEqualTo(Duration.ofSeconds(900));
    }

    @Test
    void everyDelayDrawnByARealGeneratorFitsInFifteenMinutes() {
        // given
        sweep = new MarketplaceOfferMatchSweep(storesRepository, storeActivity, providerFactory, resultStore, publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), new java.util.Random(7));
        Store[] stores = new Store[200];
        for (int i = 0; i < stores.length; i++) {
            stores[i] = store("s" + i, scheduled("MarketA"));
        }
        givenStores(stores);

        // when
        sweep.sweep();

        // then
        ArgumentCaptor<Duration> delays = ArgumentCaptor.forClass(Duration.class);
        verify(publisher, org.mockito.Mockito.times(200)).order(anyString(), eq("MarketA"), delays.capture());
        assertThat(delays.getAllValues()).allSatisfy(delay -> assertThat(delay)
                .isBetween(Duration.ZERO, Duration.ofSeconds(900)));
        assertThat(delays.getAllValues().stream().distinct().count()).isGreaterThan(1);
    }

    private void givenStores(Store... stores) {
        when(storesRepository.findAll()).thenReturn(List.of(stores));
    }

    private static Store store(String storeId, MarketplaceIntegration... integrations) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.getMarketplaces().addAll(List.of(integrations));
        return store;
    }

    private static MarketplaceIntegration scheduled(String marketplace) {
        MarketplaceIntegration integration = new MarketplaceIntegration(marketplace);
        integration.setOfferMatchingScheduled(true);
        return integration;
    }

    private static RandomGenerator fixedRandom(long seconds) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return seconds;
            }

            @Override
            public int nextInt(int bound) {
                return (int) seconds;
            }
        };
    }

    /** Draws the largest value the bound allows and remembers the bound. */
    private static final class RecordingRandom implements RandomGenerator {
        private int bound;

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            this.bound = bound;
            return bound - 1;
        }
    }
}
