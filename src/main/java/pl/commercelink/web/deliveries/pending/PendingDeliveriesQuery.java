package pl.commercelink.web.deliveries.pending;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The state of the pending deliveries page, read from and written back to the address (spec §4.1). A narrowing link
 * (supplier, search) drops the tab so the page lands on the tab that has results; a widening one keeps it.
 */
public record PendingDeliveriesQuery(Kind kind, List<String> providers, String q) {

    public static final int MAX_Q = 100;

    public PendingDeliveriesQuery {
        providers = providers == null ? List.of()
                : providers.stream().map(StringUtils::trimToNull).filter(Objects::nonNull).distinct().toList();
        q = normalizeQ(q);
    }

    public enum Kind {
        WAREHOUSE("warehouse"), DROPSHIP("dropship");
        private final String param;
        Kind(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Kind> parse(String v) { return Arrays.stream(values()).filter(k -> k.param.equalsIgnoreCase(trim(v))).findFirst(); }
    }

    public static PendingDeliveriesQuery parse(MultiValueMap<String, String> params) {
        List<String> providers = params.get("provider");
        return new PendingDeliveriesQuery(
                Kind.parse(params.getFirst("kind")).orElse(null),
                providers == null ? List.of() : providers.stream().filter(Objects::nonNull).toList(),
                params.getFirst("q"));
    }

    public boolean isFiltered() { return !providers.isEmpty() || q != null; }

    public int activeFilterCount() { return providers.size() + (q != null ? 1 : 0); }

    public PendingDeliveriesQuery withKind(Kind k) { return new PendingDeliveriesQuery(k, providers, q); }
    public PendingDeliveriesQuery withProviders(List<String> p) { return new PendingDeliveriesQuery(kind, p, q); }
    public PendingDeliveriesQuery toggleProvider(String p) { return new PendingDeliveriesQuery(null, toggled(providers, p), q); }
    public PendingDeliveriesQuery withoutProvider(String p) { return new PendingDeliveriesQuery(kind, without(providers, p), q); }
    public PendingDeliveriesQuery withQ(String newQ) { return new PendingDeliveriesQuery(null, providers, newQ); }
    public PendingDeliveriesQuery withoutQ() { return new PendingDeliveriesQuery(kind, providers, null); }
    public PendingDeliveriesQuery cleared() { return new PendingDeliveriesQuery(kind, List.of(), null); }

    public String href(String path) {
        List<String> parts = new ArrayList<>();
        if (kind != null) parts.add("kind=" + kind.param());
        providers.forEach(p -> parts.add("provider=" + encode(p)));
        if (q != null) parts.add("q=" + encode(q));
        return parts.isEmpty() ? path : path + "?" + String.join("&", parts);
    }

    private static List<String> toggled(List<String> list, String value) {
        List<String> next = new ArrayList<>(list);
        if (!next.remove(value)) next.add(value);
        return next;
    }

    private static List<String> without(List<String> list, String value) {
        List<String> next = new ArrayList<>(list);
        next.remove(value);
        return next;
    }

    private static String normalizeQ(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.left(trimmed, MAX_Q);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
