package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

/**
 * Counts category match submissions per ean. A product that still has no
 * category after max-attempts submissions stops being submitted. The counter
 * lives in memory — an application restart resets it, just like sweepCounter.
 */
@Component
public class CategoryMatchAttempts {

    private final Map<String, Integer> attemptsByEan = new ConcurrentHashMap<>();

    public int record(String ean) {
        if (ean == null || ean.isBlank()) {
            return 0;
        }
        return attemptsByEan.merge(unifyEan(ean), 1, Integer::sum);
    }

    public boolean exhausted(String ean, int maxAttempts) {
        if (maxAttempts <= 0 || ean == null) {
            return false;
        }
        return attemptsByEan.getOrDefault(unifyEan(ean), 0) >= maxAttempts;
    }

    public void clear(String ean) {
        if (ean != null) {
            attemptsByEan.remove(unifyEan(ean));
        }
    }

    public int trackedCount() {
        return attemptsByEan.size();
    }
}
