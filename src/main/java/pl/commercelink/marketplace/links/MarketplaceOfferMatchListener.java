package pl.commercelink.marketplace.links;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class MarketplaceOfferMatchListener {

    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;
    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceOfferMatching matching;
    private final OfferMatchResultStore resultStore;

    // Not caught: the message comes back after the visibility timeout and, after three receives, lands in the DLQ.
    // Links are saved per offer as the run goes, so a retry only does what is left.
    @SqsListener(
            value = MarketplaceOfferMatchPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(String body) {
        Optional<MarketplaceOfferMatchMessage> parsed = MarketplaceOfferMatchMessage.parse(body);
        if (parsed.isEmpty()) {
            log.error("Marketplace offer matching rejected an unreadable message: {}", body);
            return;
        }
        String storeId = parsed.get().storeId();
        String marketplace = parsed.get().marketplace();

        Store store = storesRepository.findById(storeId);
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            log.warn("Marketplace {} offer matching skipped store {}: no active integration", marketplace, storeId);
            return;
        }
        if (!storeActivity.isActive(store)) {
            log.warn("Marketplace {} offer matching skipped store {}: the store is inactive", marketplace, storeId);
            return;
        }
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        Optional<MarketplaceOffers> offers = provider == null ? Optional.empty() : provider.offers();
        if (offers.isEmpty()) {
            log.warn("Marketplace {} offer matching skipped store {}: the marketplace does not list offers",
                    marketplace, storeId);
            return;
        }

        try {
            resultStore.save(storeId, marketplace, matching.match(store, marketplace, offers.get()));
        } catch (RuntimeException e) {
            log.error("Marketplace {} offer matching of store {} failed", marketplace, storeId, e);
            throw e;
        }
    }
}
