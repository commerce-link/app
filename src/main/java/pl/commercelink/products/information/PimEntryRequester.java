package pl.commercelink.products.information;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
 * Asks the PIM for a product's entry right after the product is saved in a catalog, instead of
 * waiting for the periodic {@link PIMIndexEventScheduler} sweep, which stays the safety net for
 * anything missed here. Requests go out with the highest priority, as for a managed catalog.
 * <p>
 * A product the PIM already knows by manufacturer code and brand is skipped: a request would change
 * nothing, because {@code PimCatalogRegistry.attachEntryToProducts} pins entries to products by EAN only.
 * Does nothing outside {@code application.env=prod}. A failure never reaches the caller.
 * <p>
 * Both paths are asynchronous. Every request is a blocking SQS send with no time limit of its own, so on the
 * request thread an SQS degradation would hold the operator's save until the SDK gives up (two minutes measured
 * locally, longer than the load balancer timeout), and thousands of products in a bulk add could exceed that
 * timeout even when SQS is healthy. A single product and a whole batch are handed alike to a single daemon
 * worker (batches stay ordered and the PIM is not hit in parallel) with a bounded queue of
 * {@value #QUEUE_CAPACITY} batches. A batch that finds the queue full is dropped with a warning; the sweeper
 * sends those products later, so a dropped batch loses nothing.
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
    private volatile Batch inProgress;

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
        int queued = service.shutdownNow().stream()
                .mapToInt(task -> task instanceof Batch batch ? batch.getProducts().size() : 0)
                .sum();
        // the interrupt stops the running batch after its current send, which itself cannot be interrupted
        Batch running = inProgress;
        int unfinished = running == null ? 0 : running.unfinished();
        log.warn("PIM entry requests not sent before shutdown: {} products left to the sweeper", queued + unfinished);
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
        Batch batch = new Batch(storeId, pending);
        try {
            executor.execute(batch);
        } catch (RejectedExecutionException e) {
            if (executor instanceof ExecutorService service && service.isShutdown()) {
                log.warn("Application is shutting down, {} products of store {} left to the sweeper for a PIM entry request",
                        pending.size(), storeId);
            } else {
                log.warn("PIM entry request queue is full, dropping a batch of {} products for store {}; the sweeper will send them",
                        pending.size(), storeId);
            }
        }
    }

    private static boolean isPending(Product product) {
        return StringUtils.isBlank(product.getPimId()) && StringUtils.isNotBlank(product.getEan());
    }

    /** A named task rather than a lambda, so {@link #shutdown()} can count the products it leaves unsent. */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private final class Batch implements Runnable {
        private final String storeId;
        @Getter
        private final List<Product> products;
        private final AtomicInteger started = new AtomicInteger();

        @Override
        public void run() {
            inProgress = this;
            try {
                for (Product product : products) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    started.incrementAndGet();
                    sendIfMissing(storeId, product);
                }
            } finally {
                inProgress = null;
            }
        }

        /** The product being sent counts as unfinished: its send may still fail after the warning is logged. */
        int unfinished() {
            return Math.min(products.size(), products.size() - started.get() + 1);
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
