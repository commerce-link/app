package pl.commercelink.web.stores;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The state of the stores list carried in its address; every link the page builds goes through here. */
public record StoresListQuery(Segment segment, String q, Sort sort, boolean ascending, String delete) {

    public static final String PATH = "/dashboard/stores";

    public enum Segment {
        ALL, FULL, TRIAL, INACTIVE, DEMO;

        public String param() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Sort {
        CREATED(false), NAME(true);

        private final boolean defaultAscending;

        Sort(boolean defaultAscending) {
            this.defaultAscending = defaultAscending;
        }

        public String param() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean defaultAscending() {
            return defaultAscending;
        }
    }

    public static StoresListQuery defaults() {
        return new StoresListQuery(Segment.ALL, null, Sort.CREATED, false, null);
    }

    public static StoresListQuery of(String status, String q, String sort, String dir, String delete) {
        Sort parsedSort = parse(Sort.class, sort, Sort.CREATED);
        boolean ascending = "asc".equalsIgnoreCase(trim(dir)) || (!"desc".equalsIgnoreCase(trim(dir)) && parsedSort.defaultAscending());
        return new StoresListQuery(parse(Segment.class, status, Segment.ALL), blankToNull(q), parsedSort, ascending,
                blankToNull(delete));
    }

    public StoresListQuery withSegment(Segment newSegment) {
        return new StoresListQuery(newSegment, q, sort, ascending, null);
    }

    public StoresListQuery withQ(String newQ) {
        return new StoresListQuery(segment, blankToNull(newQ), sort, ascending, null);
    }

    public StoresListQuery withSort(Sort column) {
        boolean newAscending = column == sort ? !ascending : column.defaultAscending();
        return new StoresListQuery(segment, q, column, newAscending, null);
    }

    public StoresListQuery cleared() {
        return new StoresListQuery(Segment.ALL, null, sort, ascending, null);
    }

    public String ariaSort(Sort column) {
        if (column != sort) {
            return "none";
        }
        return ascending ? "ascending" : "descending";
    }

    public String nameSortHref() {
        return withSort(Sort.NAME).href();
    }

    public String createdSortHref() {
        return withSort(Sort.CREATED).href();
    }

    public String nameAriaSort() {
        return ariaSort(Sort.NAME);
    }

    public String createdAriaSort() {
        return ariaSort(Sort.CREATED);
    }

    public String href() {
        List<String> params = new ArrayList<>();
        if (segment != Segment.ALL) {
            params.add("status=" + segment.param());
        }
        if (q != null) {
            params.add("q=" + encode(q));
        }
        if (sort != Sort.CREATED) {
            params.add("sort=" + sort.param());
        }
        if (ascending != sort.defaultAscending()) {
            params.add("dir=" + (ascending ? "asc" : "desc"));
        }
        return params.isEmpty() ? PATH : PATH + "?" + String.join("&", params);
    }

    public String hrefWithDelete(String storeId) {
        return withDeleteParam(href(), storeId);
    }

    /** Back to the list the action came from, never to another address (no open redirect, no header injection). */
    public static String safeBack(String back) {
        if (back == null || back.contains("\r") || back.contains("\n")
                // RedirectView expands {…} as URI templates, which would fail after the action already happened
                || back.contains("{") || back.contains("}")) {
            return PATH;
        }
        return back.equals(PATH) || back.startsWith(PATH + "?") ? back : PATH;
    }

    public static String withDeleteParam(String back, String storeId) {
        String safe = safeBack(back);
        return safe + (safe.contains("?") ? "&" : "?") + "delete=" + encode(storeId);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return fallback;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(trimmed)) {
                return constant;
            }
        }
        return fallback;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
