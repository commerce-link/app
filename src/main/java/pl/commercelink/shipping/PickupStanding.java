package pl.commercelink.shipping;

/** Where the pickup of an indexed package stands on its owner, for the "Zamów odbiór" page and its index. */
public enum PickupStanding {
    /** It can be ordered (awaiting, failed, or never confirmed): listed on the page. */
    ORDERABLE,
    /** A pickup command waits for its result: not listed, but kept, since a failure makes it orderable again. */
    IN_FLIGHT,
    /** Ordered, not required, removed or gone with its owner: the index entry is stale. */
    GONE
}
