package pl.commercelink.marketplace.links;

import pl.commercelink.marketplace.links.OfferMatchResult.Row;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An offer matching run in progress: a marketplace may answer only a few pages a minute, so a run goes on over several
 * queue messages, and each one picks up the listing at {@code nextCursor} with what the earlier ones counted. Only the
 * finished run becomes an {@link OfferMatchResult}; the links themselves are saved as the run goes.
 *
 * @param processed offers counted so far (each seller offer key once)
 * @param total     how many offers the marketplace says it lists, or null while unknown
 * @param seenKeys            seller offer keys already counted, so an offer the listing repeats on a later page counts once
 * @param idleRateLimitPauses pauses in a row on the marketplace's request limit without a page read in between
 * @param error               why a {@code FAILED} run stopped (a code the page turns into a sentence), else null
 * @param errorStatus         the marketplace's HTTP status for {@link OfferMatchError#MARKETPLACE_ERROR}, else null
 */
public record OfferMatchProgress(String runId, Instant startedAt, Instant updatedAt, Status status, String nextCursor,
                                 int processed, Integer total, int ownOffers, int alreadyLinked, List<Row> linked,
                                 List<Row> notInCatalog, List<Row> toReview,
                                 Set<String> seenKeys, int idleRateLimitPauses, OfferMatchError error,
                                 Integer errorStatus) {

    /**
     * A {@code RUNNING} run not saved for this long is taken as abandoned (a lost continuation, a crashed worker), so a
     * new run may replace it. Well over the longest gap between two saves: a message's time budget, or the marketplace's
     * retry delay.
     */
    public static final Duration STALE_AFTER = Duration.ofMinutes(30);

    public enum Status { RUNNING, FAILED }

    public OfferMatchProgress {
        linked = linked == null ? List.of() : List.copyOf(linked);
        notInCatalog = notInCatalog == null ? List.of() : List.copyOf(notInCatalog);
        toReview = toReview == null ? List.of() : List.copyOf(toReview);
        seenKeys = seenKeys == null ? Set.of() : new LinkedHashSet<>(seenKeys);
    }

    public static OfferMatchProgress start(String runId, Instant now) {
        return new OfferMatchProgress(runId, now, now, Status.RUNNING, null, 0, null, 0, 0, List.of(), List.of(),
                List.of(), Set.of(), 0, null, null);
    }

    /** Running and saved recently enough that it is still going on: a second run must wait. */
    public boolean isRunning(Instant now) {
        return status == Status.RUNNING && updatedAt != null && updatedAt.isAfter(now.minus(STALE_AFTER));
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public OfferMatchProgress failed(Instant now, OfferMatchError reason) {
        return failed(now, reason, null);
    }

    public OfferMatchProgress failed(Instant now, OfferMatchError reason, Integer httpStatus) {
        return new OfferMatchProgress(runId, startedAt, now, Status.FAILED, nextCursor, processed, total, ownOffers,
                alreadyLinked, linked, notInCatalog, toReview, seenKeys, idleRateLimitPauses, reason,
                httpStatus);
    }

    OfferMatchResult result(Instant finishedAt) {
        return new OfferMatchResult(finishedAt, processed, linked, notInCatalog, toReview, ownOffers, alreadyLinked);
    }
}
