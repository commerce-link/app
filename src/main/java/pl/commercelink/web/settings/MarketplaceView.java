package pl.commercelink.web.settings;

import java.util.List;

/**
 * A connected marketplace summarised for the list on the marketplaces page: its state, when orders (and returns) are
 * fetched, and which catalogs send offers to it. Texts are resolved messages.
 *
 * @param catalogs      names of the catalogs with categories exported to this marketplace; empty means no offer goes there
 * @param returns       the returns schedule, or null when the marketplace does not import returns
 * @param authorizeHref the account connection page, or null when the marketplace is connected with keys only
 */
public record MarketplaceView(String name, String displayName, State state, String orders, String returns,
                              List<String> catalogs, String editHref, String authorizeHref, String disconnectHref,
                              String exportsHref) {

    public enum State {
        /** Orders are fetched and offers exported. */
        ACTIVE,
        /** The marketplace account was never connected (device flow not finished). */
        NOT_AUTHORIZED,
        /** The marketplace rejected the stored token or keys; nothing is fetched until the operator fixes it. */
        EXPIRED,
        /** The adapter of this marketplace is no longer installed. */
        MISSING
    }

    public boolean active() {
        return state == State.ACTIVE;
    }

    public boolean installed() {
        return state != State.MISSING;
    }

    /** The shortcut shown in place of the status: connect the account on its page. */
    public boolean needsAuthorization() {
        return authorizeHref != null && (state == State.NOT_AUTHORIZED || state == State.EXPIRED);
    }
}
