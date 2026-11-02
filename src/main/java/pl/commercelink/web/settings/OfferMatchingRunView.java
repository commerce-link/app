package pl.commercelink.web.settings;

import pl.commercelink.marketplace.links.OfferMatchProgress;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A run the "Dopasuj oferty" page reports: one going on (how many offers it checked) or one that failed (why).
 *
 * @param total how many offers the marketplace lists, or null before it said
 */
public record OfferMatchingRunView(boolean running, boolean failed, int processed, Integer total, String updatedAt,
                                   String startedAt, String error) {

    private static final DateTimeFormatter AT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));

    /** Null when there is nothing to report: a run abandoned long enough ago may be replaced and is not shown. */
    public static OfferMatchingRunView of(OfferMatchProgress progress, Instant now) {
        boolean running = progress.isRunning(now);
        if (!running && !progress.isFailed()) {
            return null;
        }
        return new OfferMatchingRunView(running, progress.isFailed(), progress.processed(), progress.total(),
                format(progress.updatedAt()), format(progress.startedAt()), progress.error());
    }

    private static String format(Instant at) {
        return at == null ? null : AT.format(at);
    }
}
