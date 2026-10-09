package pl.commercelink.warehouse.builtin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.taxonomy.Categories;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The state of the warehouse list, read from and written back to the address (spec §4.3). Every link of the page is built
 * here so changing one filter never loses the others; unknown values are ignored so old bookmarks and redirects open.
 * The status list is never empty: no parameter means the default status, unticking the last one means all of them. The
 * Status menu sends {@value #STATUS_MENU} with its checkboxes, which tells "nothing ticked" apart from "no parameter".
 */
public record WarehouseListQuery(boolean wms, List<FulfilmentStatus> statuses, List<String> categories, String q,
                                 Sort sort, Direction dir, int page) {

    public static final String PATH = "/dashboard/warehouse";
    public static final int PAGE_SIZE = 50;
    public static final int MAX_Q = 100;
    public static final String NO_CATEGORY = "none";
    public static final String STATUS_MENU = "statusesMenu";
    private static final String ALL = "all";

    public WarehouseListQuery {
        List<FulfilmentStatus> visible = WarehouseStatuses.visible(wms);
        Set<FulfilmentStatus> chosen = statuses == null ? Set.of() : new HashSet<>(statuses);
        statuses = visible.stream().filter(chosen::contains).toList();
        if (statuses.isEmpty()) {
            statuses = WarehouseStatuses.defaults(wms);
        }
        categories = categories == null ? List.of()
                : categories.stream().map(StringUtils::trimToNull).filter(Objects::nonNull)
                        .map(c -> Categories.UNCATEGORIZED.equals(c) ? NO_CATEGORY : c).distinct().toList();
        q = q == null ? null : StringUtils.left(StringUtils.trimToNull(q), MAX_Q);
        page = Math.max(1, page);
    }

    public enum Sort {
        NAME("name"), CATEGORY("category"), QTY("qty"), COST("cost"), STATUS("status");
        private final String param;
        Sort(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Sort> parse(String v) { return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(trim(v))).findFirst(); }
    }

    public enum Direction {
        ASC, DESC;
        public String param() { return name().toLowerCase(Locale.ROOT); }
        static Optional<Direction> parse(String v) { return Arrays.stream(values()).filter(d -> d.name().equalsIgnoreCase(trim(v))).findFirst(); }
        Direction flipped() { return this == ASC ? DESC : ASC; }
    }

    /** A store with an external WMS opens on "to receive" instead of "in stock"; no store reads as no WMS. */
    public static boolean usesWms(Store store) {
        return store != null && store.hasIntegration(IntegrationType.WMS_PROVIDER);
    }

    /** The list view these parameters address in this store, as the list itself reads them. */
    public static WarehouseListQuery parse(MultiValueMap<String, String> params, Store store) {
        return parse(params, usesWms(store));
    }

    public static WarehouseListQuery parse(MultiValueMap<String, String> params, boolean wms) {
        List<String> raw = values(params, "statuses");
        boolean all = "true".equalsIgnoreCase(trim(params.getFirst("showAll"))) || raw.stream().anyMatch(v -> ALL.equalsIgnoreCase(trim(v)))
                || (params.containsKey(STATUS_MENU) && raw.isEmpty());
        List<FulfilmentStatus> statuses = all ? WarehouseStatuses.visible(wms)
                : raw.stream().map(WarehouseListQuery::status).flatMap(Optional::stream).toList();
        return new WarehouseListQuery(wms, statuses, values(params, "categories"), params.getFirst("q"),
                Sort.parse(params.getFirst("sort")).orElse(null), Direction.parse(params.getFirst("dir")).orElse(null),
                page(params.getFirst("page")));
    }

    public boolean isDefaultStatuses() { return statuses.equals(WarehouseStatuses.defaults(wms)); }

    public boolean isAllStatuses() { return statuses.equals(WarehouseStatuses.visible(wms)); }

    /** The default status narrows the list too: the page opens with it as a chip that can be cleared (user decision 2026-10-09). */
    public boolean isFiltered() { return !isAllStatuses() || !categories.isEmpty() || q != null; }

    public Sort effectiveSort() { return sort != null ? sort : Sort.CATEGORY; }

    public Direction effectiveDir() { return dir != null ? dir : Direction.ASC; }

    public WarehouseListQuery toggleStatus(FulfilmentStatus s) {
        List<FulfilmentStatus> next = new ArrayList<>(statuses);
        if (!next.remove(s)) {
            next.add(s);
        }
        return new WarehouseListQuery(wms, next.isEmpty() ? WarehouseStatuses.visible(wms) : next, categories, q, sort, dir, 1);
    }

    public WarehouseListQuery withoutStatus(FulfilmentStatus s) {
        return statuses.contains(s) ? toggleStatus(s) : this;
    }

    /** A tile: exactly its statuses, every other narrowing dropped (orders list rule, app#238). */
    public WarehouseListQuery withStatuses(List<FulfilmentStatus> next) {
        return new WarehouseListQuery(wms, next, List.of(), null, sort, dir, 1);
    }

    public WarehouseListQuery toggleCategory(String c) {
        List<String> next = new ArrayList<>(categories);
        if (!next.remove(c)) {
            next.add(c);
        }
        return new WarehouseListQuery(wms, statuses, next, q, sort, dir, 1);
    }

    public WarehouseListQuery withoutCategory(String c) {
        List<String> next = new ArrayList<>(categories);
        next.remove(c);
        return new WarehouseListQuery(wms, statuses, next, q, sort, dir, 1);
    }

    /** Where a bulk action leads: the action's target status in the same search, categories and sort, on page one. */
    public WarehouseListQuery afterAction(FulfilmentStatus target) {
        return new WarehouseListQuery(wms, List.of(target), categories, q, sort, dir, 1);
    }

    public WarehouseListQuery withQ(String newQ) { return new WarehouseListQuery(wms, statuses, categories, newQ, sort, dir, 1); }

    public WarehouseListQuery withPage(int n) { return new WarehouseListQuery(wms, statuses, categories, q, sort, dir, n); }

    /** Nothing narrows the list: every status, which is the explicit statuses=all because a bare address opens the default. */
    public WarehouseListQuery cleared() { return new WarehouseListQuery(wms, WarehouseStatuses.visible(wms), List.of(), null, sort, dir, 1); }

    public WarehouseListQuery toggleSort(Sort column) {
        Direction next = effectiveSort() == column ? effectiveDir().flipped() : Direction.ASC;
        return new WarehouseListQuery(wms, statuses, categories, q, column, next, 1);
    }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (isAllStatuses() && !isDefaultStatuses()) {
            parts.add("statuses=" + ALL);
        } else if (!isDefaultStatuses()) {
            statuses.forEach(s -> parts.add("statuses=" + s.name()));
        }
        categories.forEach(c -> parts.add("categories=" + encode(c)));
        if (q != null) parts.add("q=" + encode(q));
        if (sort != null) parts.add("sort=" + sort.param());
        if (dir != null) parts.add("dir=" + dir.param());
        if (page > 1) parts.add("page=" + page);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static Optional<FulfilmentStatus> status(String value) {
        return Arrays.stream(FulfilmentStatus.values()).filter(s -> s.name().equalsIgnoreCase(trim(value))).findFirst();
    }

    private static List<String> values(MultiValueMap<String, String> params, String name) {
        List<String> raw = params.get(name);
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).toList();
    }

    private static int page(String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
