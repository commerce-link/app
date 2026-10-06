package pl.commercelink.web.inventory;

import org.springframework.util.MultiValueMap;
import pl.commercelink.inventory.BrowseCriteria;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Everything the browse list shows, read from and written to its address so it survives reloads and bookmarks. */
public record BrowseQuery(String category, List<String> suppliers, BrowseCriteria.Stock stock, CatalogFilter catalog,
                          String q2, BrowseCriteria.Sort sort, boolean descending, int page) {

    public static final String PATH = "/dashboard/inventory";
    public static final String FRAGMENT_PATH = "/dashboard/inventory/browse";
    public static final int PAGE_SIZE = 50;
    public static final int MIN_TEXT = 3;
    public static final int MAX_TEXT = 100;

    public enum CatalogFilter {
        ALL, OUT, IN, UNMATCHED;

        String param() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<CatalogFilter> parse(String value) {
            return Arrays.stream(values()).filter(f -> f != ALL && f.param().equalsIgnoreCase(trim(value))).findFirst();
        }
    }

    public static BrowseQuery start() {
        return new BrowseQuery(null, List.of(), BrowseCriteria.Stock.ALL, CatalogFilter.ALL, null,
                BrowseCriteria.Sort.NAME, false, 1);
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
                parseStock(params.getFirst("stock")),
                CatalogFilter.parse(params.getFirst("catalog")).orElse(CatalogFilter.ALL),
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
        return new BrowseQuery(emptyToNull(trim(newCategory)), suppliers, stock, catalog, q2, sort, descending, 1);
    }

    public BrowseQuery withoutText() {
        return new BrowseQuery(category, suppliers, stock, catalog, null, sort, descending, 1);
    }

    public BrowseQuery withoutSupplier(String supplier) {
        return new BrowseQuery(category, suppliers.stream().filter(s -> !s.equals(supplier)).toList(), stock, catalog,
                q2, sort, descending, 1);
    }

    public BrowseQuery withStock(BrowseCriteria.Stock newStock) {
        return new BrowseQuery(category, suppliers, newStock, catalog, q2, sort, descending, 1);
    }

    public BrowseQuery withCatalog(CatalogFilter newCatalog) {
        return new BrowseQuery(category, suppliers, stock, newCatalog, q2, sort, descending, 1);
    }

    public BrowseQuery cleared() {
        return new BrowseQuery(category, List.of(), BrowseCriteria.Stock.ALL, CatalogFilter.ALL, null, sort, descending, 1);
    }

    public BrowseQuery toggleSort(BrowseCriteria.Sort column) {
        boolean nextDescending = sort == column && !descending;
        return new BrowseQuery(category, suppliers, stock, catalog, q2, column, nextDescending, 1);
    }

    public BrowseQuery withPage(int newPage) {
        return new BrowseQuery(category, suppliers, stock, catalog, q2, sort, descending, Math.max(1, newPage));
    }

    public BrowseCriteria toCriteria(Set<String> categoryIds) {
        return BrowseCriteria.all()
                .inCategories(categoryIds)
                .fromSuppliers(Set.copyOf(suppliers))
                .withStock(stock)
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
        if (stock == BrowseCriteria.Stock.IN_STOCK) {
            params.add(new Param("stock", "in-stock"));
        } else if (stock == BrowseCriteria.Stock.ON_ORDER) {
            params.add(new Param("stock", "on-order"));
        }
        if (catalog != CatalogFilter.ALL) {
            params.add(new Param("catalog", catalog.param()));
        }
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
        return hrefWith(null);
    }

    /** The list's address with an extra, already encoded query appended, e.g. the parameters that open a dialog on it. */
    public String hrefWith(String extraQuery) {
        List<String> parts = new ArrayList<>(params(null).stream()
                .map(param -> param.name() + "=" + encode(param.value()))
                .toList());
        if (page > 1) {
            parts.add("page=" + page);
        }
        if (extraQuery != null && !extraQuery.isEmpty()) {
            parts.add(extraQuery);
        }
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static BrowseCriteria.Stock parseStock(String value) {
        return switch (trim(value).toLowerCase(Locale.ROOT)) {
            case "in-stock" -> BrowseCriteria.Stock.IN_STOCK;
            case "on-order" -> BrowseCriteria.Stock.ON_ORDER;
            default -> BrowseCriteria.Stock.ALL;
        };
    }

    private static BrowseCriteria.Sort parseSort(String value) {
        return switch (trim(value).toLowerCase(Locale.ROOT)) {
            case "cost" -> BrowseCriteria.Sort.COST;
            case "qty" -> BrowseCriteria.Sort.QTY;
            default -> BrowseCriteria.Sort.NAME;
        };
    }

    private static int parsePage(String value) {
        try {
            return Math.max(1, Integer.parseInt(trim(value)));
        } catch (NumberFormatException e) {
            return 1;
        }
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
