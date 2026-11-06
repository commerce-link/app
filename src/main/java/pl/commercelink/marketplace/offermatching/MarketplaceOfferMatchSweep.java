package pl.commercelink.marketplace.offermatching;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Nightly pass that orders offer matching for every marketplace connection with daily matching switched on, as the
 * operator's "Dopasuj oferty" button does. Knows no marketplace: any adapter that lists the store's offers takes part.
 * Each order is held back by a random delay of up to 15 minutes, so the stores do not all hit the marketplaces at once
 * nor meet the orders imports at the full hour; the marketplace's own request limit stays in its adapter.
 */
@Slf4j
@Component
public class MarketplaceOfferMatchSweep {

    /** The longest delay SQS accepts on a message. */
    static final int MAX_SPREAD_SECONDS = 900;

    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;
    private final MarketplaceProviderFactory providerFactory;
    private final OfferMatchResultStore resultStore;
    private final MarketplaceOfferMatchPublisher publisher;
    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    MarketplaceOfferMatchSweep(StoresRepository storesRepository, StoreActivity storeActivity,
                               MarketplaceProviderFactory providerFactory, OfferMatchResultStore resultStore,
                               MarketplaceOfferMatchPublisher publisher) {
        this(storesRepository, storeActivity, providerFactory, resultStore, publisher, Clock.systemUTC(),
                new SecureRandom());
    }

    MarketplaceOfferMatchSweep(StoresRepository storesRepository, StoreActivity storeActivity,
                               MarketplaceProviderFactory providerFactory, OfferMatchResultStore resultStore,
                               MarketplaceOfferMatchPublisher publisher, Clock clock, RandomGenerator random) {
        this.storesRepository = storesRepository;
        this.storeActivity = storeActivity;
        this.providerFactory = providerFactory;
        this.resultStore = resultStore;
        this.publisher = publisher;
        this.clock = clock;
        this.random = random;
    }

    public void sweep() {
        for (Store store : storesRepository.findAll()) {
            try {
                orderScheduledRuns(store);
            } catch (RuntimeException e) {
                log.error("Offer matching sweep failed for store {}", store.getStoreId(), e);
            }
        }
    }

    private void orderScheduledRuns(Store store) {
        List<String> marketplaces = store.getMarketplaces().stream()
                .filter(MarketplaceIntegration::runsOfferMatchingDaily)
                // the same checks as the operator's order and the worker, which would skip the run
                .filter(MarketplaceIntegration::isLoggedIn)
                .map(MarketplaceIntegration::getName)
                .filter(this::listsOffers)
                .toList();
        if (marketplaces.isEmpty() || !storeActivity.isActive(store)) {
            return;
        }
        for (String marketplace : marketplaces) {
            // the worker would ignore it too, but a run going on needs no second message
            if (isRunning(store.getStoreId(), marketplace)) {
                continue;
            }
            publisher.order(store.getStoreId(), marketplace,
                    Duration.ofSeconds(random.nextInt(MAX_SPREAD_SECONDS + 1)));
        }
    }

    private boolean listsOffers(String marketplace) {
        MarketplaceProviderDescriptor descriptor = providerFactory.getDescriptor(marketplace);
        return descriptor != null && descriptor.supportsOfferListing();
    }

    private boolean isRunning(String storeId, String marketplace) {
        return resultStore.progress(storeId, marketplace)
                .filter(progress -> progress.isRunning(clock.instant()))
                .isPresent();
    }
}
