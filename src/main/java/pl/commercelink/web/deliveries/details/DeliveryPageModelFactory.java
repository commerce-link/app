package pl.commercelink.web.deliveries.details;

import java.util.Set;

/**
 * Builds the delivery details page model (spec §3.2) from what the controller resolved. A dialog asked for by
 * ?open= (the no-JavaScript opener) is rendered open only when the viewer could open it from the page; "…-all" dialogs
 * come with every waiting allocation checked, as the page's script checks them before opening.
 */
public final class DeliveryPageModelFactory {

    static final Set<String> SELECT_ALL = Set.of("receive-all", "ship-all", "remove-all");

    private DeliveryPageModelFactory() {
    }

    /** "ship-all" and "remove-all" open the selection's own dialog (its fields exist once), with every line checked. */
    public static String dialogId(String key) {
        return switch (key) {
            case "ship-all" -> "ship-dialog";
            case "remove-all" -> "remove-dialog";
            default -> key + "-dialog";
        };
    }
}
