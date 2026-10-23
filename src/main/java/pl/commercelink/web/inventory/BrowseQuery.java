package pl.commercelink.web.inventory;

import org.springframework.util.MultiValueMap;
import pl.commercelink.inventory.BrowseCriteria;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Everything the browse list shows, read from and written to its address so it survives reloads and bookmarks. */
public record BrowseQuery(String category, List<String> suppliers, String q2, BrowseCriteria.Sort sort, boolean descending,
                          int page) {

    public static final String PATH = "/dashboard/inventory";
    public static final String FRAGMENT_PATH = "/dashboard/inventory/browse";
    public static final int PAGE_SIZE = 50;
    public static final int MIN_TEXT = 3;
    public static final int MAX_TEXT = 100;
    // The largest page whose offset still fits an int; anything beyond is past the end and shows the last page anyway.
    static final int MAX_PAGE = Integer.MAX_VALUE / PAGE_SIZE;

    public static BrowseQuery start() {
        return new BrowseQuery(null, List.of(), null, BrowseCriteria.Sort.NAME, false, 1);
    }

    public static BrowseQuery parse(MultiValueMap<String, String> params) {
        List<String> suppliers = new ArrayList<>(new LinkedHashSet<>(
                Optional.ofNullable(params.get("supplier")).orElse(List.of()).stream()
                        .map(BrowseQuery::trim)
                        .filter(value -> !value.isEmpty())
                        .toList()));
        return new BrowseQuery(
                emptyToNull(trim(params.getFirst("cat"))),
                List.copyOf(suppliers),
                normalizeText(params.getFirst("q2")),
                parseSort(params.getFirst("sort")),
                "desc".equalsIgnoreCase(trim(params.getFirst("dir"))),
                parsePage(params.getFirst("page")));
    }

    public boolean isStart() {
        return category == null && q2 == null;
    }

    public boolean textTooShort() {
        return q2 != null && q2.length() < MIN_TEXT;
    }

    public BrowseQuery withCategory(String newCategory) {
        return new BrowseQuery(emptyToNull(trim(newCategory)), suppliers, q2, sort, descending, 1);
    }

    public BrowseQuery withoutText() {
        return new BrowseQuery(category, suppliers, null, sort, descending, 1);
    }

    public BrowseQuery withoutSupplier(String supplier) {
        return new BrowseQuery(category, suppliers.stream().filter(s -> !s.equals(supplier)).toList(), q2, sort,
                descending, 1);
    }

    public BrowseQuery cleared() {
        return new BrowseQuery(category, List.of(), null, sort, descending, 1);
    }

    /** The current column flips its direction; another starts ascending, except quantity, which starts with the most. */
    public BrowseQuery toggleSort(BrowseCriteria.Sort column) {
        boolean nextDescending = sort == column ? !descending : column == BrowseCriteria.Sort.QTY;
        return new BrowseQuery(category, suppliers, q2, column, nextDescending, 1);
    }

    public BrowseQuery withPage(int newPage) {
        return new BrowseQuery(category, suppliers, q2, sort, descending, clampPage(newPage));
    }

    public BrowseCriteria toCriteria(Set<String> categoryIds) {
        return BrowseCriteria.all()
                .inCategories(categoryIds)
                .fromSuppliers(Set.copyOf(suppliers))
                .withText(textTooShort() ? null : q2)
                .sortedBy(sort, descending)
                .page((page - 1) * PAGE_SIZE, PAGE_SIZE);
    }

    public record Param(String name, String value) {
    }

    /** The list's state as form fields, minus the one the form itself sets; a form always starts again at page one. */
    public List<Param> params(String except) {
        List<Param> params = new ArrayList<>();
        if (category != null) {
            params.add(new Param("cat", category));
        }
        suppliers.forEach(supplier -> params.add(new Param("supplier", supplier)));
        if (q2 != null) {
            params.add(new Param("q2", q2));
        }
        if (sort != BrowseCriteria.Sort.NAME) {
            params.add(new Param("sort", sort.name().toLowerCase(Locale.ROOT)));
        }
        if (descending) {
            params.add(new Param("dir", "desc"));
        }
        return except == null ? params : params.stream().filter(param -> !param.name().equals(except)).toList();
    }

    public String href() {
        List<String> parts = new ArrayList<>(params(null).stream()
                .map(param -> param.name() + "=" + encode(param.value()))
                .toList());
        if (page > 1) {
            parts.add("page=" + page);
        }
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static BrowseCriteria.Sort parseSort(String value) {
        return switch (trim(value).toLowerCase(Locale.ROOT)) {
            case "cost" -> BrowseCriteria.Sort.COST;
            case "qty" -> BrowseCriteria.Sort.QTY;
            default -> BrowseCriteria.Sort.NAME;
        };
    }

    private static int parsePage(String value) {
        String digits = trim(value);
        if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
            // Too many digits for any number is still a page past the end, not the first page.
            return digits.length() > 10 ? MAX_PAGE : clampPage(Long.parseLong(digits));
        }
        try {
            return clampPage(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int clampPage(long page) {
        return (int) Math.min(Math.max(1, page), MAX_PAGE);
    }

    private static String normalizeText(String value) {
        String text = emptyToNull(trim(value));
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
