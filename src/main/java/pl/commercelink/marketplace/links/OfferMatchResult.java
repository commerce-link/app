package pl.commercelink.marketplace.links;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one offer matching run for a store and marketplace. Offers already linked and offers published under our
 * own pimId are only counted; every other offer lands in exactly one list.
 */
public record OfferMatchResult(Instant finishedAt, int total, List<Row> linked, List<Row> notInCatalog,
                               List<Row> noPimEntry, List<Row> toReview, int ownOffers, int alreadyLinked) {

    public static final String NO_EAN = "no-ean";
    public static final String NO_KEY = "no-key";
    public static final String AMBIGUOUS = "ambiguous";

    /** {@code pimId} is set for a saved link; {@code reason} only for an offer left to review. */
    public record Row(String sellerOfferKey, String title, List<String> eans, String pimId, String reason) {
    }
}
