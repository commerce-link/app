package pl.commercelink.marketplace.links;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs the operator's offer matching order, one part per message: a part ends when the marketplace limits requests or
 * the time budget is used, its progress is saved and a continuation is sent, delayed by SQS rather than a sleeping
 * worker. Only the continuation at the saved run and cursor goes on, so a duplicated or late one is dropped.
 */
@Slf4j
@Component
// Not gated on application.env: the queue is fed only by an operator, not a schedule, and SQS gives each message to one
// instance, so it runs in every environment, as ReportGenerationListener does.
class MarketplaceOfferMatchListener {

    private static final int MAX_ERROR_LENGTH = 300;

    private final StoresRepository storesRepository;
    private final StoreActivity storeActivity;
    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceOfferMatching matching;
    private final OfferMatchResultStore resultStore;
    private final MarketplaceOfferMatchPublisher publisher;
    private final Clock clock;

    @Autowired
    MarketplaceOfferMatchListener(StoresRepository storesRepository, StoreActivity storeActivity,
                                  MarketplaceProviderFactory providerFactory, MarketplaceOfferMatching matching,
                                  OfferMatchResultStore resultStore, MarketplaceOfferMatchPublisher publisher) {
        this(storesRepository, storeActivity, providerFactory, matching, resultStore, publisher, Clock.systemUTC());
    }

    MarketplaceOfferMatchListener(StoresRepository storesRepository, StoreActivity storeActivity,
                                  MarketplaceProviderFactory providerFactory, MarketplaceOfferMatching matching,
                                  OfferMatchResultStore resultStore, MarketplaceOfferMatchPublisher publisher,
                                  Clock clock) {
        this.storesRepository = storesRepository;
        this.storeActivity = storeActivity;
        this.providerFactory = providerFactory;
        this.matching = matching;
        this.resultStore = resultStore;
        this.publisher = publisher;
        this.clock = clock;
    }

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
        MarketplaceOfferMatchMessage message = parsed.get();
        String storeId = message.storeId();
        String marketplace = message.marketplace();

        Optional<OfferMatchProgress> current = resultStore.progress(storeId, marketplace);
        if (message.isContinuation()) {
            if (current.isEmpty() || !message.continues(current.get())) {
                log.info("Marketplace {} offer matching of store {} ignored a continuation of run {} at {}: not the run's "
                        + "next part", marketplace, storeId, message.runId(), message.cursor());
                return;
            }
        } else if (current.isPresent() && current.get().isRunning(clock.instant())) {
            log.info("Marketplace {} offer matching of store {} ignored a new run: run {} is in progress", marketplace,
                    storeId, current.get().runId());
            return;
        }

        Store store = storesRepository.findById(storeId);
        String skipped = skipReason(store, marketplace);
        if (skipped != null) {
            log.warn("Marketplace {} offer matching skipped store {}: {}", marketplace, storeId, skipped);
            // a run already going on is closed, so the page does not show it running until it goes stale
            current.filter(progress -> message.isContinuation())
                    .ifPresent(progress -> resultStore.saveProgress(storeId, marketplace,
                            progress.failed(clock.instant(), skipped)));
            return;
        }

        OfferMatchProgress progress = current.filter(previous -> message.isContinuation())
                .orElseGet(() -> OfferMatchProgress.start(UUID.randomUUID().toString(), clock.instant()));
        if (!message.isContinuation()) {
            // saved before the first page, so the page shows the run and a second order is refused at once
            resultStore.saveProgress(storeId, marketplace, progress);
        }
        try {
            run(store, marketplace, progress);
        } catch (RuntimeException e) {
            // not rethrown: a retry would read the same pages again; the page shows the failure and the operator re-orders
            log.error("Marketplace {} offer matching of store {} failed (run {})", marketplace, storeId,
                    progress.runId(), e);
            resultStore.saveProgress(storeId, marketplace, progress.failed(clock.instant(), describe(e)));
        }
    }

    private void run(Store store, String marketplace, OfferMatchProgress progress) {
        String storeId = store.getStoreId();
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        Optional<MarketplaceOffers> offers = provider == null ? Optional.empty() : provider.offers();
        if (offers.isEmpty()) {
            log.warn("Marketplace {} offer matching skipped store {}: the marketplace does not list offers",
                    marketplace, storeId);
            resultStore.saveProgress(storeId, marketplace,
                    progress.failed(clock.instant(), "the marketplace does not list offers"));
            return;
        }
        MarketplaceOfferMatching.Step step = matching.resume(store, marketplace, offers.get(), progress);
        if (step instanceof MarketplaceOfferMatching.Finished finished) {
            resultStore.save(storeId, marketplace, finished.result());
            resultStore.deleteProgress(storeId, marketplace);
        } else if (step instanceof MarketplaceOfferMatching.Paused paused) {
            // saved before it is sent: a continuation arriving first would not match the file and be dropped
            resultStore.saveProgress(storeId, marketplace, paused.progress());
            publisher.continueRun(storeId, marketplace, paused.progress().runId(), paused.progress().nextCursor(),
                    paused.delay());
        }
    }

    private String skipReason(Store store, String marketplace) {
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            return "no active integration";
        }
        if (!storeActivity.isActive(store)) {
            return "the store is inactive";
        }
        return null;
    }

    private static String describe(RuntimeException e) {
        String text = StringUtils.normalizeSpace(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        return StringUtils.abbreviate(text, MAX_ERROR_LENGTH);
    }
}
