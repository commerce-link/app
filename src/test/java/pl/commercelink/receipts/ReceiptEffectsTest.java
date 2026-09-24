package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptEffectsTest {

    private static final String KEY = ORDER_ID + ":R1";

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final OrdersRepository orders = mock(OrdersRepository.class);
    private final ReceiptAttemptService attemptService = mock(ReceiptAttemptService.class);
    private final OrderLifecycleEventPublisher lifecycleEvents = mock(OrderLifecycleEventPublisher.class);
    private final EmailClient emailClient = mock(EmailClient.class);
    private final OrderEventsRepository orderEvents = mock(OrderEventsRepository.class);
    private final StoresRepository stores = mock(StoresRepository.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private ReceiptEffects effects;
    private Order order;
    private Store store;

    @BeforeEach
    void setUp() {
        order = b2cOrder(100);
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> order);
        OptimisticLockingExecutor locking = mock(OptimisticLockingExecutor.class);
        doAnswer(i -> {
            Object entity = ((Supplier<?>) i.getArgument(0)).get();
            ((Consumer<Object>) i.getArgument(1)).accept(entity);
            ((Consumer<Object>) i.getArgument(2)).accept(entity);
            return entity;
        }).when(locking).modifyAndSave(any(), any(), any());
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any())).thenReturn(true);
        store = new Store();
        store.setStoreId(STORE_ID);
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.enableNotification(EmailNotificationType.ORDER_RECEIPT, "OrderReceiptTemplate");
        store.setClientNotificationsConfiguration(notifications);
        when(stores.findById(STORE_ID)).thenReturn(store);
        effects = new ReceiptEffects(attempts, orders, locking, attemptService, lifecycleEvents, emailClient,
                orderEvents, stores, clock);
    }

    private void fiscalised(String url) {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(STORE_ID);
        attempt.setReceiptKey(KEY);
        attempt.setOrderId(ORDER_ID);
        attempt.setState(ReceiptAttemptState.FISCALISED);
        attempt.setFiscalisedAt(Instant.parse("2026-09-23T12:00:00Z"));
        attempt.setDocumentUrl(url);
        attempt.setRequestSnapshot(ReceiptSnapshotJson.write(new ReceiptRequestSnapshot(ORDER_ID, DELIVERED_AT,
                "jan@example.com", List.of(), List.of())));
        attempts.create(attempt);
    }

    @Test
    void withTheLinkTheOrderGetsTheDocumentMarketplaceEventAndOneEmail() {
        fiscalised("https://paragony.pl/r/1");

        effects.apply(STORE_ID, KEY);
        effects.apply(STORE_ID, KEY);

        assertThat(order.getDocuments()).filteredOn(d -> d.getType() == DocumentType.Receipt).singleElement()
                .satisfies(d -> {
                    assertThat(d.getId()).isEqualTo(KEY);
                    assertThat(d.getNumber()).isEqualTo(KEY);
                    assertThat(d.getLink()).isEqualTo("https://paragony.pl/r/1");
                });
        verify(attemptService, times(1)).saveThroughLifecycle(order);
        verify(lifecycleEvents, times(1)).publish(order, OrderLifecycleEventType.InvoiceCreated);
        verify(emailClient, times(1)).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any());
        ReceiptAttempt done = attempts.find(STORE_ID, KEY).orElseThrow();
        assertThat(done.getEmailSentAt()).isNotNull();
        assertThat(ReceiptEffects.pending(done)).isFalse();
    }

    @Test
    void theEmailOrderEventIsRecordedInTheServersLocalTimeNotTheClocksUtcZone() {
        // The clock's own zone (Europe/Warsaw, see MutableClock.at) must not be what decides the timestamp -
        // only the JVM's default zone may. Asia/Tokyo differs from both UTC and Europe/Warsaw, so this fails
        // if the code falls back to either of those instead of the system default.
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            fiscalised("https://paragony.pl/r/1");

            effects.apply(STORE_ID, KEY);

            ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEvents).save(captor.capture());
            LocalDateTime local = captor.getValue().getCreatedAt();
            assertThat(local).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Tokyo")));
            assertThat(local).isNotEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            assertThat(local).isNotEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Europe/Warsaw")));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void withoutTheLinkTheOrderIsClosedButNothingGoesOut() {
        fiscalised(null);

        effects.apply(STORE_ID, KEY);

        assertThat(order.getClosingDocument()).isPresent();
        verifyNoInteractions(lifecycleEvents, emailClient);
        assertThat(ReceiptEffects.pending(attempts.find(STORE_ID, KEY).orElseThrow())).isFalse();
    }

    @Test
    void aLinkArrivingLaterCompletesTheDocumentAndSendsTheEmail() {
        fiscalised(null);
        effects.apply(STORE_ID, KEY);
        attempts.update(STORE_ID, KEY, a -> {
            a.setDocumentUrl("https://paragony.pl/r/1");
            return true;
        });

        assertThat(ReceiptEffects.pending(attempts.find(STORE_ID, KEY).orElseThrow())).isTrue();
        effects.apply(STORE_ID, KEY);

        assertThat(order.getClosingDocument()).get().extracting(d -> d.getLink()).isEqualTo("https://paragony.pl/r/1");
        verify(emailClient).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any());
    }

    @Test
    void anEmailTheClientCouldNotSendIsNeverRetried() {
        fiscalised("https://paragony.pl/r/1");
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any())).thenReturn(false);

        effects.apply(STORE_ID, KEY);
        effects.apply(STORE_ID, KEY);

        verify(emailClient, times(1)).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any());
        ReceiptAttempt attempt = attempts.find(STORE_ID, KEY).orElseThrow();
        assertThat(attempt.getEmailClaimedAt()).isNotNull();
        assertThat(attempt.getEmailSentAt()).isNull();
        assertThat(attempt.getEmailSkippedAt()).isNull();
        assertThat(attempt.getLastError()).contains("e-mail");
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, clock.instant())).isEqualTo(ReceiptAttention.EMAIL_NOT_SENT);
    }

    @Test
    void aStoreThatDoesNotSendTheReceiptEmailTypeSkipsItQuietly() {
        store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());   // ORDER_RECEIPT off
        fiscalised("https://paragony.pl/r/1");

        effects.apply(STORE_ID, KEY);

        verifyNoInteractions(emailClient);
        ReceiptAttempt attempt = attempts.find(STORE_ID, KEY).orElseThrow();
        assertThat(attempt.getEmailClaimedAt()).isNotNull();
        assertThat(attempt.getEmailSkippedAt()).isNotNull();
        assertThat(attempt.getEmailSentAt()).isNull();
        assertThat(ReceiptAttentionEvaluator.evaluate(attempt, clock.instant())).isNull();
        assertThat(ReceiptEffects.pending(attempt)).isFalse();
    }

    @Test
    void aBuyerWithNoEmailSkipsTheEmailQuietly() {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setStoreId(STORE_ID);
        attempt.setReceiptKey(KEY);
        attempt.setOrderId(ORDER_ID);
        attempt.setState(ReceiptAttemptState.FISCALISED);
        attempt.setFiscalisedAt(Instant.parse("2026-09-23T12:00:00Z"));
        attempt.setDocumentUrl("https://paragony.pl/r/1");
        attempt.setRequestSnapshot(ReceiptSnapshotJson.write(new ReceiptRequestSnapshot(ORDER_ID, DELIVERED_AT,
                null, List.of(), List.of())));
        attempts.create(attempt);

        effects.apply(STORE_ID, KEY);

        verifyNoInteractions(emailClient);
        ReceiptAttempt saved = attempts.find(STORE_ID, KEY).orElseThrow();
        assertThat(saved.getEmailClaimedAt()).isNotNull();
        assertThat(saved.getEmailSkippedAt()).isNotNull();
        assertThat(ReceiptAttentionEvaluator.evaluate(saved, clock.instant())).isNull();
    }

    @Test
    void aStaleReadThatConflictsOnSaveIsRetriedWithoutDuplicatingTheDocument() {
        fiscalised("https://paragony.pl/r/1");
        // Simulates an eventually-consistent read right after an earlier, already-persisted attach: the stale
        // order has no Receipt document yet, so saving it (with a stale version) would conflict.
        Order staleOrder = b2cOrder(100);
        Order freshOrder = b2cOrder(100);
        freshOrder.addDocument(new Document(KEY, KEY, "https://paragony.pl/r/1", DocumentType.Receipt, LocalDate.now()));
        when(orders.findById(STORE_ID, ORDER_ID)).thenReturn(staleOrder, freshOrder);
        doThrow(new ConditionalCheckFailedException("stale version"))
                .when(attemptService).saveThroughLifecycle(staleOrder);

        OptimisticLockingExecutor retryingLocking = mock(OptimisticLockingExecutor.class);
        doAnswer(i -> {
            Supplier<Order> loader = (Supplier<Order>) i.getArgument(0);
            Consumer<Order> mutator = (Consumer<Order>) i.getArgument(1);
            Consumer<Order> saver = (Consumer<Order>) i.getArgument(2);
            Order entity = loader.get();
            mutator.accept(entity);
            try {
                saver.accept(entity);
            } catch (ConditionalCheckFailedException e) {
                entity = loader.get();
                mutator.accept(entity);
                saver.accept(entity);
            }
            return entity;
        }).when(retryingLocking).modifyAndSave(any(), any(), any());
        ReceiptEffects retryingEffects = new ReceiptEffects(attempts, orders, retryingLocking, attemptService,
                lifecycleEvents, emailClient, orderEvents, stores, clock);

        retryingEffects.apply(STORE_ID, KEY);

        assertThat(freshOrder.getDocuments()).filteredOn(d -> d.getType() == DocumentType.Receipt).hasSize(1);
        verify(attemptService).saveThroughLifecycle(freshOrder);
    }

    @Test
    void aConcurrentMarketplaceClaimIsNotOverriddenByALoserOfTheSameCasRetry() {
        fiscalised("https://paragony.pl/r/1");
        attempts.update(STORE_ID, KEY, a -> {
            a.setDocumentAttachedAt(clock.instant());
            a.setDocumentLinkedAt(clock.instant());
            a.setEmailClaimedAt(clock.instant());   // keep this run's only claim attempt the marketplace one
            return true;
        });
        Instant otherClaim = Instant.parse("2026-09-23T12:30:00Z");
        attempts.interleaveOnce(a -> a.setMarketplaceNotifiedAt(otherClaim));

        effects.apply(STORE_ID, KEY);

        verify(lifecycleEvents, never()).publish(any(), any());
        assertThat(attempts.find(STORE_ID, KEY).orElseThrow().getMarketplaceNotifiedAt()).isEqualTo(otherClaim);
    }

    @Test
    void aConcurrentEmailClaimIsNotOverriddenByALoserOfTheSameCasRetry() {
        fiscalised("https://paragony.pl/r/1");
        attempts.update(STORE_ID, KEY, a -> {
            a.setDocumentAttachedAt(clock.instant());
            a.setDocumentLinkedAt(clock.instant());
            a.setMarketplaceNotifiedAt(clock.instant());   // keep this run's only claim attempt the e-mail one
            return true;
        });
        Instant otherClaim = Instant.parse("2026-09-23T12:30:00Z");
        attempts.interleaveOnce(a -> a.setEmailClaimedAt(otherClaim));

        effects.apply(STORE_ID, KEY);

        verify(emailClient, never()).send(any(), any(), any());
        assertThat(attempts.find(STORE_ID, KEY).orElseThrow().getEmailClaimedAt()).isEqualTo(otherClaim);
    }

    @Test
    void anOutcomeWriteThatFindsTheClaimClearedByAConcurrentResendReclaimsInsteadOfLeavingTheAttemptUnclaimed() {
        // The lease guard in ReceiptAttemptService.resendEmail is meant to make this impossible, but the outcome
        // write is a second, independent line of defence: it must never leave a sent e-mail's claim cleared, or a
        // later apply() (e.g. the resend's own queued work) would see emailClaimedAt == null and send it again.
        fiscalised("https://paragony.pl/r/1");
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any())).thenAnswer(invocation -> {
            attempts.update(STORE_ID, KEY, a -> {
                a.setEmailClaimedAt(null);   // simulates a resendEmail racing the in-flight send
                return true;
            });
            return true;
        });

        effects.apply(STORE_ID, KEY);

        ReceiptAttempt attempt = attempts.find(STORE_ID, KEY).orElseThrow();
        assertThat(attempt.getEmailSentAt()).isNotNull();
        assertThat(attempt.getEmailClaimedAt()).isNotNull();
        assertThat(ReceiptEffects.pending(attempt)).isFalse();

        effects.apply(STORE_ID, KEY);   // the requeued work from the (hypothetical) racing resend re-enters apply()

        verify(emailClient, times(1)).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_RECEIPT), any());
    }

    @Test
    void attemptsThatAreNotFiscalisedHaveNoEffects() {
        fiscalised("u");
        attempts.update(STORE_ID, KEY, a -> {
            a.setState(ReceiptAttemptState.PENDING);
            return true;
        });

        effects.apply(STORE_ID, KEY);

        verifyNoInteractions(attemptService, lifecycleEvents, emailClient);
    }
}
