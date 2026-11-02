package pl.commercelink.web.settings;

import pl.commercelink.marketplace.links.OfferMatchResult;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The latest offer matching run as the "Dopasuj oferty" page shows it: one group per outcome, each with its full count
 * but at most {@link #ROW_LIMIT} rows, since a store can have thousands of offers in one group. The EANs of the groups
 * whose products have to be added to a catalog are given in full, one per line, to paste into the catalog screens.
 */
public record OfferMatchingView(String finishedAt, int total, int ownOffers, int alreadyLinked, List<Group> groups) {

    public static final int ROW_LIMIT = 100;

    private static final DateTimeFormatter FINISHED_AT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));
    private static final String REASON_KEY = "store.marketplaces.offer.matching.reason.";
    private static final Set<String> KNOWN_REASONS = Set.of(OfferMatchResult.NO_EAN, OfferMatchResult.NO_KEY,
            OfferMatchResult.AMBIGUOUS, OfferMatchResult.STALE_LINK);

    public static OfferMatchingView of(OfferMatchResult result) {
        return new OfferMatchingView(FINISHED_AT.format(result.finishedAt()), result.total(), result.ownOffers(),
                result.alreadyLinked(), List.of(
                group("linked", result.linked(), false),
                group("notInCatalog", result.notInCatalog(), true),
                group("noPimEntry", result.noPimEntry(), true),
                group("toReview", result.toReview(), false)));
    }

    public Group group(String name) {
        return groups.stream().filter(group -> group.name().equals(name)).findFirst().orElseThrow();
    }

    private static Group group(String name, List<OfferMatchResult.Row> rows, boolean eansToCopy) {
        List<OfferMatchResult.Row> all = rows == null ? List.of() : rows;
        return new Group(name, all.size(),
                all.stream().limit(ROW_LIMIT).map(OfferMatchingView::row).toList(),
                eansToCopy && !all.isEmpty() ? eans(all) : null);
    }

    private static String eans(List<OfferMatchResult.Row> rows) {
        Set<String> eans = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.eans() != null) {
                eans.addAll(row.eans());
            }
        });
        return String.join("\n", eans);
    }

    private static Row row(OfferMatchResult.Row row) {
        String reason = row.reason();
        return new Row(row.sellerOfferKey(), row.title(), row.eans() == null ? List.of() : row.eans(), row.pimId(),
                reason != null && KNOWN_REASONS.contains(reason) ? REASON_KEY + reason : null,
                OfferMatchResult.ALREADY_LINKED.equals(reason));
    }

    /**
     * @param eans the distinct EANs of every offer in the group, one per line, or null when the group has nothing to
     *             add to a catalog
     */
    public record Group(String name, int count, List<Row> rows, String eans) {

        public boolean limited() {
            return count > rows.size();
        }
    }

    /**
     * @param reasonKey    message key of the reason an offer was left to review, or null
     * @param linkedBefore the offer was linked by an earlier run; its product is still missing from the catalog
     */
    public record Row(String sellerOfferKey, String title, List<String> eans, String pimId, String reasonKey,
                      boolean linkedBefore) {
    }
}
