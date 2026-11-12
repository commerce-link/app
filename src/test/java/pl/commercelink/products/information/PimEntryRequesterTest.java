package pl.commercelink.products.information;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PimEntryRequesterTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "4719331361699";
    private static final String SECOND_EAN = "5901234567890";
    private static final String THIRD_EAN = "5901234567891";
    private static final String ORDER_EAN = "5901234123457";
    private static final String SECOND_ORDER_EAN = "4006381333931";

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private PimView pimView;

    @Mock
    private BrandMapper brandMapper;

    @BeforeEach
    void setUp() {
        when(pimCatalog.withStoreData(any())).thenReturn(pimView);
        when(brandMapper.unifyBrand(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(pimView.findByGtin(any())).thenReturn(Optional.empty());
        when(pimView.findByGtinOrMpnAndBrand(any(), any(), any())).thenReturn(Optional.empty());
    }

    private PimEntryRequester requester(String env) {
        return new PimEntryRequester(pimCatalog, brandMapper, env, Runnable::run);
    }

    private static class RecordingExecutor implements Executor {
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }

    private static Product product(String pimId, String ean, String mfn, String brand) {
        return new Product("cat-1", pimId, ean, mfn, brand, "l", "MSI RTX 5070", "Default");
    }

    @Test
    void pendingProductIsRequestedWithTheManagedPriority() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
    }

    @Test
    void productWithAPimIdIsNotRequested() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product("pim-1", EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void productWithoutAnEanIsNotRequested(String ean) {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, ean, "MFN-1", null));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void productThePimKnowsByManufacturerCodeAndBrandIsNotRequested() {
        // given
        when(pimView.findByGtinOrMpnAndBrand(EAN, "MFN-1", "MSI")).thenReturn(Optional.of(mock(PimEntry.class)));

        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void productWithoutABrandIsCheckedByEanOnlyAndRequested() {
        // when
        requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", null));

        // then
        verify(pimView).findByGtin(EAN);
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", null, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "test"})
    void nothingIsAskedOfThePimOutsideProd(String env) {
        // when
        requester(env).requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verifyNoInteractions(pimCatalog);
    }

    @Test
    void failedSubmitDoesNotReachTheCaller() {
        // given
        doThrow(new IllegalStateException("SQS not configured")).when(pimCatalog).submit(any());

        // when / then
        assertThatCode(() -> requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI")))
                .doesNotThrowAnyException();
    }

    @Test
    void failureForOneProductDoesNotStopTheRest() {
        // given
        PimEntryRequest second = new PimEntryRequest("5901234567890", "MFN-2", "ASUS", 100);
        doThrow(new IllegalStateException("boom")).when(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));

        // when
        requester("prod").requestMissingEntries(STORE_ID, List.of(
                product(null, EAN, "MFN-1", "MSI"), product(null, "5901234567890", "MFN-2", "ASUS")));

        // then
        verify(pimCatalog).submit(second);
    }

    @Test
    void failedLookupDoesNotReachTheCaller() {
        // given
        when(pimCatalog.withStoreData(STORE_ID)).thenThrow(new IllegalStateException("index not loaded"));

        // when / then
        assertThatCode(() -> requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI")))
                .doesNotThrowAnyException();
    }

    @Test
    void bulkRequestsAreHandedToTheExecutorAndNotSentOnTheCallersThread() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);

        // when
        requester.requestMissingEntries(STORE_ID, List.of(
                product(null, EAN, "MFN-1", "MSI"), product(null, "5901234567890", "MFN-2", "ASUS")));

        // then
        verify(pimCatalog, never()).submit(any());
        assertThat(executor.tasks).hasSize(1);

        // when
        executor.tasks.get(0).run();

        // then
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
        verify(pimCatalog).submit(new PimEntryRequest("5901234567890", "MFN-2", "ASUS", 100));
    }

    @Test
    void nothingIsHandedToTheExecutorOutsideProd() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "localhost", executor);

        // when
        requester.requestMissingEntries(STORE_ID, List.of(product(null, EAN, "MFN-1", "MSI")));

        // then
        assertThat(executor.tasks).isEmpty();
        verifyNoInteractions(pimCatalog);
    }

    @Test
    void emptyBatchIsNotHandedToTheExecutor() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);

        // when
        requester.requestMissingEntries(STORE_ID, List.of());

        // then
        assertThat(executor.tasks).isEmpty();
    }

    @Test
    void batchRejectedByTheExecutorDoesNotReachTheCaller() {
        // given
        Executor rejecting = task -> {
            throw new RejectedExecutionException("queue full");
        };
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", rejecting);

        // when / then
        assertThatCode(() -> requester.requestMissingEntries(STORE_ID, List.of(product(null, EAN, "MFN-1", "MSI"))))
                .doesNotThrowAnyException();
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void laterChangesToTheCallersListDoNotAffectTheBatch() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);
        List<Product> products = new ArrayList<>(List.of(product(null, EAN, "MFN-1", "MSI")));

        // when
        requester.requestMissingEntries(STORE_ID, products);
        products.clear();
        executor.tasks.get(0).run();

        // then
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
    }

    @Test
    void singleProductIsHandedToTheExecutorAndNotSentOnTheCallersThread() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);

        // when
        requester.requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

        // then
        verify(pimCatalog, never()).submit(any());
        assertThat(executor.tasks).hasSize(1);

        // when
        executor.tasks.get(0).run();

        // then
        verify(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
    }

    @Test
    void failedSubmitIsLoggedWithItsRootCause() {
        // given
        doThrow(new RuntimeException("Failed to submit PIM entry request",
                new IllegalStateException("The specified queue does not exist"))).when(pimCatalog).submit(any());

        // when
        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            requester("prod").requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));

            // then
            assertThat(logs.warnings()).singleElement().asString()
                    .contains(EAN)
                    .contains("IllegalStateException: The specified queue does not exist");
        }
    }

    @Test
    void productsThatNeedNoRequestDoNotTakeAPlaceInTheQueue() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);

        // when
        requester.requestMissingEntry(STORE_ID, product("pim-1", EAN, "MFN-1", "MSI"));
        requester.requestMissingEntries(STORE_ID, List.of(product("pim-1", EAN, "MFN-1", "MSI"),
                product(null, " ", "MFN-2", "ASUS"), product(null, SECOND_EAN, "MFN-3", "ASUS")));

        // then
        assertThat(executor.tasks).hasSize(1);

        // when
        executor.tasks.get(0).run();

        // then
        verify(pimCatalog).submit(new PimEntryRequest(SECOND_EAN, "MFN-3", "ASUS", 100));
        verify(pimCatalog).submit(any());
    }

    @Test
    void shutdownWaitsForTheBatchesStillQueued() throws InterruptedException {
        // given
        CompletableFuture<Void> sqs = new CompletableFuture<>();
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        blockSendOf(EAN, firstSendStarted, sqs);
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", PimEntryRequester.newWorker());
        requester.requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));
        requester.requestMissingEntry(STORE_ID, product(null, SECOND_EAN, "MFN-2", "ASUS"));
        assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread shutdown = new Thread(requester::shutdown);

        // when
        shutdown.start();
        shutdown.join(200);
        boolean stillWaitingWhileSqsHangs = shutdown.isAlive();
        sqs.complete(null);
        shutdown.join(5000);

        // then
        assertThat(stillWaitingWhileSqsHangs).isTrue();
        assertThat(shutdown.isAlive()).isFalse();
        verify(pimCatalog).submit(new PimEntryRequest(SECOND_EAN, "MFN-2", "ASUS", 100));
    }

    @Test
    void shutdownReportsTheProductsTheWaitLeftUnsentAndStopsTheRunningBatch() throws InterruptedException {
        // given
        CompletableFuture<Void> sqs = new CompletableFuture<>();
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        blockSendOf(EAN, firstSendStarted, sqs);
        ThreadPoolExecutor worker = PimEntryRequester.newWorker();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", worker, Duration.ofMillis(50));
        requester.requestMissingEntries(STORE_ID, List.of(
                product(null, EAN, "MFN-1", "MSI"), product(null, SECOND_EAN, "MFN-2", "ASUS")));
        requester.requestMissingEntry(STORE_ID, product(null, THIRD_EAN, "MFN-3", "MSI"));
        assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            // when
            requester.shutdown();
            sqs.complete(null);

            // then
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(logs.warnings()).singleElement().asString().contains("3 products left to the sweeper");
        }
        verify(pimCatalog, never()).submit(new PimEntryRequest(SECOND_EAN, "MFN-2", "ASUS", 100));
        verify(pimCatalog, never()).submit(new PimEntryRequest(THIRD_EAN, "MFN-3", "MSI", 100));
    }

    /** A send that hangs like the adapter's {@code join()} on a stuck SQS: an interrupt does not end it. */
    private void blockSendOf(String ean, CountDownLatch started, CompletableFuture<Void> sqs) {
        doAnswer(invocation -> {
            started.countDown();
            sqs.join();
            return null;
        }).when(pimCatalog).submit(new PimEntryRequest(ean, "MFN-1", "MSI", 100));
    }

    @Test
    void requestAfterShutdownIsLeftToTheSweeperAndDoesNotReachTheCaller() {
        // given
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", PimEntryRequester.newWorker());
        requester.shutdown();

        // when / then
        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            assertThatCode(() -> requester.requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI")))
                    .doesNotThrowAnyException();
            assertThat(logs.warnings()).singleElement().asString().contains("shutting down");
        }
        verify(pimCatalog, never()).submit(any());
    }

    private static PimEntryRequest orderRequest(String ean) {
        return new PimEntryRequest(ean, null, null, 100);
    }

    @Test
    void unknownOrderEanIsRequestedWithTheEanAlone() {
        // when
        requester("prod").requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN));

        // then
        verify(pimCatalog).submit(orderRequest(ORDER_EAN));
    }

    @Test
    void orderEanThePimKnowsIsNotRequested() {
        // given
        when(pimView.findByGtin(ORDER_EAN)).thenReturn(Optional.of(mock(PimEntry.class)));

        // when
        requester("prod").requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN));

        // then
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void orderEansAskNothingOfThePimOutsideProd() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "localhost", executor);

        // when
        requester.requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN));

        // then
        assertThat(executor.tasks).isEmpty();
        verifyNoInteractions(pimCatalog);
    }

    @Test
    void blankOrderEansAreDroppedAndAnEmptyBatchIsNotHandedToTheExecutor() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);

        // when
        requester.requestMissingEntriesByEan(STORE_ID, Arrays.asList(null, "", "  "));

        // then
        assertThat(executor.tasks).isEmpty();
    }

    @Test
    void orderEansAreHandedToTheExecutorAndNotSentOnTheCallersThread() {
        // given
        RecordingExecutor executor = new RecordingExecutor();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", executor);
        List<String> eans = new ArrayList<>(List.of(ORDER_EAN, SECOND_ORDER_EAN));

        // when
        requester.requestMissingEntriesByEan(STORE_ID, eans);
        eans.clear();

        // then
        verify(pimCatalog, never()).submit(any());
        assertThat(executor.tasks).hasSize(1);

        // when
        executor.tasks.get(0).run();

        // then
        verify(pimCatalog).submit(orderRequest(ORDER_EAN));
        verify(pimCatalog).submit(orderRequest(SECOND_ORDER_EAN));
    }

    @Test
    void failureForOneOrderEanDoesNotStopTheRest() {
        // given
        doThrow(new IllegalStateException("boom")).when(pimCatalog).submit(orderRequest(ORDER_EAN));

        // when / then
        assertThatCode(() -> requester("prod").requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN, SECOND_ORDER_EAN)))
                .doesNotThrowAnyException();
        verify(pimCatalog).submit(orderRequest(SECOND_ORDER_EAN));
    }

    @Test
    void failedOrderRequestIsLoggedWithItsEanAndWithoutACategory() {
        // given
        doThrow(new IllegalStateException("boom")).when(pimCatalog).submit(orderRequest(ORDER_EAN));

        // when
        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            requester("prod").requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN));

            // then
            assertThat(logs.warnings()).singleElement().asString()
                    .contains("order EAN " + ORDER_EAN)
                    .doesNotContain("category");
        }
    }

    @Test
    void orderBatchRejectedByAFullQueueIsLoggedWithItsEansAndPromisesNoSweeper() {
        // given
        Executor rejecting = task -> {
            throw new RejectedExecutionException("queue full");
        };
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", rejecting);

        // when / then
        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            assertThatCode(() -> requester.requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN, SECOND_ORDER_EAN)))
                    .doesNotThrowAnyException();
            assertThat(logs.warnings()).singleElement().asString()
                    .contains(ORDER_EAN)
                    .contains(SECOND_ORDER_EAN)
                    .contains("nothing will resend them")
                    .doesNotContain("sweeper");
        }
    }

    @Test
    void orderBatchAfterShutdownIsLoggedWithItsEansAndPromisesNoSweeper() {
        // given
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", PimEntryRequester.newWorker());
        requester.shutdown();

        // when / then
        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            assertThatCode(() -> requester.requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN)))
                    .doesNotThrowAnyException();
            assertThat(logs.warnings()).singleElement().asString()
                    .contains("shutting down")
                    .contains(ORDER_EAN)
                    .doesNotContain("sweeper");
        }
        verify(pimCatalog, never()).submit(any());
    }

    @Test
    void shutdownReportsUnsentOrderEansOnTheirOwnLine() throws InterruptedException {
        // given
        CompletableFuture<Void> sqs = new CompletableFuture<>();
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstSendStarted.countDown();
            sqs.join();
            return null;
        }).when(pimCatalog).submit(new PimEntryRequest(EAN, "MFN-1", "MSI", 100));
        ThreadPoolExecutor worker = PimEntryRequester.newWorker();
        PimEntryRequester requester = new PimEntryRequester(pimCatalog, brandMapper, "prod", worker, Duration.ofMillis(50));
        requester.requestMissingEntry(STORE_ID, product(null, EAN, "MFN-1", "MSI"));
        requester.requestMissingEntriesByEan(STORE_ID, List.of(ORDER_EAN));
        assertThat(firstSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

        try (CapturedLogs logs = CapturedLogs.of(PimEntryRequester.class)) {
            // when
            requester.shutdown();
            sqs.complete(null);

            // then
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(logs.warnings()).hasSize(2);
            assertThat(logs.warnings().get(0)).asString().contains("1 products left to the sweeper");
            assertThat(logs.warnings().get(1)).asString()
                    .contains("order EANs")
                    .contains(ORDER_EAN)
                    .doesNotContain("sweeper");
        }
        verify(pimCatalog, never()).submit(orderRequest(ORDER_EAN));
    }
}
