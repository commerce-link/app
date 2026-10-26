package pl.commercelink.inventory;

/**
 * Tells the inventory which PIM ids are entries owned by one store and, for a clone, which global entry it was
 * cloned from. Only {@link #isStoreEntry} is abstract so that a test can supply it as a lambda.
 */
@FunctionalInterface
interface StoreEntryKeys {

    /** Whether the id is a store entry: a PIM id the global catalog does not know (spec 4.3). */
    boolean isStoreEntry(String pimId);

    /** The global entry a store entry was cloned from, or {@code null} for a plain store entry. */
    default String sourceOf(String pimId) {
        return null;
    }

    static StoreEntryKeys none() {
        return pimId -> false;
    }
}
