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
        OptimisticLockingExecutor locking = mock(OptimisticLockingExecutor.class);
        doAnswer(i -> {
            Object entity = ((java.util.function.Supplier<?>) i.getArgument(0)).get();
            ((java.util.function.Consumer<Object>) i.getArgument(1)).accept(entity);
            ((java.util.function.Consumer<Object>) i.getArgument(2)).accept(entity);
            return entity;
        }).when(locking).modifyAndSave(any(), any(), any());
        service = new ReceiptAttemptService(attempts, stores, orders, orderItems, factory,
                new ReceiptRequestConverter(), new ReceiptEligibility(factory), publisher, locking, lifecycle, clock);
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
}
