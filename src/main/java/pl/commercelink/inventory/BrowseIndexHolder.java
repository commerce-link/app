package pl.commercelink.inventory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        if (index.version() != global.version() && rebuilding.compareAndSet(false, true)) {
            executor.execute(() -> {
                try {
                    current.set(build());
                } finally {
                    rebuilding.set(false);
                }
            });
        }
        return index;
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
        // Read the version before the items: a reload in between only causes one extra rebuild, never a stale index.
        long version = global.version();
        BrowseIndex index = BrowseIndex.build(version, global.all());
        log.info("Browse index built: {} products, version {}, {} ms", index.size(), version,
                (System.nanoTime() - started) / 1_000_000);
        return index;
    }
}
