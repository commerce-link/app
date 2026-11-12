package pl.commercelink.products.information;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asks the PIM for a product's entry from two sources: a product saved in a catalog, and a product sold in an
 * order. Requests go out with the highest priority, as for a managed catalog.
 * <p>
 * A catalog product the PIM already knows by manufacturer code and brand is skipped: a request would change
 * nothing, because {@code PimCatalogRegistry.attachEntryToProducts} pins entries to products by EAN only.
 * That rule belongs to the catalog only; an order EAN is checked by the EAN alone, and requested with the EAN alone.
 * Does nothing outside {@code application.env=prod}. A failure never reaches the caller.
 * <p>
 * Both sources are asynchronous. Every request is a blocking SQS send with no time limit of its own, so on the
 * request thread an SQS degradation would hold the operator's save until the SDK gives up (two minutes measured
 * locally, longer than the load balancer timeout), and thousands of products in a bulk add could exceed that
 * timeout even when SQS is healthy. A single product and a whole batch are handed alike to a single daemon
 * worker (batches stay ordered and the PIM is not hit in parallel) with a bounded queue of
 * {@value #QUEUE_CAPACITY} batches, shared by both sources.
 * <p>
 * The periodic {@link PIMIndexEventScheduler} sweep is the safety net for catalog products only: a catalog batch
 * that finds the queue full is dropped with a warning and the sweeper sends those products later. Nothing re-sends
 * a request for an order EAN, so a dropped order batch is logged with its EANs, the only trace from which they can
 * be requested by hand.
 */
@Component
@Slf4j
public class PimEntryRequester {

    private static final int QUEUE_CAPACITY = 100;
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final String env;
    private final Executor executor;
    private final Duration shutdownWait;
    private volatile Batch<?> inProgress;

    @Autowired
    public PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, @Value("${application.env:}") String env) {
        this(pimCatalog, brandMapper, env, newWorker(), SHUTDOWN_WAIT);
    }

    PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, String env, Executor executor) {
        this(pimCatalog, brandMapper, env, executor, SHUTDOWN_WAIT);
    }

    PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, String env, Executor executor,
                      Duration shutdownWait) {
        this.pimCatalog = pimCatalog;
        this.brandMapper = brandMapper;
        this.env = env;
        this.executor = executor;
        this.shutdownWait = shutdownWait;
    }

    static ThreadPoolExecutor newWorker() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "pim-entry-requester");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Spring destroys this bean before the PIM catalog and the SQS client it depends on, so the queued batches
     * can still be sent while waiting here. What is left after the wait is reported and left to the sweeper.
     */
    @PreDestroy
    void shutdown() {
        if (!(executor instanceof ExecutorService service)) {
            return;
        }
        service.shutdown();
        try {
            if (service.awaitTermination(shutdownWait.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<Batch<?>> unsent = new ArrayList<>();
        for (Runnable task : service.shutdownNow()) {
            if (task instanceof Batch<?> batch) {
                unsent.add(batch);
            }
        }
        // the interrupt stops the running batch after its current send, which itself cannot be interrupted
        Batch<?> running = inProgress;
        if (running != null) {
            unsent.add(running);
        }
        int catalogProducts = 0;
        List<String> orderEans = new ArrayList<>();
        for (Batch<?> batch : unsent) {
            if (batch instanceof OrderBatch order) {
                orderEans.addAll(order.unfinishedItems());
            } else {
                catalogProducts += batch.unfinishedItems().size();
            }
        }
        if (catalogProducts > 0 || orderEans.isEmpty()) {
            log.warn("PIM entry requests not sent before shutdown: {} products left to the sweeper", catalogProducts);
        }
        if (!orderEans.isEmpty()) {
            log.warn("PIM entry requests not sent before shutdown: order EANs {} not requested; nothing will resend them",
                    orderEans);
        }
    }

    /** Returns without waiting for the send; see the class comment for why. */
    public void requestMissingEntry(String storeId, Product product) {
        requestMissingEntries(storeId, List.of(product));
    }

    /**
     * Returns without waiting for the sends; see the class comment for why and for what happens when the queue is full.
     */
    public void requestMissingEntries(String storeId, Collection<Product> products) {
        if (!"prod".equals(env)) {
            return;
        }
        // checked here, not on the worker: a product that needs no request must not take a place in the queue
        List<Product> pending = products.stream().filter(PimEntryRequester::isPending).toList();
        if (pending.isEmpty()) {
            return;
        }
        hand(new CatalogBatch(storeId, pending));
    }

    /**
     * Asks the PIM for the entries of products sold in an order, by EAN alone; the EANs are expected to be valid
     * already. Returns without waiting for the sends and never throws. Unlike a catalog product, a dropped request
     * is not sent again by anything, so every failure is logged with the EANs.
     */
    public void requestMissingEntriesByEan(String storeId, Collection<String> eans) {
        if (!"prod".equals(env)) {
            return;
        }
        List<String> pending = eans.stream().filter(StringUtils::isNotBlank).toList();
        if (pending.isEmpty()) {
            return;
        }
        hand(new OrderBatch(storeId, pending));
    }

    private void hand(Batch<?> batch) {
        try {
            executor.execute(batch);
        } catch (RejectedExecutionException e) {
            batch.logRejected(executor instanceof ExecutorService service && service.isShutdown());
        }
    }

    private static boolean isPending(Product product) {
        return StringUtils.isBlank(product.getPimId()) && StringUtils.isNotBlank(product.getEan());
    }

    /** A named task rather than a lambda, so {@link #shutdown()} can list the items it leaves unsent. */
    private abstract class Batch<T> implements Runnable {
        final String storeId;
        private final List<T> items;
        private final AtomicInteger started = new AtomicInteger();

        Batch(String storeId, List<T> items) {
            this.storeId = storeId;
            this.items = items;
        }

        abstract void send(T item);

        abstract void logRejected(boolean shuttingDown);

        List<T> items() {
            return items;
        }

        @Override
        public void run() {
            inProgress = this;
            try {
                for (T item : items) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    started.incrementAndGet();
                    send(item);
                }
            } finally {
                inProgress = null;
            }
        }

        /** The item being sent counts as unfinished: its send may still fail after the warning is logged. */
        List<T> unfinishedItems() {
            return items.subList(Math.max(0, started.get() - 1), items.size());
        }
    }

    private final class CatalogBatch extends Batch<Product> {
        CatalogBatch(String storeId, List<Product> products) {
            super(storeId, products);
        }

        @Override
        void send(Product product) {
            sendIfMissing(storeId, product);
        }

        @Override
        void logRejected(boolean shuttingDown) {
            if (shuttingDown) {
                log.warn("Application is shutting down, {} products of store {} left to the sweeper for a PIM entry request",
                        items().size(), storeId);
            } else {
                log.warn("PIM entry request queue is full, dropping a batch of {} products for store {}; the sweeper will send them",
                        items().size(), storeId);
            }
        }
    }

    private final class OrderBatch extends Batch<String> {
        OrderBatch(String storeId, List<String> eans) {
            super(storeId, eans);
        }

        @Override
        void send(String ean) {
            sendOrderEanIfMissing(storeId, ean);
        }

        @Override
        void logRejected(boolean shuttingDown) {
            if (shuttingDown) {
                log.warn("Application is shutting down, order EANs {} of store {} not requested from PIM; nothing will resend them",
                        items(), storeId);
            } else {
                log.warn("PIM entry request queue is full, dropping order EANs {} of store {}; nothing will resend them",
                        items(), storeId);
            }
        }
    }

    private void sendOrderEanIfMissing(String storeId, String ean) {
        try {
            if (pimCatalog.withStoreData(storeId).findByGtin(ean).isPresent()) {
                return;
            }
            pimCatalog.submit(new PimEntryRequest(ean, null, null, PimQueuePriorityCalculator.MAX_VALUE));
        } catch (RuntimeException e) {
            log.warn("Could not request PIM entry for store {}, order EAN {}: {}",
                    storeId, ean, ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private void sendIfMissing(String storeId, Product product) {
        try {
            PimView pim = pimCatalog.withStoreData(storeId);
            if (isKnown(pim, brandMapper, product)) {
                return;
            }
            pimCatalog.submit(new PimEntryRequest(product.getEan(), product.getManufacturerCode(),
                    product.getBrand(), PimQueuePriorityCalculator.MAX_VALUE));
        } catch (RuntimeException e) {
            // root cause only: the adapter wraps every SQS error alike, and a stack trace per product would flood
            // the log during an SQS outage on a large batch
            log.warn("Could not request PIM entry for store {}, category {}, EAN {}: {}",
                    storeId, product.getCategoryId(), product.getEan(), ExceptionUtils.getRootCauseMessage(e));
        }
    }

    static boolean isKnown(PimView pim, BrandMapper brandMapper, Product product) {
        if (StringUtils.isBlank(product.getBrand())) {
            return pim.findByGtin(product.getEan()).isPresent();
        }
        // pim-api compares brands without aliases, and PIM stores the unified name
        return pim.findByGtinOrMpnAndBrand(product.getEan(), product.getManufacturerCode(),
                brandMapper.unifyBrand(product.getBrand())).isPresent();
    }
}
