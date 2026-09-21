package pl.commercelink.starter.security.tenant;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

// Best-effort, per-instance throttle for the deprecated last-6 plaintext key path. It blunts brute-force
// during migration; it does not touch the hashed path (256-bit keys need no throttling).
// Remove together with the legacy path once every store is rotated to a hashed key.
@Component
public class LegacyApiKeyAttemptLimiter {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final long MAX_TRACKED_STORES = 10_000;

    private final Cache<String, AtomicInteger> failures = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW)
            .maximumSize(MAX_TRACKED_STORES)
            .build();

    public boolean isBlocked(String storeId) {
        AtomicInteger count = failures.getIfPresent(storeId);
        return count != null && count.get() >= MAX_FAILURES;
    }

    public void recordFailure(String storeId) {
        failures.get(storeId, key -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(String storeId) {
        failures.invalidate(storeId);
    }
}
