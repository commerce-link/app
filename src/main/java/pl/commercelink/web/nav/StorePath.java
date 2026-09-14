package pl.commercelink.web.nav;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StorePath {

    private static final Pattern STORE_PATH = Pattern.compile("/dashboard/store/([^/]+)(/.*)?");

    private static final Set<String> RESERVED_SEGMENTS = Set.of(
            "branding", "categories", "company-details", "create", "email-templates", "fulfilment",
            "integrations", "invoicing", "manual-supplier", "marketplaces", "notification", "payments",
            "report", "rma", "rma-centers", "shipping", "warehouse");

    private StorePath() {
    }

    public static String storeIdIn(String path) {
        Matcher matcher = STORE_PATH.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        String candidate = matcher.group(1);
        return RESERVED_SEGMENTS.contains(candidate) ? null : candidate;
    }

    public static String stripStorePrefix(String path) {
        String storeId = storeIdIn(path);
        if (storeId == null) {
            return path;
        }
        String rest = path.substring(("/dashboard/store/" + storeId).length());
        return rest.isEmpty() ? "/dashboard/store" : "/dashboard" + rest;
    }
}
