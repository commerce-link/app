package pl.commercelink.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.provider.ProviderCallRejectedException;
import pl.commercelink.receipts.api.FiscalData;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptException;
import pl.commercelink.receipts.api.ReceiptFailure;
import pl.commercelink.receipts.api.ReceiptOutcomeUnknownException;
import pl.commercelink.receipts.api.ReceiptRejectedException;
import pl.commercelink.receipts.api.ReceiptValidationException;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptProcessorTest {

    private static final String KEY = ORDER_ID + ":R1";

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final StoresRepository stores = mock(StoresRepository.class);
    private final OrdersRepository orders = mock(OrdersRepository.class);
    private final ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
    private final ReceiptEffects effects = mock(ReceiptEffects.class);
    private final ReceiptAlerts alerts = mock(ReceiptAlerts.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private final FakeReceiptProvider provider = new FakeReceiptProvider();
    private ReceiptProcessor processor;
    private Order order;

    @BeforeEach
    void setUp() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        order = b2cOrder(100);
        when(stores.findById(STORE_ID)).thenReturn(store);
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> order);
        when(factory.get(any(Store.class), eq(FakeReceiptProviderDescriptor.NAME))).thenReturn(provider);
        when(alerts.sync(any(), any())).thenAnswer(i -> {
            ReceiptAttempt a = i.getArgument(0);
            ReceiptAttention attention = i.getArgument(1);
            String name = attention == null ? null : attention.name();
            boolean changed = !java.util.Objects.equals(name, a.getAttention());
            a.setAttention(name);
            return changed;
        });
        processor = new ReceiptProcessor(attempts, stores, orders, factory, new ReceiptEligibility(factory), effects,
                alerts, clock);
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(STORE_ID);
        attempt.setReceiptKey(KEY);
        attempt.setOrderId(ORDER_ID);
        attempt.setAttemptNo(1);
        attempt.setProvider(FakeReceiptProviderDescriptor.NAME);
        attempt.setState(ReceiptAttemptState.ISSUING);
        attempt.setCreatedAt(clock.instant());
        attempt.setRequestSnapshot(ReceiptSnapshotJson.write(new ReceiptRequestSnapshot(ORDER_ID, DELIVERED_AT,
                "jan@example.com",
                List.of(new ReceiptRequestSnapshot.Line(pl.commercelink.receipts.api.LineKind.GOODS, "Mysz",
                        java.math.BigDecimal.ONE, 10000, pl.commercelink.receipts.api.VatRate.VAT_23, null, null)),
                List.of(new ReceiptRequestSnapshot.Pay(pl.commercelink.receipts.api.PaymentForm.TRANSFER, 10000, null)))));
        attempt.schedule(clock.instant());
        attempts.create(attempt);
    }

    private ReceiptAttempt stored() {
        return attempts.find(STORE_ID, KEY).orElseThrow();
    }

    @Test
    void acceptedReceiptIsPolledUntilFiscalised() {
        processor.process(STORE_ID, KEY);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.PENDING);
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        provider.answerFetch(id -> Receipt.fiscalised(null, id, new FiscalData(null, null, clock.instant()), "https://x/1"));
        clock.advance(Duration.ofMinutes(1));
        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
        verify(effects, atLeastOnce()).apply(STORE_ID, KEY);
        assertThat(stored().getLeaseUntil()).isNull();
    }

    @Test
    void lostAnswerIsRetriedWithTheSameKeyEvenWhenFindSeesThePendingReceipt() {
        provider.answerIssue(r -> { throw new ReceiptOutcomeUnknownException("timeout"); });
        provider.findResults.put(KEY, Receipt.pending(KEY, "p1"));

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(stored().getIssueCalls()).isEqualTo(1);
        assertThat(stored().getLastError()).contains("timeout");
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        clock.advance(Duration.ofMinutes(1));
        processor.process(STORE_ID, KEY);

        assertThat(provider.issued).extracting(r -> r.receiptKey()).containsExactly(KEY, KEY);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.PENDING);
    }

    @Test
    void aConcurrentLeaseWinnerIsNeverOverriddenByALoserOfTheSameCasRetry() {
        // A version-conflict retry inside acquire()'s update() must not hand this attempt to us just because
        // an earlier try of the predicate happened to acquire it: "other" really won the race.
        attempts.interleaveOnce(a -> {
            a.setLeaseOwner("other");
            a.setLeaseUntil(clock.instant().plus(ReceiptProcessor.LEASE));
        });

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isZero();
        assertThat(stored().getLeaseOwner()).isEqualTo("other");
    }

    @Test
    void aLeaseLostJustBeforeIssueAbortsWithoutCallingTheProvider() {
        // The lease can be lost between acquire() succeeding and the issueCalls++ write that guards the actual
        // provider call (e.g. a slow order lookup letting the lease expire under us). Arm the steal as a side
        // effect of the order lookup stillQualifies() makes, right before that guarding write.
        Instant expectedNextCheckAt = stored().getNextCheckAt();
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> {
            attempts.interleaveOnce(a -> {
                a.setLeaseOwner("stealer");
                a.setLeaseUntil(clock.instant().plus(ReceiptProcessor.LEASE));
            });
            return order;
        });

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isZero();
        assertThat(stored().getLeaseOwner()).isEqualTo("stealer");
        // Neither the effects step nor finish() may act for an attempt this call no longer owns: the stealer's
        // own run decides the schedule and the alert, not ours.
        verify(effects, never()).apply(STORE_ID, KEY);
        assertThat(stored().getNextCheckAt()).isEqualTo(expectedNextCheckAt);
        assertThat(stored().getAttention()).isNull();
    }

    @Test
    void aLeaseExpiredButNotStolenBeforeIssueAbortsWithoutCallingTheProvider() {
        // Nobody took the lease over, but time passed (a slow order lookup) so it lapsed by the time the guard
        // right before the provider call re-checks it: still our owner id, but no longer "leased at now".
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> {
            clock.advance(ReceiptProcessor.LEASE.plusSeconds(1));
            return order;
        });

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isZero();
    }

    @Test
    void aNotEligibleOrderDoesNotBlockAnAttemptWhoseLeaseWasStolenAndIsAlreadyBeingIssued() {
        // stillQualifies() (called to decide NOT_ELIGIBLE) is the same slow window as the issueCalls guard's:
        // if the lease is stolen and the new owner is already inside issue() by the time we get back, blocking
        // the attempt here would kill it and drop that owner's eventual result (BLOCKED ignores everything).
        order.setStatus(OrderStatus.Cancelled);
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> {
            attempts.interleaveOnce(a -> {
                a.setLeaseOwner("other");
                a.setLeaseUntil(clock.instant().plus(ReceiptProcessor.LEASE));
                a.setIssueCalls(1);
            });
            return order;
        });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(stored().getIssueCalls()).isEqualTo(1);
        assertThat(stored().getLeaseOwner()).isEqualTo("other");
        assertThat(provider.issueCalls.get()).isZero();
    }

    @Test
    @Timeout(10)
    void redeliveryDuringIssueDoesNotCallIssueAgain() throws Exception {
        provider.issueGate = new CountDownLatch(1);
        Thread first = new Thread(() -> processor.process(STORE_ID, KEY));
        first.start();
        assertThat(provider.issueEntered.await(5, TimeUnit.SECONDS)).isTrue();

        processor.process(STORE_ID, KEY);   // the redelivered message

        provider.issueGate.countDown();
        first.join(5000);
        assertThat(provider.issueCalls.get()).isEqualTo(1);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.PENDING);
    }

    @Test
    void anExpiredLeaseCanBeTakenOver() {
        attempts.update(STORE_ID, KEY, a -> {
            a.setLeaseOwner("crashed");
            a.setLeaseUntil(clock.instant().minusSeconds(1));
            return true;
        });

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isEqualTo(1);
    }

    @Test
    void rejectionFailsTheAttemptAndOnlyTheOperatorMayIssueANewKey() {
        provider.answerIssue(r -> { throw new ReceiptRejectedException("fiscal_error", "stawka VAT"); });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.FAILED);
        assertThat(stored().getFailureMessage()).isEqualTo("stawka VAT");
        assertThat(stored().isScheduled()).isFalse();
        assertThat(attempts.all()).hasSize(1);
        verify(alerts, atLeastOnce()).sync(any(), eq(ReceiptAttention.FAILED));
    }

    @Test
    void invalidRequestOnTheFirstCallBlocksButAfterEarlierCallsKeepsRetrying() {
        provider.answerIssue(r -> { throw new ReceiptValidationException("mixed payments"); });
        processor.process(STORE_ID, KEY);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.BLOCKED);
    }

    @Test
    void invalidRequestAfterAnUnknownOutcomeNeverKillsTheAttempt() {
        provider.answerIssue(r -> { throw new ReceiptOutcomeUnknownException("timeout"); });
        provider.answerIssue(r -> { throw new ReceiptValidationException("config changed"); });
        processor.process(STORE_ID, KEY);
        clock.advance(Duration.ofMinutes(1));

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(stored().isInvalidAfterSend()).isTrue();
        assertThat(stored().isScheduled()).isTrue();
    }

    @Test
    void failuresBeforeSendingAreRetriedWithTheSameKey() {
        provider.answerIssue(r -> { throw new ReceiptException("401"); });
        processor.process(STORE_ID, KEY);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);

        provider.answerIssue(r -> { throw new IllegalStateException("adapter bug"); });
        clock.advance(Duration.ofMinutes(1));
        processor.process(STORE_ID, KEY);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(stored().getIssueCalls()).isEqualTo(2);
    }

    @Test
    void limiterRejectionDoesNotCountAsAnIssueCall() {
        provider.answerIssue(r -> { throw new ProviderCallRejectedException("busy"); });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getIssueCalls()).isZero();
        assertThat(stored().getPreSendFailures()).isEqualTo(1);
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
    }

    @Test
    void aWebhookFiscalisationDuringIssueIsNotUndoneByTheIssueResult() {
        provider.answerIssue(r -> {
            attempts.update(STORE_ID, KEY, a -> ReceiptStatusMerger.merge(a,
                    Receipt.fiscalised(KEY, "fake-" + KEY, new FiscalData(null, null, clock.instant()), "https://x/1"),
                    ReceiptStatusMerger.Source.PUSH, clock.instant()));
            return Receipt.pending(KEY, "fake-" + KEY);
        });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.FISCALISED);
        verify(effects).apply(STORE_ID, KEY);
    }

    @Test
    void anOrderCancelledBeforeTheFirstCallBlocksWithoutCallingTheProvider() {
        order.setStatus(OrderStatus.Cancelled);

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.BLOCKED);
        assertThat(stored().getBlockedReason()).isEqualTo(ReceiptBlockReason.NOT_ELIGIBLE.name());
        assertThat(provider.issueCalls.get()).isZero();
    }

    @Test
    void anOrderCancelledAfterAnUnknownOutcomeIsStillRetried() {
        provider.answerIssue(r -> { throw new ReceiptOutcomeUnknownException("timeout"); });
        processor.process(STORE_ID, KEY);
        order.setStatus(OrderStatus.Cancelled);
        clock.advance(Duration.ofMinutes(1));

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isEqualTo(2);
    }

    @Test
    void fiscalisedWithoutLinkIsPolledForTheLinkThenGivenUp() {
        doAnswer(i -> {
            attempts.update(STORE_ID, KEY, a -> {
                a.setDocumentAttachedAt(clock.instant());   // what the real effects do without a link
                return true;
            });
            return null;
        }).when(effects).apply(STORE_ID, KEY);
        provider.answerIssue(r -> Receipt.fiscalised(KEY, "p1", new FiscalData(null, null, clock.instant()), null));
        processor.process(STORE_ID, KEY);
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));

        clock.advance(Duration.ofDays(8));
        processor.process(STORE_ID, KEY);

        assertThat(stored().getLinkGaveUpAt()).isNotNull();
        assertThat(stored().isScheduled()).isFalse();
    }

    @Test
    void aFailureReportedLaterEndsAPendingAttempt() {
        processor.process(STORE_ID, KEY);
        provider.answerFetch(id -> Receipt.failed(null, id, new ReceiptFailure("fiscal_error", "VAT")));
        clock.advance(Duration.ofMinutes(1));

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.FAILED);
    }

    @Test
    void aMissingProviderAtIssueTimeRecordsAnErrorWithoutCallingIssueOrIncrementingIssueCalls() {
        // The adapter is not on the classpath: providerFactory.get() returns null instead of throwing. Before the
        // fix this null slipped past the preparation try/catch and only blew up as an NPE on provider.issue(),
        // after issueCalls had already been bumped by the guard right before that call.
        when(factory.get(any(Store.class), eq(FakeReceiptProviderDescriptor.NAME))).thenReturn(null);

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isZero();
        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(stored().getIssueCalls()).isZero();
        assertThat(stored().getLastError()).isNotBlank();
        assertThat(stored().getLastErrorAt()).isNotNull();
    }

    @Test
    void preSendFailuresBackOffGrowsInsteadOfStayingAtOneMinuteForever() {
        when(factory.get(any(Store.class), eq(FakeReceiptProviderDescriptor.NAME))).thenReturn(null);

        processor.process(STORE_ID, KEY);
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        clock.advance(Duration.ofMinutes(1));
        processor.process(STORE_ID, KEY);
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(2)));

        clock.advance(Duration.ofMinutes(2));
        processor.process(STORE_ID, KEY);
        assertThat(stored().getNextCheckAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));

        assertThat(stored().getIssueCalls()).isZero();
    }

    @Test
    @Timeout(10)
    void theLeaseIsRenewedAtThePreIssueGuardNotJustAtAcquisition() throws Exception {
        // Advance the clock inside the order lookup stillQualifies() makes, between acquire() (which sets the
        // first lease) and the guard right before issue() (which must renew it): if the guard did not renew the
        // lease, it would still carry acquire()'s earlier deadline.
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> {
            clock.advance(Duration.ofMinutes(5));
            return order;
        });
        provider.issueGate = new CountDownLatch(1);

        Thread worker = new Thread(() -> processor.process(STORE_ID, KEY));
        worker.start();
        assertThat(provider.issueEntered.await(5, TimeUnit.SECONDS)).isTrue();

        Instant guardNow = clock.instant();
        assertThat(stored().getLeaseUntil()).isEqualTo(guardNow.plus(ReceiptProcessor.LEASE));

        provider.issueGate.countDown();
        worker.join(5000);
    }

    @Test
    void aRejectionDuringIssueNeverOverwritesAnAttemptAlreadyMovedToPendingByAConcurrentPush() {
        // A push webhook can land while our own issue() call is in flight and establish a newer, real outcome
        // (PENDING) before our call comes back with a rejection. That stale rejection must never turn a PENDING
        // attempt into FAILED — only an attempt still ISSUING is ours to fail.
        provider.answerIssue(r -> {
            attempts.interleaveOnce(a -> a.setState(ReceiptAttemptState.PENDING));
            throw new ReceiptRejectedException("fiscal_error", "stawka VAT");
        });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.PENDING);
    }

    @Test
    void alertSyncFailureNeverBlocksTheLeaseReleaseOrTheScheduleWrite() {
        // doThrow(...).when(mock)... (rather than when(mock...).thenThrow(...)) avoids re-running the default
        // answer stubbed in setUp() as a side effect of registering this override.
        doThrow(new RuntimeException("notification service down")).when(alerts).sync(any(), any());
        provider.answerIssue(r -> { throw new ReceiptRejectedException("fiscal_error", "stawka VAT"); });

        processor.process(STORE_ID, KEY);

        assertThat(stored().getState()).isEqualTo(ReceiptAttemptState.FAILED);
        assertThat(stored().getLeaseOwner()).isNull();
        assertThat(stored().getLeaseUntil()).isNull();
        assertThat(stored().isScheduled()).isFalse();
    }

    @Test
    void blockedAttemptsOnlyRaiseTheirAlert() {
        attempts.update(STORE_ID, KEY, a -> {
            a.setState(ReceiptAttemptState.BLOCKED);
            return true;
        });

        processor.process(STORE_ID, KEY);

        assertThat(provider.issueCalls.get()).isZero();
        assertThat(stored().isScheduled()).isFalse();
        assertThat(stored().getAttention()).isEqualTo("BLOCKED");
    }
}
