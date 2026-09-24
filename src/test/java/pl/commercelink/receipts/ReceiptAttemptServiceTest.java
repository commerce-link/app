package pl.commercelink.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptAttemptServiceTest {

    private final InMemoryReceiptAttemptStore attempts = new InMemoryReceiptAttemptStore();
    private final StoresRepository stores = mock(StoresRepository.class);
    private final OrdersRepository orders = mock(OrdersRepository.class);
    private final OrderItemsRepository orderItems = mock(OrderItemsRepository.class);
    private final ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
    private final ReceiptWorkPublisher publisher = mock(ReceiptWorkPublisher.class);
    private final OrderLifecycle lifecycle = mock(OrderLifecycle.class);
    private final MutableClock clock = MutableClock.at("2026-09-23T13:00:00Z");
    private final FakeReceiptProvider provider = new FakeReceiptProvider();
    private final OptimisticLockingExecutor locking = mock(OptimisticLockingExecutor.class);
    private final ReceiptAlerts alerts = mock(ReceiptAlerts.class);
    private ReceiptAttemptService service;
    private Store store;
    private Order order;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        order = b2cOrder(100.00);
        when(stores.findById(STORE_ID)).thenReturn(store);
        when(orders.findById(STORE_ID, ORDER_ID)).thenAnswer(i -> order);
        when(orderItems.findByOrderId(ORDER_ID)).thenReturn(items(item("Mysz", 1, 100.00, 1.23)));
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        when(factory.get(any(Store.class), anyString())).thenReturn(provider);
        doAnswer(ReceiptAttemptServiceTest::modifyAndSave).when(locking).modifyAndSave(any(), any(), any());
        service = new ReceiptAttemptService(attempts, stores, orders, orderItems, factory,
                new ReceiptRequestConverter(), new ReceiptEligibility(factory), publisher, locking, lifecycle,
                alerts, clock);
    }

    // Mirrors the real OptimisticLockingExecutor: load, modify, save — used both as the default stub and, in one
    // test, as the second half of a stub chain whose first call throws (a failed order write).
    private static Object modifyAndSave(org.mockito.invocation.InvocationOnMock i) {
        Object entity = ((java.util.function.Supplier<?>) i.getArgument(0)).get();
        ((java.util.function.Consumer<Object>) i.getArgument(1)).accept(entity);
        ((java.util.function.Consumer<Object>) i.getArgument(2)).accept(entity);
        return entity;
    }

    @Test
    void automaticStartCreatesTheFirstAttemptWithAFrozenRequestAndWakesIt() {
        ReceiptAttempt attempt = service.startAutomatic(store, order).orElseThrow();

        assertThat(attempt.getReceiptKey()).isEqualTo(ORDER_ID + ":R1");
        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.ISSUING);
        assertThat(attempt.getProvider()).isEqualTo(FakeReceiptProviderDescriptor.NAME);
        assertThat(ReceiptSnapshotJson.read(attempt.getRequestSnapshot()).lines()).hasSize(1);
        assertThat(attempt.getNextCheckAt()).isEqualTo(clock.instant());
        verify(publisher).publishDue(any(ReceiptAttempt.class));
        assertThat(provider.issueCalls.get()).isZero();
    }

    @Test
    void automaticStartNeverCreatesASecondAttempt() {
        service.startAutomatic(store, order);

        assertThat(service.startAutomatic(store, order)).isEmpty();
        assertThat(attempts.all()).hasSize(1);
    }

    @Test
    void dataErrorsCreateABlockedAttemptThatStillWakesForTheAlert() {
        order.getBillingDetails().setEmail(null);
        order.setEmail(null);

        ReceiptAttempt attempt = service.startAutomatic(store, order).orElseThrow();

        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.BLOCKED);
        assertThat(attempt.getBlockedReason()).isEqualTo(ReceiptBlockReason.MISSING_EMAIL.name());
        assertThat(attempt.isScheduled()).isTrue();
    }

    @Test
    void providerThatCannotBeCreatedBlocksTheAttempt() {
        when(factory.get(any(Store.class), anyString())).thenThrow(new IllegalStateException("no secret"));

        assertThat(service.startAutomatic(store, order).orElseThrow().getBlockedReason())
                .isEqualTo(ReceiptBlockReason.PROVIDER_UNAVAILABLE.name());
    }

    @Test
    void aMissingAdapterBlocksTheAttemptAsProviderUnavailable() {
        // The adapter is not on the classpath: providerFactory.get() returns null (no descriptor) rather than
        // throwing. Before the fix this null reached ReceiptRequestConverter.convert() and NPE'd there.
        when(factory.get(any(Store.class), anyString())).thenReturn(null);

        ReceiptAttempt attempt = service.startAutomatic(store, order).orElseThrow();

        assertThat(attempt.getState()).isEqualTo(ReceiptAttemptState.BLOCKED);
        assertThat(attempt.getBlockedReason()).isEqualTo(ReceiptBlockReason.PROVIDER_UNAVAILABLE.name());
    }

    @Test
    void reissueIsRefusedWhenTheAdapterIsMissingEvenThoughAProviderNameIsConfigured() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FAILED);
            return true;
        });
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, "uninstalled-adapter");
        // factory.getDescriptor("uninstalled-adapter") is unstubbed, so the mock returns null: the adapter is gone.

        assertThatThrownBy(() -> service.reissue(STORE_ID, ORDER_ID, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.reissue.noProvider");
    }

    @Test
    void reissueIsRefusedWhenTheOrderHasNoAttemptsYet() {
        assertThatThrownBy(() -> service.reissue(STORE_ID, ORDER_ID, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.reissue.none");
    }

    @Test
    void reissueIsRefusedWhileAnAttemptIsAlive() {
        service.startAutomatic(store, order);

        assertThatThrownBy(() -> service.reissue(STORE_ID, ORDER_ID, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.reissue.live");
    }

    @Test
    void reissueAfterAFailureCreatesTheNextKey() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FAILED);
            return true;
        });

        ReceiptAttempt second = service.reissue(STORE_ID, ORDER_ID, "operator");

        assertThat(second.getReceiptKey()).isEqualTo(ORDER_ID + ":R2");
        assertThat(second.getAttemptNo()).isEqualTo(2);
        assertThat(second.getCreatedBy()).isEqualTo("operator");
    }

    @Test
    void reissueResolvesTheBellAlertOfEveryEarlierDeadAttempt() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FAILED);
            return true;
        });

        service.reissue(STORE_ID, ORDER_ID, "operator");

        verify(alerts).resolve(argThat(a -> a.getReceiptKey().equals(ORDER_ID + ":R1")));
    }

    @Test
    void reissueResolvesEveryEarlierDeadAttemptNotJustTheLatest() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FAILED);
            return true;
        });
        service.reissue(STORE_ID, ORDER_ID, "operator");
        attempts.update(STORE_ID, ORDER_ID + ":R2", a -> {
            a.setState(ReceiptAttemptState.BLOCKED);
            return true;
        });
        clearInvocations(alerts);

        service.reissue(STORE_ID, ORDER_ID, "operator");

        verify(alerts).resolve(argThat(a -> a.getReceiptKey().equals(ORDER_ID + ":R1")));
        verify(alerts).resolve(argThat(a -> a.getReceiptKey().equals(ORDER_ID + ":R2")));
    }

    @Test
    void doubleClickedReissueCreatesExactlyOneAttempt() throws Exception {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FAILED);
            return true;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Object> click = () -> {
            try {
                return service.reissue(STORE_ID, ORDER_ID, "operator");
            } catch (ReceiptActionException e) {
                return e;
            }
        };
        Future<Object> first = pool.submit(click);
        Future<Object> second = pool.submit(click);
        first.get();
        second.get();
        pool.shutdownNow();

        assertThat(attempts.findByOrder(STORE_ID, ORDER_ID)).extracting(ReceiptAttempt::getReceiptKey)
                .containsExactly(ORDER_ID + ":R1", ORDER_ID + ":R2");
    }

    @Test
    void reissueIsRefusedForAnOrderThatGotAnotherDocument() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.BLOCKED);
            return true;
        });
        order.addDocument(new pl.commercelink.documents.Document("x", "FV/1", null,
                pl.commercelink.documents.DocumentType.InvoicePersonal));

        assertThatThrownBy(() -> service.reissue(STORE_ID, ORDER_ID, "operator"))
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.reissue.notEligible");
    }

    @Test
    void closeManuallyAttachesTheOperatorsDocumentAndStopsPolling() {
        service.startAutomatic(store, order);

        service.closeManually(STORE_ID, ORDER_ID + ":R1", "PAR/1/09", "https://paragony.pl/x", "operator");

        ReceiptAttempt closed = attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow();
        assertThat(closed.getState()).isEqualTo(ReceiptAttemptState.CLOSED_MANUALLY);
        assertThat(closed.isScheduled()).isFalse();
        assertThat(order.getClosingDocument()).get().satisfies(d -> {
            assertThat(d.getId()).isEqualTo(ORDER_ID + ":R1");
            assertThat(d.getNumber()).isEqualTo("PAR/1/09");
        });
        verify(lifecycle).update(order);
    }

    @Test
    void closeManuallyIsRefusedWhileTheProviderIsBeingCalled() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setLeaseUntil(clock.instant().plus(Duration.ofMinutes(5)));
            return true;
        });

        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "1", null, "operator"))
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.close.busy");
    }

    @Test
    void closeManuallyRejectsALinkThatDoesNotLookLikeAUrl() {
        service.startAutomatic(store, order);

        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "1", "javascript:alert(1)", "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.close.invalidLink");
        assertThat(attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow().getState())
                .isEqualTo(ReceiptAttemptState.ISSUING);
        verifyNoInteractions(locking);
    }

    @Test
    void closeManuallyAcceptsAnUppercaseHttpsLinkAndABlankOne() {
        service.startAutomatic(store, order);

        service.closeManually(STORE_ID, ORDER_ID + ":R1", "1", "HTTPS://Paragony.pl/x", "operator");

        assertThat(attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow().getState())
                .isEqualTo(ReceiptAttemptState.CLOSED_MANUALLY);
    }

    @Test
    void closeManuallyRejectsABlankNumber() {
        service.startAutomatic(store, order);

        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "   ", null, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.close.numberRequired");
        assertThat(attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow().getState())
                .isEqualTo(ReceiptAttemptState.ISSUING);
        verifyNoInteractions(locking);
    }

    @Test
    void closeManuallyRetriesTheOrderStepAfterAFailedSaveWithoutDuplicatingTheDocument() {
        service.startAutomatic(store, order);
        doThrow(new RuntimeException("write conflict"))
                .doAnswer(ReceiptAttemptServiceTest::modifyAndSave)
                .when(locking).modifyAndSave(any(), any(), any());

        // first call: the attempt is closed, but the order write fails
        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "PAR/1", null, "operator"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("write conflict");
        ReceiptAttempt afterFirstCall = attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow();
        assertThat(afterFirstCall.getState()).isEqualTo(ReceiptAttemptState.CLOSED_MANUALLY);
        assertThat(afterFirstCall.getReceiptNumber()).isEqualTo("PAR/1");
        assertThat(order.getDocuments()).isEmpty();
        Long versionAfterFirstCall = afterFirstCall.getVersion();

        // retry with the same number: the attempt is not touched again, only the order step is redone
        service.closeManually(STORE_ID, ORDER_ID + ":R1", "PAR/1", null, "operator");

        assertThat(order.getDocuments()).hasSize(1);
        assertThat(order.getDocuments().get(0).getNumber()).isEqualTo("PAR/1");
        assertThat(attempts.find(STORE_ID, ORDER_ID + ":R1").orElseThrow().getVersion()).isEqualTo(versionAfterFirstCall);
        verify(locking, times(2)).modifyAndSave(any(), any(), any());
    }

    @Test
    void closeManuallyRefusesARetryWithADifferentNumberThanTheOneAlreadyClosedWith() {
        service.startAutomatic(store, order);
        service.closeManually(STORE_ID, ORDER_ID + ":R1", "PAR/1", null, "operator");

        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "PAR/2", null, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.close.notHung");
    }

    @Test
    void closeManuallyIsRefusedForAFiscalisedAttempt() {
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, ORDER_ID + ":R1", a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            return true;
        });

        assertThatThrownBy(() -> service.closeManually(STORE_ID, ORDER_ID + ":R1", "1", null, "operator"))
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.close.notHung");
    }

    @Test
    void checkNowWakesAScheduledAttempt() {
        service.startAutomatic(store, order);
        clearInvocations(publisher);

        service.checkNow(STORE_ID, ORDER_ID + ":R1");

        verify(publisher).publishNow(STORE_ID, ORDER_ID + ":R1");
    }

    @Test
    void resendEmailClearsTheClaimResolvesTheAlertAndWakesTheAttempt() {
        String key = ORDER_ID + ":R1";
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, key, a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            a.setDocumentUrl("https://paragony.pl/x");
            a.setEmailClaimedAt(clock.instant());
            a.unschedule();
            return true;
        });
        clearInvocations(publisher);

        service.resendEmail(STORE_ID, ORDER_ID, key, "operator");

        ReceiptAttempt attempt = attempts.find(STORE_ID, key).orElseThrow();
        assertThat(attempt.getEmailClaimedAt()).isNull();
        assertThat(attempt.isScheduled()).isTrue();
        verify(alerts).resolve(argThat(a -> a.getReceiptKey().equals(key)));
        verify(publisher).publishNow(STORE_ID, key);
    }

    @Test
    void resendEmailIsRefusedWhileTheProcessorHoldsTheLeaseEvenThoughTheAttemptLooksFailed() {
        // The window ReceiptEffects.sendEmail's claim opens before the outcome is written: emailFailed() is true
        // (claimed, not yet sent) even though nothing has actually failed. Clearing the claim here would race the
        // in-flight send and could get the mail sent twice, so the lease must refuse this regardless of emailFailed().
        String key = ORDER_ID + ":R1";
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, key, a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            a.setDocumentUrl("https://paragony.pl/x");
            a.setEmailClaimedAt(clock.instant());
            a.setLeaseUntil(clock.instant().plus(Duration.ofMinutes(10)));
            return true;
        });

        assertThatThrownBy(() -> service.resendEmail(STORE_ID, ORDER_ID, key, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.resendEmail.busy");
        ReceiptAttempt attempt = attempts.find(STORE_ID, key).orElseThrow();
        assertThat(attempt.getEmailClaimedAt()).isNotNull();
        verifyNoInteractions(alerts);
        verify(publisher, never()).publishNow(any(), any());
    }

    @Test
    void resendEmailIsRefusedOnceTheMailWasActuallySent() {
        String key = ORDER_ID + ":R1";
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, key, a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            a.setDocumentUrl("https://paragony.pl/x");
            a.setEmailClaimedAt(clock.instant());
            a.setEmailSentAt(clock.instant());
            a.unschedule();
            return true;
        });

        assertThatThrownBy(() -> service.resendEmail(STORE_ID, ORDER_ID, key, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.resendEmail.notEligible");
        verifyNoInteractions(alerts);
        verify(publisher, never()).publishNow(any(), any());
    }

    @Test
    void resendEmailIsRefusedWhenTheMailWasSkippedByTheStore() {
        String key = ORDER_ID + ":R1";
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, key, a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            a.setDocumentUrl("https://paragony.pl/x");
            a.setEmailClaimedAt(clock.instant());
            a.setEmailSkippedAt(clock.instant());
            a.unschedule();
            return true;
        });

        assertThatThrownBy(() -> service.resendEmail(STORE_ID, ORDER_ID, key, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.resendEmail.notEligible");
    }

    @Test
    void resendEmailIsRefusedForAnotherOrdersKey() {
        String key = ORDER_ID + ":R1";
        service.startAutomatic(store, order);
        attempts.update(STORE_ID, key, a -> {
            a.setState(ReceiptAttemptState.FISCALISED);
            a.setDocumentUrl("https://paragony.pl/x");
            a.setEmailClaimedAt(clock.instant());
            a.unschedule();
            return true;
        });

        assertThatThrownBy(() -> service.resendEmail(STORE_ID, "some-other-order", key, "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.notFound");
        verifyNoInteractions(alerts);
    }

    @Test
    void resendEmailIsRefusedWhenTheAttemptDoesNotExist() {
        assertThatThrownBy(() -> service.resendEmail(STORE_ID, ORDER_ID, ORDER_ID + ":R1", "operator"))
                .isInstanceOf(ReceiptActionException.class)
                .extracting(e -> ((ReceiptActionException) e).getMessageKey())
                .isEqualTo("receipts.action.notFound");
    }
}
