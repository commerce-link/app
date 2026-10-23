package pl.commercelink.web;

/** What the Wysyłam z Allegro page can say about the store's Allegro connection, asked from Allegro on each visit. */
public enum AllegroShippingStatus {
    /** No logged-in Allegro marketplace connection: nothing to borrow the tokens from. */
    MARKETPLACE_NOT_CONNECTED,
    /** Allegro answers 403: the store's Allegro application was authorised without the shipments scope. */
    MISSING_SHIPMENTS_CONSENT,
    /** Allegro did not answer (timeout, 5xx): nothing is known, the operator may try again. */
    CHECK_FAILED,
    /** The connection works and has the consent, Wysyłam z Allegro is off. */
    READY,
    /** The connection works and Wysyłam z Allegro is on. */
    ENABLED
}
