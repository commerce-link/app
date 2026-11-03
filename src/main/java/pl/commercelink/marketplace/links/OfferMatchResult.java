package pl.commercelink.marketplace.links;

import java.time.Instant;
import java.util.List;

/**
 * Outcome of one offer matching run for a store and marketplace, grouped by what the store's catalogs say about each
 * offer's EANs. Offers published under our own pimId are only counted. Offers an operator linked by hand are counted,
 * and also listed while the link cannot work: in {@code notInCatalog} when no catalog product has its EANs, in
 * {@code toReview} when they point at several products or at one without a manufacturer code. Every other offer lands in
 * exactly one list.
 */
public record OfferMatchResult(Instant finishedAt, int total, List<Row> linked, List<Row> notInCatalog,
                               List<Row> toReview, int ownOffers, int alreadyLinked) {

    public static final String NO_EAN = "no-ean";
    public static final String NO_KEY = "no-key";
    /** In {@code toReview}: the EANs point at catalog products with different manufacturer codes. */
    public static final String AMBIGUOUS = "ambiguous";
    /** One EAN of the offer is carried by catalog products with different manufacturer codes: a catalog duplicate. */
    public static final String DUPLICATED_EAN = "duplicated-ean";
    /** In {@code toReview}: the catalog product the EANs point at has no manufacturer code, which an order line needs. */
    public static final String NO_MFN = "no-mfn";
    /** In {@code notInCatalog}: linked by an operator, no catalog product has the link's EANs yet. */
    public static final String ALREADY_LINKED = "already-linked";

    /**
     * {@code product} is the catalog product's name for a {@code linked} row; {@code reason} is set for an offer left to
     * review or linked by hand. {@code active} is whether the offer is on sale on the marketplace (null in files saved
     * before it was kept: such an offer counts as active). {@code catalogEan} is, in {@code notInCatalog}, the EAN to add
     * to a catalog for the link to work (null in files of older runs).
     */
    public record Row(String sellerOfferKey, String title, List<String> eans, String product, String reason,
                      Boolean active, String catalogEan) {

        public Row {
            active = active == null ? Boolean.TRUE : active;
        }

        public Row(String sellerOfferKey, String title, List<String> eans, String product, String reason) {
            this(sellerOfferKey, title, eans, product, reason, true, null);
        }
    }
}
