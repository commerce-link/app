package pl.commercelink.warehouse.builtin;

import pl.commercelink.orders.FulfilmentStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Where the item page leads back to: the warehouse list or the destroyed archive exactly as the operator left it
 * (filters, page, sort), carried in "back". The address comes from the request, so only those two lists are accepted;
 * anything else falls back to the list the item belongs on.
 */
record ItemBack(String href, String labelKey) {

    static final String LIST = "/dashboard/warehouse";
    static final String ARCHIVE = "/dashboard/warehouse/items/destroyed";
    private static final Pattern SAFE = Pattern.compile("^/dashboard/warehouse(/items/destroyed)?(\\?[^\\s]*)?$");

    static ItemBack resolve(String back, FulfilmentStatus status) {
        String safe = safe(back);
        if (safe != null) {
            return new ItemBack(safe, safe.startsWith(ARCHIVE) ? "warehouse.destroyed.title" : "nav.warehouse");
        }
        return status == FulfilmentStatus.Destroyed
                ? new ItemBack(ARCHIVE, "warehouse.destroyed.title")
                : new ItemBack(LIST, "nav.warehouse");
    }

    static String safe(String back) {
        return back != null && SAFE.matcher(back).matches() && !back.contains("//") && !back.contains("..") ? back : null;
    }

    static String itemHref(String itemId, String back) {
        String path = "/dashboard/warehouse/items/" + itemId;
        String safe = safe(back);
        return safe == null ? path : path + "?back=" + URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }
}
