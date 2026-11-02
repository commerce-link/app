package pl.commercelink.marketplace.links;

public record ResolvedOffer(String pimId, String ean, String mfn, String category, Source source) {

    public enum Source {
        /** An explicit link from the seller offer key to a PIM entry. */
        LINK,
        /** The seller offer key is our own pimId, published by CommerceLink. */
        OWN_OFFER,
        /** The manufacturer code from the order line matched a PIM MPN. */
        MFN
    }
}
