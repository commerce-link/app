package pl.commercelink.inventory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the browse index in step with the global inventory. Nothing announces a feed reload, so each call compares the
 * inventory's version: the first call builds the index in place, later versions are rebuilt on a background thread
 * while the previous index keeps answering — a page never waits for a reload of the feeds.
 */
@Slf4j
@Component
public class BrowseIndexHolder {

    private final GlobalMatchedInventory global;
    private final Executor executor;
    private final AtomicReference<BrowseIndex> current = new AtomicReference<>();
    private final AtomicBoolean rebuilding = new AtomicBoolean();
    private final List<Runnable> replacedListeners = new CopyOnWriteArrayList<>();

    @Autowired
    public BrowseIndexHolder(GlobalMatchedInventory global) {
        this(global, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "browse-index");
            thread.setDaemon(true);
            return thread;
        }));
    }

    BrowseIndexHolder(GlobalMatchedInventory global, Executor executor) {
        this.global = global;
        this.executor = executor;
    }

    public BrowseIndex current() {
        BrowseIndex index = current.get();
        if (index == null) {
            return buildFirst();
        }
        rebuildIfStale(index);
        return index;
    }

    /** Runs after a rebuilt index replaced the previous one, so whoever holds results of the old one can drop them. */
    public void onReplaced(Runnable listener) {
        replacedListeners.add(listener);
    }

    /**
     * A stale index holds the previous inventory generation in memory until it is replaced; without this, that lasts
     * until someone opens the browse page. Before the first browse there is no index and nothing is built here.
     */
    @Scheduled(fixedDelay = 60_000)
    public void refreshIfStale() {
        BrowseIndex index = current.get();
        if (index != null) {
            rebuildIfStale(index);
        }
    }

    private void rebuildIfStale(BrowseIndex index) {
        if (index.version() != global.version() && rebuilding.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    current.set(build());
                    replacedListeners.forEach(Runnable::run);
                } catch (RuntimeException e) {
                    log.error("Browse index rebuild failed", e);
                } finally {
                    rebuilding.set(false);
                }
            });
        }
    }

    private synchronized BrowseIndex buildFirst() {
        BrowseIndex index = current.get();
        if (index == null) {
            index = build();
            current.set(index);
        }
        return index;
    }

    private BrowseIndex build() {
        long started = System.nanoTime();
        GlobalMatchedInventory.Generation generation = global.generation();
        BrowseIndex index = BrowseIndex.build(generation.version(), generation.all(), generation.index());
        log.info("Browse index built: {} products, version {}, {} ms", index.size(), generation.version(),
                (System.nanoTime() - started) / 1_000_000);
        return index;
    }
}
