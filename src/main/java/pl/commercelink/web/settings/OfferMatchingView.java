package pl.commercelink.web.settings;

import pl.commercelink.marketplace.links.OfferMatchResult;

import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The latest offer matching run as the "Dopasuj oferty" page shows it: one group per outcome, each with its full count
 * but at most {@link #ROW_LIMIT} rows, since a store can have thousands of offers in one group. The EANs of the offers
 * whose products have to be added to a catalog are given in full (one per active offer), one per line, to paste into
 * the catalog screens.
 */
public record OfferMatchingView(String finishedAt, int total, int ownOffers, int alreadyLinked, List<Group> groups) {

    public static final int ROW_LIMIT = 100;

    private static final DateTimeFormatter FINISHED_AT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Warsaw"));
    private static final String REASON_KEY = "store.marketplaces.offer.matching.reason.";
    private static final Set<String> KNOWN_REASONS = Set.of(OfferMatchResult.NO_EAN, OfferMatchResult.NO_KEY,
            OfferMatchResult.AMBIGUOUS, OfferMatchResult.DUPLICATED_EAN, OfferMatchResult.NO_MFN);

    public static OfferMatchingView of(OfferMatchResult result) {
        return new OfferMatchingView(FINISHED_AT.format(result.finishedAt()), result.total(), result.ownOffers(),
                result.alreadyLinked(), List.of(
                group("linked", result.linked(), false),
                group("notInCatalog", result.notInCatalog(), true),
                group("toReview", result.toReview(), false)));
    }

    public Group group(String name) {
        return groups.stream().filter(group -> group.name().equals(name)).findFirst().orElseThrow();
    }

    private static Group group(String name, List<OfferMatchResult.Row> rows, boolean eansToCopy) {
        List<OfferMatchResult.Row> all = rows == null ? List.of() : rows;
        // active offers first (stable), so the capped rows show the ones that matter
        List<OfferMatchResult.Row> ordered = all.stream()
                .sorted(Comparator.comparing((OfferMatchResult.Row row) -> !isActive(row))).toList();
        return new Group(name, all.size(),
                ordered.stream().limit(ROW_LIMIT).map(OfferMatchingView::row).toList(),
                eansToCopy && !all.isEmpty() ? eans(ordered) : null);
    }

    private static boolean isActive(OfferMatchResult.Row row) {
        return !Boolean.FALSE.equals(row.active());
    }

    /**
     * One EAN per active offer: the row's catalog EAN (the link's) when known, else the offer's first EAN. Every EAN of an
     * offer would add the same product to the catalog several times, and an inactive offer is not worth a catalog slot.
     */
    private static String eans(List<OfferMatchResult.Row> rows) {
        Set<String> eans = new LinkedHashSet<>();
        rows.stream().filter(OfferMatchingView::isActive).forEach(row -> {
            if (StringUtils.isNotBlank(row.catalogEan())) {
                eans.add(row.catalogEan());
            } else if (row.eans() != null && !row.eans().isEmpty()) {
                eans.add(row.eans().get(0));
            }
        });
        return String.join("\n", eans);
    }

    private static Row row(OfferMatchResult.Row row) {
        String reason = row.reason();
        return new Row(row.sellerOfferKey(), row.title(), row.eans() == null ? List.of() : row.eans(), row.product(),
                row.catalogEan(), reason != null && KNOWN_REASONS.contains(reason) ? REASON_KEY + reason : null,
                OfferMatchResult.ALREADY_LINKED.equals(reason), isActive(row));
    }

    /**
     * @param eans one EAN per active offer in the group, one per line, or null when the group has nothing to add to a
     *             catalog
     */
    public record Group(String name, int count, List<Row> rows, String eans) {

        public boolean limited() {
            return count > rows.size();
        }
    }

    /**
     * @param product      name of the catalog product a linked offer's orders land on, or null
     * @param catalogEan   the EAN to add to a catalog for the offer's link to work, or null
     * @param reasonKey    message key of the reason an offer was left to review, or null
     * @param linkedBefore an operator linked the offer; no catalog product has the link's EANs yet
     * @param active       the offer is on sale on the marketplace
     */
    public record Row(String sellerOfferKey, String title, List<String> eans, String product, String catalogEan,
                      String reasonKey, boolean linkedBefore, boolean active) {
    }
}
