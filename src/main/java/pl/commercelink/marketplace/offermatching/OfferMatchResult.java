package pl.commercelink.marketplace.offermatching;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one offer matching run for a store and marketplace. Offers published under our own pimId and offers an
 * operator linked by hand are only counted; every other offer is either linked to its EANs or left to review (no EAN,
 * no EAN any more but an earlier link kept, no key). Files of runs saved before the catalog stopped being consulted
 * carry more fields, which are ignored.
 */
public record OfferMatchResult(Instant finishedAt, int total, List<Row> linked, List<Row> toReview, int ownOffers,
                               int alreadyLinked) {

    public static final String NO_EAN = "no-ean";
    public static final String NO_KEY = "no-key";
    /**
     * The offer lists no EAN any more, but an earlier run linked it: the link stays, and the row carries the link's EANs,
     * which the offer's orders keep getting.
     */
    public static final String NO_EAN_KEPT_LINK = "no-ean-kept-link";

    /**
     * {@code reason} is set for an offer left to review. {@code active} is whether the offer is on sale on the
     * marketplace (null in files saved before it was kept: such an offer counts as active).
     */
    public record Row(String sellerOfferKey, String title, List<String> eans, String reason, Boolean active) {

        public Row {
            active = active == null ? Boolean.TRUE : active;
        }
    }
}
