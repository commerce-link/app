package pl.commercelink.products.information;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import jakarta.annotation.PreDestroy;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Asks the PIM for a product's entry right after the product is saved in a catalog, instead of
 * waiting for the periodic {@link PIMIndexEventScheduler} sweep, which stays the safety net for
 * anything missed here. Requests go out with the highest priority, as for a managed catalog.
 * <p>
 * A product the PIM already knows by manufacturer code and brand is skipped: a request would change
 * nothing, because {@code PimCatalogRegistry.attachEntryToProducts} pins entries to products by EAN only.
 * Does nothing outside {@code application.env=prod}. A failure never reaches the caller.
 * <p>
 * The single-product path is synchronous. The bulk path ({@link #requestMissingEntries}) is asynchronous:
 * every request is a blocking SQS send, so thousands of products on the request thread could exceed the
 * load balancer timeout, and an SQS degradation would stall each send until the SDK times out. The whole
 * batch is handed to a single daemon worker (batches stay ordered and the PIM is not hit in parallel)
 * with a bounded queue of {@value #QUEUE_CAPACITY} batches. A batch that finds the queue full is dropped
 * with a warning; the sweeper sends those products later, so a dropped batch loses nothing.
 */
@Component
@Slf4j
public class PimEntryRequester {

    private static final int QUEUE_CAPACITY = 100;

    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final String env;
    private final Executor executor;

    @Autowired
    public PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, @Value("${application.env:}") String env) {
        this(pimCatalog, brandMapper, env, newWorker());
    }

    PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, String env, Executor executor) {
        this.pimCatalog = pimCatalog;
        this.brandMapper = brandMapper;
        this.env = env;
        this.executor = executor;
    }

    private static Executor newWorker() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "pim-entry-requester");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    /**
     * Returns without waiting for the sends; see the class comment for why and for what happens when the queue is full.
     */
    public void requestMissingEntries(String storeId, Collection<Product> products) {
        if (!"prod".equals(env) || products.isEmpty()) {
            return;
        }
        List<Product> batch = List.copyOf(products);
        try {
            executor.execute(() -> requestBatch(storeId, batch));
        } catch (RejectedExecutionException e) {
            log.warn("PIM entry request queue is full, dropping a batch of {} products for store {}; the sweeper will send them",
                    batch.size(), storeId);
        }
    }

    private void requestBatch(String storeId, List<Product> batch) {
        for (Product product : batch) {
            try {
                requestMissingEntry(storeId, product);
            } catch (RuntimeException e) {
                log.warn("Could not request PIM entry for store {}: {}", storeId, e.toString());
            }
        }
    }

    public void requestMissingEntry(String storeId, Product product) {
        if (!"prod".equals(env)) {
            return;
        }
        if (StringUtils.isNotBlank(product.getPimId()) || StringUtils.isBlank(product.getEan())) {
            return;
        }
        try {
            PimView pim = pimCatalog.withStoreData(storeId);
            if (isKnown(pim, brandMapper, product)) {
                return;
            }
            pimCatalog.submit(new PimEntryRequest(product.getEan(), product.getManufacturerCode(),
                    product.getBrand(), PimQueuePriorityCalculator.MAX_VALUE));
        } catch (RuntimeException e) {
            // message only: a stack trace per product would flood the log during an SQS outage on a large batch
            log.warn("Could not request PIM entry for store {}, category {}, EAN {}: {}",
                    storeId, product.getCategoryId(), product.getEan(), e.toString());
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
