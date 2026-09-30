package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * How each supplier of a store is ordered from: through its API or by hand, and whether the order waits for the super
 * admin's approval. Whether a supplier supports ordering depends on the account's configuration, so every check reads
 * the supplier's secret (never cached, see ProviderConfigurationManager); the checks run in parallel and a failing or
 * slow one counts as manual instead of breaking the page.
 */
@Slf4j
@Component
public class SupplierOrderingModes {

    public record OrderingMode(boolean api, boolean approval) {
        public static final OrderingMode MANUAL = new OrderingMode(false, false);
    }

    private final StoresRepository storesRepository;
    private final SupplierProviderResolver supplierProviderResolver;
    private final Duration timeout;

    @Autowired
    public SupplierOrderingModes(StoresRepository storesRepository, SupplierProviderResolver supplierProviderResolver) {
        this(storesRepository, supplierProviderResolver, Duration.ofSeconds(10));
    }

    SupplierOrderingModes(StoresRepository storesRepository, SupplierProviderResolver supplierProviderResolver, Duration timeout) {
        this.storesRepository = storesRepository;
        this.supplierProviderResolver = supplierProviderResolver;
        this.timeout = timeout;
    }

    public Map<String, OrderingMode> of(String storeId, Collection<String> providers) {
        List<String> distinct = providers.stream().filter(Objects::nonNull).distinct().toList();
        Map<String, OrderingMode> modes = new LinkedHashMap<>();
        Store store = distinct.isEmpty() ? null : storesRepository.findById(storeId);
        if (store == null) {
            distinct.forEach(provider -> modes.put(provider, OrderingMode.MANUAL));
            return modes;
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Map<String, Future<Boolean>> checks = new LinkedHashMap<>();
            distinct.forEach(provider -> checks.put(provider, executor.submit(() -> supportsOrdering(store, provider))));
            long deadline = System.nanoTime() + timeout.toNanos();
            checks.forEach((provider, check) ->
                    modes.put(provider, new OrderingMode(result(provider, check, deadline), store.isGlobalSupplier(provider))));
        } finally {
            // shutdownNow, not close(): close() would wait for a hanging adapter
            executor.shutdownNow();
        }
        return modes;
    }

    private boolean supportsOrdering(Store store, String provider) {
        SupplierProvider supplierProvider = supplierProviderResolver.resolve(store, provider);
        return supplierProvider != null && supplierProvider.supportsOrdering();
    }

    private static boolean result(String provider, Future<Boolean> check, long deadline) {
        try {
            return check.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Could not tell whether supplier {} supports ordering: {}", provider, e.toString());
            check.cancel(true);
            return false;
        }
    }
}
