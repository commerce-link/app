package pl.commercelink.marketplace.offermatching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import pl.commercelink.marketplace.MarketplaceProviderFactory;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.offermatching.OfferMatchProgress.Status;
import pl.commercelink.rest.client.HttpClientException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOfferMatchListenerTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

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
    @Mock
    private MarketplaceOfferMatchPublisher publisher;

    private MarketplaceOfferMatchListener listener;
    private Store store;
    private ListAppender<ILoggingEvent> logs;
    private final AtomicReference<OfferMatchProgress> file = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        listener = new MarketplaceOfferMatchListener(storesRepository, storeActivity, providerFactory, matching,
                resultStore, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
        store = new Store();
        store.setStoreId(STORE);
        MarketplaceIntegration integration = new MarketplaceIntegration(EMPIK);
        integration.setLoggedIn(true);
        store.getMarketplaces().add(integration);
        when(storesRepository.findById(STORE)).thenReturn(store);
        when(storeActivity.isActive(store)).thenReturn(true);
        when(providerFactory.get(store, EMPIK)).thenReturn(provider);
        when(provider.offers()).thenReturn(Optional.of(offers));
        // the progress file as S3 keeps it: what was saved last, gone once deleted
        when(resultStore.progress(STORE, EMPIK)).thenAnswer(call -> Optional.ofNullable(file.get()));
        doAnswer(call -> {
            file.set(call.getArgument(2));
            return null;
        }).when(resultStore).saveProgress(eq(STORE), eq(EMPIK), any());
        doAnswer(call -> {
            file.set(null);
            return null;
        }).when(resultStore).deleteProgress(STORE, EMPIK);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(MarketplaceOfferMatchListener.class)).addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        ((Logger) LoggerFactory.getLogger(MarketplaceOfferMatchListener.class)).detachAppender(logs);
    }

    private static String start() {
        return MarketplaceOfferMatchMessage.start(STORE, EMPIK).toJson();
    }

    private static String continuation(String runId, String cursor) {
        return MarketplaceOfferMatchMessage.continuation(STORE, EMPIK, runId, cursor).toJson();
    }

    private static OfferMatchProgress running(String runId, String nextCursor, Instant updatedAt) {
        return new OfferMatchProgress(runId, NOW.minus(Duration.ofHours(1)), updatedAt, Status.RUNNING, nextCursor, 100,
                6150, 0, 0, null, null, null, 0, null, null);
    }

    private static OfferMatchResult result() {
        return new OfferMatchResult(NOW, 1, List.of(), List.of(), 1, 0);
    }

    private void givenProgress(OfferMatchProgress progress) {
        file.set(progress);
    }

    private static OfferMatchProgress pausedAt(String runId, String cursor, int idleRateLimitPauses) {
        return new OfferMatchProgress(runId, NOW.minus(Duration.ofHours(1)), NOW, Status.RUNNING, cursor, 200, 6150, 0, 0,
                null, null, null, idleRateLimitPauses, null, null);
    }

    private OfferMatchProgress lastSavedProgress() {
        ArgumentCaptor<OfferMatchProgress> saved = ArgumentCaptor.forClass(OfferMatchProgress.class);
        verify(resultStore, org.mockito.Mockito.atLeastOnce()).saveProgress(eq(STORE), eq(EMPIK), saved.capture());
        return saved.getValue();
    }

    private List<Level> levels() {
        return logs.list.stream().map(ILoggingEvent::getLevel).toList();
    }

    @Test
    void startsARunWithFreshProgressSavedBeforeTheFirstPage() {
        // given
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(new MarketplaceOfferMatching.Finished(result()));
        ArgumentCaptor<OfferMatchProgress> resumed = ArgumentCaptor.forClass(OfferMatchProgress.class);

        // when
        listener.handleMessage(start());

        // then
        verify(matching).resume(eq(store), eq(EMPIK), eq(offers), resumed.capture());
        OfferMatchProgress progress = resumed.getValue();
        assertThat(progress.runId()).isNotBlank();
        assertThat(progress.nextCursor()).isNull();
        assertThat(progress.startedAt()).isEqualTo(NOW);
        assertThat(progress.status()).isEqualTo(Status.RUNNING);
        InOrder order = inOrder(resultStore, matching);
        order.verify(resultStore).saveProgress(STORE, EMPIK, progress);
        order.verify(matching).resume(store, EMPIK, offers, progress);
    }

    @Test
    void finalPageWritesLatestAndRemovesProgress() {
        // given
        OfferMatchResult result = result();
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(new MarketplaceOfferMatching.Finished(result));

        // when
        listener.handleMessage(start());

        // then
        InOrder order = inOrder(resultStore);
        order.verify(resultStore).save(STORE, EMPIK, result);
        order.verify(resultStore).deleteProgress(STORE, EMPIK);
        verifyNoInteractions(publisher);
    }

    @Test
    void publishesContinuationDelayedByRetryAfter() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        OfferMatchProgress paused = running("run-1", "200", NOW);
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any()))
                .thenReturn(new MarketplaceOfferMatching.Paused(paused, Duration.ofSeconds(60)));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then the progress is saved before the continuation can arrive
        InOrder order = inOrder(resultStore, publisher);
        order.verify(resultStore).saveProgress(STORE, EMPIK, paused);
        order.verify(publisher).continueRun(STORE, EMPIK, "run-1", "200", Duration.ofSeconds(60));
        verify(resultStore, never()).save(any(), any(), any());
        verify(resultStore, never()).deleteProgress(any(), any());
    }

    @Test
    void continuesWithoutDelayWhenTimeBudgetIsUsed() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        OfferMatchProgress paused = running("run-1", "4200", NOW);
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any()))
                .thenReturn(new MarketplaceOfferMatching.Paused(paused, Duration.ZERO));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verify(resultStore).saveProgress(STORE, EMPIK, paused);
        verify(publisher).continueRun(STORE, EMPIK, "run-1", "4200", Duration.ZERO);
    }

    @Test
    void continuesTheRunFromItsProgressFile() {
        // given
        OfferMatchProgress current = running("run-1", "100", NOW.minusSeconds(60));
        givenProgress(current);
        when(matching.resume(store, EMPIK, offers, current)).thenReturn(new MarketplaceOfferMatching.Finished(result()));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verify(matching).resume(store, EMPIK, offers, current);
        verify(resultStore, never()).saveProgress(any(), any(), any());
        verify(resultStore).deleteProgress(STORE, EMPIK);
    }

    @Test
    void ignoresMessageOfAnotherRun() {
        // given
        givenProgress(running("run-2", "100", NOW));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, publisher, providerFactory);
        verify(resultStore, never()).saveProgress(any(), any(), any());
        assertThat(levels()).doesNotContain(Level.ERROR);
    }

    @Test
    void ignoresADuplicatedContinuation() {
        // given the first copy already moved the run on
        givenProgress(running("run-1", "300", NOW));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, publisher, providerFactory);
        verify(resultStore, never()).saveProgress(any(), any(), any());
    }

    @Test
    void ignoresContinuationWithoutProgressFile() {
        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, publisher, providerFactory);
        verify(resultStore, never()).saveProgress(any(), any(), any());
        assertThat(levels()).doesNotContain(Level.ERROR);
    }

    @Test
    void ignoresAContinuationOfAFailedRun() {
        // given
        givenProgress(running("run-1", "100", NOW).failed(NOW, OfferMatchError.INTERNAL));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, publisher);
    }

    @Test
    void ignoresASecondStartWhileARunIsGoing() {
        // given
        givenProgress(running("run-1", "100", NOW.minus(Duration.ofMinutes(29))));

        // when
        listener.handleMessage(start());

        // then
        verifyNoInteractions(matching, publisher, providerFactory);
        verify(resultStore, never()).saveProgress(any(), any(), any());
    }

    @Test
    void startsOverAStaleRun() {
        // given
        givenProgress(running("run-old", "100", NOW.minus(Duration.ofMinutes(31))));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(new MarketplaceOfferMatching.Finished(result()));

        // when
        listener.handleMessage(start());

        // then
        OfferMatchProgress fresh = lastSavedProgress();
        assertThat(fresh.runId()).isNotEqualTo("run-old");
        assertThat(fresh.processed()).isZero();
        verify(matching).resume(store, EMPIK, offers, fresh);
    }

    @Test
    void startsOverAFailedRun() {
        // given
        givenProgress(running("run-old", "100", NOW).failed(NOW, OfferMatchError.INTERNAL));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(new MarketplaceOfferMatching.Finished(result()));

        // when
        listener.handleMessage(start());

        // then
        verify(matching).resume(eq(store), eq(EMPIK), eq(offers), any());
        assertThat(lastSavedProgress().runId()).isNotEqualTo("run-old");
    }

    @Test
    void marksRunFailedOnOtherErrors() {
        // given
        OfferMatchProgress current = running("run-1", "100", NOW.minusSeconds(60));
        givenProgress(current);
        when(matching.resume(store, EMPIK, offers, current)).thenThrow(new IllegalStateException("OF21 answered 500"));

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then the message is acknowledged: the operator orders a new run
        OfferMatchProgress failed = lastSavedProgress();
        assertThat(failed.status()).isEqualTo(Status.FAILED);
        assertThat(failed.runId()).isEqualTo("run-1");
        assertThat(failed.error()).isEqualTo(OfferMatchError.INTERNAL);
        assertThat(failed.errorStatus()).isNull();
        assertThat(failed.updatedAt()).isEqualTo(NOW);
        verifyNoInteractions(publisher);
        assertThat(levels()).containsExactly(Level.ERROR);
    }

    @Test
    void marksRunFailedWhenTheProviderCannotBeBuilt() {
        // given
        when(providerFactory.get(store, EMPIK)).thenThrow(new IllegalStateException("credentials unreadable"));

        // when
        listener.handleMessage(start());

        // then
        verifyNoInteractions(matching, publisher);
        assertThat(lastSavedProgress().status()).isEqualTo(Status.FAILED);
        assertThat(lastSavedProgress().error()).isEqualTo(OfferMatchError.INTERNAL);
        assertThat(levels()).containsExactly(Level.ERROR);
    }

    @Test
    void recordsTheMarketplacesHttpStatusButNotItsAnswer() {
        // given the adapter wraps the HTTP error
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenThrow(new IllegalStateException(
                "OF21 failed", new HttpClientException(503, "{\"token\":\"secret\"}")));

        // when
        listener.handleMessage(start());

        // then
        OfferMatchProgress failed = lastSavedProgress();
        assertThat(failed.error()).isEqualTo(OfferMatchError.MARKETPLACE_ERROR);
        assertThat(failed.errorStatus()).isEqualTo(503);
        assertThat(levels()).containsExactly(Level.ERROR);
    }

    @Test
    void doesNotFinishARunAnotherRunReplaced() {
        // given a second run took the file over while this one read its pages
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenAnswer(call -> {
            file.set(running("run-2", null, NOW));
            return new MarketplaceOfferMatching.Finished(result());
        });

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verify(resultStore, never()).save(any(), any(), any());
        verify(resultStore, never()).deleteProgress(any(), any());
        assertThat(file.get().runId()).isEqualTo("run-2");
        assertThat(levels()).doesNotContain(Level.ERROR);
    }

    @Test
    void doesNotSaveAPauseOfARunAnotherRunReplaced() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenAnswer(call -> {
            file.set(running("run-2", null, NOW));
            return new MarketplaceOfferMatching.Paused(running("run-1", "200", NOW), Duration.ofSeconds(60));
        });

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verify(resultStore, never()).saveProgress(any(), any(), any());
        verifyNoInteractions(publisher);
        assertThat(file.get().runId()).isEqualTo("run-2");
    }

    @Test
    void doesNotMarkARunAnotherRunReplacedAsFailed() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenAnswer(call -> {
            file.set(running("run-2", null, NOW));
            throw new IllegalStateException("OF21 failed");
        });

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        assertThat(file.get().runId()).isEqualTo("run-2");
        assertThat(file.get().status()).isEqualTo(Status.RUNNING);
    }

    @Test
    void failsTheRunWhenTheMarketplaceKeepsRefusingPages() {
        // given the fifteenth pause in a row without a page read
        givenProgress(pausedAt("run-1", "200", 14));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(
                new MarketplaceOfferMatching.Paused(pausedAt("run-1", "200", 15), Duration.ofSeconds(60)));

        // when
        listener.handleMessage(continuation("run-1", "200"));

        // then
        verifyNoInteractions(publisher);
        assertThat(file.get().status()).isEqualTo(Status.FAILED);
        assertThat(file.get().error()).isEqualTo(OfferMatchError.RATE_LIMIT_EXHAUSTED);
    }

    @Test
    void keepsWaitingOnTheRateLimitBelowTheLimitOfPauses() {
        // given
        givenProgress(pausedAt("run-1", "200", 13));
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any())).thenReturn(
                new MarketplaceOfferMatching.Paused(pausedAt("run-1", "200", 14), Duration.ofSeconds(60)));

        // when
        listener.handleMessage(continuation("run-1", "200"));

        // then
        verify(publisher).continueRun(STORE, EMPIK, "run-1", "200", Duration.ofSeconds(60));
        assertThat(file.get().status()).isEqualTo(Status.RUNNING);
    }

    @Test
    void failsTheRunWhenTheMarketplaceNoLongerListsOffers() {
        // given
        when(provider.offers()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(start());

        // then
        verifyNoInteractions(matching, publisher);
        assertThat(lastSavedProgress().status()).isEqualTo(Status.FAILED);
        assertThat(lastSavedProgress().error()).isEqualTo(OfferMatchError.NO_LISTING);
    }

    @Test
    void failsAContinuedRunOfAStoreDeactivatedMeanwhile() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        when(storeActivity.isActive(store)).thenReturn(false);

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, providerFactory, publisher);
        assertThat(lastSavedProgress().error()).isEqualTo(OfferMatchError.STORE_INACTIVE);
    }

    @Test
    void skipsAStartForAnInactiveStore() {
        // given
        when(storeActivity.isActive(store)).thenReturn(false);

        // when
        listener.handleMessage(start());

        // then
        verifyNoInteractions(matching, providerFactory, publisher);
        verify(resultStore, never()).saveProgress(any(), any(), any());
    }

    @Test
    void skipsAStartForAStoreWithoutTheMarketplaceConnected() {
        // given
        store.getMarketplaces().clear();

        // when
        listener.handleMessage(start());

        // then
        verifyNoInteractions(matching, providerFactory, publisher);
        verify(resultStore, never()).saveProgress(any(), any(), any());
    }

    @Test
    void failsAContinuedRunWhoseConnectionExpired() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        store.getMarketplaceIntegration(EMPIK).setLoggedIn(false);

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        verifyNoInteractions(matching, providerFactory, publisher);
        assertThat(lastSavedProgress().status()).isEqualTo(Status.FAILED);
        assertThat(lastSavedProgress().runId()).isEqualTo("run-1");
        assertThat(lastSavedProgress().error()).isEqualTo(OfferMatchError.INTEGRATION_INACTIVE);
    }

    @Test
    void failsTheRunWhenTheContinuationCannotBeSent() {
        // given
        givenProgress(running("run-1", "100", NOW.minusSeconds(60)));
        OfferMatchProgress paused = running("run-1", "200", NOW);
        when(matching.resume(eq(store), eq(EMPIK), eq(offers), any()))
                .thenReturn(new MarketplaceOfferMatching.Paused(paused, Duration.ofSeconds(60)));
        org.mockito.Mockito.doThrow(new IllegalStateException("SQS down"))
                .when(publisher).continueRun(anyString(), anyString(), anyString(), anyString(), any());

        // when
        listener.handleMessage(continuation("run-1", "100"));

        // then
        assertThat(lastSavedProgress().status()).isEqualTo(Status.FAILED);
        assertThat(lastSavedProgress().error()).isEqualTo(OfferMatchError.INTERNAL);
    }

    @Test
    void dropsUnreadableMessage() {
        // when
        listener.handleMessage("not json");

        // then
        verifyNoInteractions(matching, resultStore, storesRepository, publisher);
    }
}
