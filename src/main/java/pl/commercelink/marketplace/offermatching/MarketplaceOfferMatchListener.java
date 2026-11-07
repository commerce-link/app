package pl.commercelink.marketplace.offermatching;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs an offer matching order (the operator's or the nightly sweep's), one part per message: a part ends when the
 * marketplace limits requests or the time budget is used, its progress is saved and a continuation is sent, delayed by
 * SQS rather than a sleeping worker. Only the continuation at the saved run and cursor goes on, so a duplicated or late one is dropped.
 */
@Slf4j
@Component
// Not gated on application.env: SQS gives each message to one instance, so it runs in every environment, as
// ReportGenerationListener does. The queue is fed by the operator and, on AWS only (application.env=prod), by the nightly
// MarketplaceOfferMatchSweep; a local run orders nothing by itself.
class MarketplaceOfferMatchListener {

    /**
     * Pauses in a row on the request limit without a page read before the run gives up: at Empik's 60 s that is a quarter
     * of an hour in which the marketplace answered nothing but 429.
     */
    static final int MAX_IDLE_RATE_LIMIT_PAUSES = 15;

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
        OfferMatchError skipped = skipReason(store, marketplace);
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
            if (ownsProgressFile(storeId, marketplace, progress.runId())) {
                Integer status = httpStatusOf(e);
                resultStore.saveProgress(storeId, marketplace, progress.failed(clock.instant(),
                        status == null ? OfferMatchError.INTERNAL : OfferMatchError.MARKETPLACE_ERROR, status));
            }
        }
    }

    private void run(Store store, String marketplace, OfferMatchProgress progress) {
        String storeId = store.getStoreId();
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        Optional<MarketplaceOffers> offers = provider == null ? Optional.empty() : provider.offers();
        if (offers.isEmpty()) {
            log.warn("Marketplace {} offer matching skipped store {}: the marketplace does not list offers",
                    marketplace, storeId);
            resultStore.saveProgress(storeId, marketplace, progress.failed(clock.instant(), OfferMatchError.NO_LISTING));
            return;
        }
        MarketplaceOfferMatching.Step step = matching.resume(store, marketplace, offers.get(), progress);
        // a run that went stale while this message worked may have been replaced; the newer run owns the files now
        if (!ownsProgressFile(storeId, marketplace, progress.runId())) {
            log.info("Marketplace {} offer matching of store {} stopped run {}: a newer run replaced it", marketplace,
                    storeId, progress.runId());
            return;
        }
        if (step instanceof MarketplaceOfferMatching.Finished finished) {
            resultStore.save(storeId, marketplace, finished.result());
            resultStore.deleteProgress(storeId, marketplace);
        } else if (step instanceof MarketplaceOfferMatching.Paused paused) {
            if (paused.progress().idleRateLimitPauses() >= MAX_IDLE_RATE_LIMIT_PAUSES) {
                log.warn("Marketplace {} offer matching of store {} gave up run {}: {} pauses on the request limit "
                        + "without a page", marketplace, storeId, progress.runId(), MAX_IDLE_RATE_LIMIT_PAUSES);
                resultStore.saveProgress(storeId, marketplace,
                        paused.progress().failed(clock.instant(), OfferMatchError.RATE_LIMIT_EXHAUSTED));
                return;
            }
            // saved before it is sent: a continuation arriving first would not match the file and be dropped
            resultStore.saveProgress(storeId, marketplace, paused.progress());
            publisher.continueRun(storeId, marketplace, paused.progress().runId(), paused.progress().nextCursor(),
                    paused.delay());
        }
    }

    private boolean ownsProgressFile(String storeId, String marketplace, String runId) {
        return resultStore.progress(storeId, marketplace).map(OfferMatchProgress::runId).filter(runId::equals)
                .isPresent();
    }

    private OfferMatchError skipReason(Store store, String marketplace) {
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            return OfferMatchError.INTEGRATION_INACTIVE;
        }
        if (!storeActivity.isActive(store)) {
            return OfferMatchError.STORE_INACTIVE;
        }
        return null;
    }

    /** The marketplace's HTTP status when the failure is (or wraps) its HTTP error; its answer stays in the log only. */
    private static Integer httpStatusOf(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpClientException http) {
                return http.getStatusCode();
            }
        }
        return null;
    }
}
