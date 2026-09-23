package pl.commercelink.web.catalog;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The filter of the category page as its address carries it -- {@code status}, {@code feature} ("Pokaż"), {@code label}
 * and {@code q} (the search) -- taken back from a request and echoed into the links and redirects that should return to
 * it. Nothing else of a request ever reaches a redirect: every value is re-validated here and form-encoded, so neither
 * another address nor a header can be smuggled in through it.
 *
 * <p>The status is always stated (an unknown one becomes the active products); the other three only when they narrow
 * the list.
 */
public record CategoryFilter(String status, String feature, String label, String search) {

    public static final String DEFAULT_STATUS = "active";

    private static final String ALL = "all";

    /** A label or a search longer than this is not something the page produced; it is dropped rather than echoed. */
    private static final int MAX_TEXT = 200;

    public static CategoryFilter of(String status, String feature, String label, String search) {
        return new CategoryFilter(
                ProductStatus.fromFilter(status) != null || ALL.equals(status) ? status : DEFAULT_STATUS,
                feature != null && CategoryPageModel.FEATURES.contains(feature) ? feature : null,
                ALL.equals(label) ? null : text(label),
                text(search));
    }

    /** The query string, starting with {@code ?}, that opens the category page on this filter. */
    public String query() {
        StringBuilder query = new StringBuilder("?status=").append(encode(status));
        append(query, "feature", feature);
        append(query, "label", label);
        append(query, "q", search);
        return query.toString();
    }

    private static void append(StringBuilder query, String name, String value) {
        if (value != null) {
            query.append('&').append(name).append('=').append(encode(value));
        }
    }

    private static String text(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null || trimmed.length() > MAX_TEXT ? null : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
