package pl.commercelink.web.settings;

import pl.commercelink.marketplace.links.OfferMatchResult;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The latest offer matching run as the "Dopasuj oferty" page shows it: one group per outcome, each with its full count
 * but at most {@link #ROW_LIMIT} rows, since a store can have thousands of offers in one group.
 */
public record OfferMatchingView(String finishedAt, int total, int ownOffers, int alreadyLinked, List<Group> groups) {

    public static final int ROW_LIMIT = 100;

    private static final DateTimeFormatter FINISHED_AT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));
    private static final String REASON_KEY = "store.marketplaces.offer.matching.reason.";
    private static final Set<String> KNOWN_REASONS = Set.of(OfferMatchResult.NO_EAN, OfferMatchResult.NO_KEY);

    public static OfferMatchingView of(OfferMatchResult result) {
        return new OfferMatchingView(FINISHED_AT.format(result.finishedAt()), result.total(), result.ownOffers(),
                result.alreadyLinked(), List.of(
                group("linked", result.linked()),
                group("toReview", result.toReview())));
    }

    public Group group(String name) {
        return groups.stream().filter(group -> group.name().equals(name)).findFirst().orElseThrow();
    }

    private static Group group(String name, List<OfferMatchResult.Row> rows) {
        List<OfferMatchResult.Row> all = rows == null ? List.of() : rows;
        // active offers first (stable), so the capped rows show the ones that matter
        List<OfferMatchResult.Row> ordered = all.stream()
                .sorted(Comparator.comparing((OfferMatchResult.Row row) -> !isActive(row))).toList();
        return new Group(name, all.size(), ordered.stream().limit(ROW_LIMIT).map(OfferMatchingView::row).toList());
    }

    private static boolean isActive(OfferMatchResult.Row row) {
        return !Boolean.FALSE.equals(row.active());
    }

    private static Row row(OfferMatchResult.Row row) {
        String reason = row.reason();
        return new Row(row.sellerOfferKey(), row.title(), row.eans() == null ? List.of() : row.eans(),
                reason != null && KNOWN_REASONS.contains(reason) ? REASON_KEY + reason : null, isActive(row));
    }

    public record Group(String name, int count, List<Row> rows) {

        public boolean limited() {
            return count > rows.size();
        }
    }

    /**
     * @param reasonKey message key of the reason an offer was left to review, or null
     * @param active    the offer is on sale on the marketplace
     */
    public record Row(String sellerOfferKey, String title, List<String> eans, String reasonKey, boolean active) {
    }
}
