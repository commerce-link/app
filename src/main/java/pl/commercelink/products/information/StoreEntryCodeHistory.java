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
 * The codes each store entry had in this instance's copy of the PIM index during the last {@link #KEEP}, recorded just
 * before every refresh replaces the copy. An edit's event can be handled after a refresh that was triggered by another
 * event; the copy then already holds the new codes, and only this history still knows which codes the edit removed.
 * In memory, per instance: an instance sees every refresh of its own copy, so the instance that handles an event has
 * recorded the codes its copy held before the edit. A restart starts with an empty history.
 */
class StoreEntryCodeHistory {

    static final Duration KEEP = Duration.ofMinutes(30);

    private final Clock clock;
    private final Map<String, Map<String, Instant>> eansByPimId = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> mpnsByPimId = new ConcurrentHashMap<>();

    StoreEntryCodeHistory(Clock clock) {
        this.clock = clock;
    }

    /** Records the codes of the given store entries as seen now, and forgets codes not seen for longer than {@link #KEEP}. */
    void remember(Collection<PimEntry> storeEntries) {
        Instant now = clock.instant();
        for (PimEntry entry : storeEntries) {
            record(eansByPimId, entry.pimId(), entry.gtins(), now);
            record(mpnsByPimId, entry.pimId(), entry.mpns(), now);
        }
        forgetOlderThan(eansByPimId, now.minus(KEEP));
        forgetOlderThan(mpnsByPimId, now.minus(KEEP));
    }

    /** EANs the entry had in the copy within {@link #KEEP}. */
    Set<String> eansOf(String pimId) {
        return recent(eansByPimId, pimId);
    }

    /** MPNs the entry had in the copy within {@link #KEEP}. */
    Set<String> mpnsOf(String pimId) {
        return recent(mpnsByPimId, pimId);
    }

    private static void record(Map<String, Map<String, Instant>> byPimId, String pimId, Collection<String> codes,
                               Instant now) {
        if (pimId == null || codes.isEmpty()) {
            return;
        }
        Map<String, Instant> seen = byPimId.computeIfAbsent(pimId, id -> new ConcurrentHashMap<>());
        codes.stream().filter(code -> code != null && !code.isBlank()).forEach(code -> seen.put(code, now));
    }

    private static void forgetOlderThan(Map<String, Map<String, Instant>> byPimId, Instant limit) {
        byPimId.values().forEach(seen -> seen.values().removeIf(lastSeen -> lastSeen.isBefore(limit)));
        byPimId.values().removeIf(Map::isEmpty);
    }

    private Set<String> recent(Map<String, Map<String, Instant>> byPimId, String pimId) {
        Map<String, Instant> seen = pimId == null ? null : byPimId.get(pimId);
        if (seen == null) {
            return Set.of();
        }
        Instant limit = clock.instant().minus(KEEP);
        return seen.entrySet().stream()
                .filter(code -> !code.getValue().isBefore(limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
