package pl.commercelink.web.inventory;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where a catalog form may send the operator back to after a save. Only the inventory page itself: any other value
 * would turn the redirect into an open redirect.
 */
public final class InventoryReturnTo {

    private static final Pattern INVENTORY_PAGE = Pattern.compile("^/dashboard/inventory(\\?\\S*)?$");

    private InventoryReturnTo() {
    }

    public static Optional<String> safe(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        if (value.contains("//") || value.contains("\\") || !INVENTORY_PAGE.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
