package pl.commercelink.marketplace.links;

/**
 * Why an offer matching run failed, as the page explains it. A code, not the exception's text: the page is the
 * operator's, and an exception message may carry anything an adapter put into it; the details go to the log.
 */
public enum OfferMatchError {
    /** The marketplace answered with an HTTP error (the status is kept next to the code). */
    MARKETPLACE_ERROR,
    /** The marketplace no longer lists the store's offers for this connection. */
    NO_LISTING,
    /** The connection stopped working while the run went on. */
    INTEGRATION_INACTIVE,
    /** The store was deactivated while the run went on. */
    STORE_INACTIVE,
    /** The marketplace kept refusing the next page for too long. */
    RATE_LIMIT_EXHAUSTED,
    /** Anything else: a failure on our side. */
    INTERNAL
}
