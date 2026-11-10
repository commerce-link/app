package pl.commercelink.products.information;

import pl.commercelink.pim.api.PimEntry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The EANs each store entry had in this instance's copy of the PIM index during the last {@link #KEEP}, recorded just
 * before every refresh replaces the copy. An edit's event can be handled after a refresh that was triggered by another
 * event; the copy then already holds the new EANs, and only this history still knows which EANs the edit removed.
 * In memory, per instance: an instance sees every refresh of its own copy, so the instance that handles an event has
 * recorded the EANs its copy held before the edit. A restart starts with an empty history.
 */
class StoreEntryCodeHistory {

    static final Duration KEEP = Duration.ofMinutes(30);

    private final Clock clock;
    private final Map<String, Map<String, Instant>> eansByPimId = new ConcurrentHashMap<>();

    StoreEntryCodeHistory(Clock clock) {
        this.clock = clock;
    }

    /** Records the EANs of the given store entries as seen now, and forgets EANs not seen for longer than {@link #KEEP}. */
    void remember(Collection<PimEntry> storeEntries) {
        Instant now = clock.instant();
        for (PimEntry entry : storeEntries) {
            record(entry.pimId(), entry.gtins(), now);
        }
        forgetOlderThan(now.minus(KEEP));
    }

    /** EANs the entry had in the copy within {@link #KEEP}. */
    Set<String> eansOf(String pimId) {
        Map<String, Instant> seen = pimId == null ? null : eansByPimId.get(pimId);
        if (seen == null) {
            return Set.of();
        }
        Instant limit = clock.instant().minus(KEEP);
        return seen.entrySet().stream()
                .filter(code -> !code.getValue().isBefore(limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void record(String pimId, Collection<String> eans, Instant now) {
        if (pimId == null || eans.isEmpty()) {
            return;
        }
        Map<String, Instant> seen = eansByPimId.computeIfAbsent(pimId, id -> new ConcurrentHashMap<>());
        eans.stream().filter(ean -> ean != null && !ean.isBlank()).forEach(ean -> seen.put(ean, now));
    }

    private void forgetOlderThan(Instant limit) {
        eansByPimId.values().forEach(seen -> seen.values().removeIf(lastSeen -> lastSeen.isBefore(limit)));
        eansByPimId.values().removeIf(Map::isEmpty);
    }
}
