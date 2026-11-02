package pl.commercelink.marketplace.links;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one offer matching run for a store and marketplace. Offers published under our own pimId are only counted.
 * Offers already linked are counted, and also listed while the link cannot work yet: in {@code notInCatalog} when its
 * product is missing from the store's catalog, in {@code toReview} when the PIM no longer knows its entry. Every other
 * offer lands in exactly one list.
 */
public record OfferMatchResult(Instant finishedAt, int total, List<Row> linked, List<Row> notInCatalog,
                               List<Row> noPimEntry, List<Row> toReview, int ownOffers, int alreadyLinked) {

    public static final String NO_EAN = "no-ean";
    public static final String NO_KEY = "no-key";
    public static final String AMBIGUOUS = "ambiguous";
    /** In {@code notInCatalog}: linked before this run, the product still missing from the store's catalog. */
    public static final String ALREADY_LINKED = "already-linked";
    /** In {@code toReview}: the existing link points at an entry the PIM no longer knows. */
    public static final String STALE_LINK = "stale-link";

    /** {@code pimId} is set for a saved link; {@code reason} only for an offer left to review. */
    public record Row(String sellerOfferKey, String title, List<String> eans, String pimId, String reason) {
    }
}
