package pl.commercelink.inventory;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
class StoreInventoryRefresher {

    private static final int THREADS = 2;

    private final ExecutorService executor;
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    StoreInventoryRefresher() {
        this(Executors.newFixedThreadPool(THREADS, StoreInventoryRefresher::daemonThread));
    }

    StoreInventoryRefresher(ExecutorService executor) {
        this.executor = executor;
    }

    // one refresh per store at a time: every request arriving during a rebuild sees the same stale snapshot
    void refreshInBackground(String storeId, Runnable refresh) {
        if (!inProgress.add(storeId)) {
            return;
        }
        try {
            executor.execute(() -> run(storeId, refresh));
        } catch (RejectedExecutionException e) {
            inProgress.remove(storeId);
            log.error("Store inventory refresh for {} was not started", storeId, e);
        }
    }

    private void run(String storeId, Runnable refresh) {
        try {
            refresh.run();
        } catch (RuntimeException e) {
            log.error("Store inventory refresh failed for {}; the previous snapshot stays in use", storeId, e);
        } finally {
            inProgress.remove(storeId);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "store-inventory-refresh-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}
