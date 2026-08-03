package pl.commercelink.taxonomy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts category match submissions per mfn. A product that still has no
 * category after max-attempts submissions stops being submitted. The counter
 * lives in memory — an application restart resets it, just like sweepCounter.
 */
@Component
public class CategoryMatchAttempts {

    private final Map<String, Integer> attemptsByMfn = new ConcurrentHashMap<>();

    public int record(String mfn) {
        if (mfn == null || mfn.isBlank()) {
            return 0;
        }
        return attemptsByMfn.merge(mfn, 1, Integer::sum);
    }

    public boolean exhausted(String mfn, int maxAttempts) {
        if (maxAttempts <= 0 || mfn == null) {
            return false;
        }
        return attemptsByMfn.getOrDefault(mfn, 0) >= maxAttempts;
    }

    public void clear(String mfn) {
        if (mfn != null) {
            attemptsByMfn.remove(mfn);
        }
    }

    public int trackedCount() {
        return attemptsByMfn.size();
    }
}
