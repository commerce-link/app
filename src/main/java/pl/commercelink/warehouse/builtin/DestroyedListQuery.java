package pl.commercelink.warehouse.builtin;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.ItemCondition;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * The state of the destroyed archive, read from and written back to the address (spec §6). The tiles set a period, the
 * Destroyed menu a custom range that wins over it; unknown values are ignored so old bookmarks still open.
 */
public record DestroyedListQuery(Period period, LocalDate from, LocalDate to, List<String> categories, List<ItemCondition> conditions, String q,
                                 Sort sort, WarehouseListQuery.Direction dir, int page) {

    public static final String PATH = "/dashboard/warehouse/items/destroyed";
    public static final int PAGE_SIZE = 50;

    public DestroyedListQuery {
        period = period == null ? Period.ALL : period;
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        categories = categories == null ? List.of()
                : categories.stream().map(StringUtils::trimToNull).filter(Objects::nonNull)
                        .map(c -> Categories.UNCATEGORIZED.equals(c) ? WarehouseListQuery.NO_CATEGORY : c).distinct().toList();
        // the menu's order (Sealed, OpenBox, Damaged), whatever order the address gave
        conditions = conditions == null ? List.of() : conditions.stream().filter(Objects::nonNull).distinct().sorted().toList();
        q = q == null ? null : StringUtils.left(StringUtils.trimToNull(q), WarehouseListQuery.MAX_Q);
        page = Math.max(1, page);
    }

    public enum Period {
        ALL(""), MONTH("month"), PREVIOUS("previous"), YEAR("year");
        private final String param;
        Period(String param) { this.param = param; }
        public String param() { return param; }

        /** Inclusive first and last day; null for ALL. */
        public LocalDate[] range(LocalDate today) {
            return switch (this) {
                case ALL -> null;
                case MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
                case PREVIOUS -> {
                    LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                    yield new LocalDate[]{first, first.withDayOfMonth(first.lengthOfMonth())};
                }
                case YEAR -> new LocalDate[]{today.minusMonths(12).plusDays(1), today};
            };
        }

        static Period parse(String v) {
            return Arrays.stream(values()).filter(p -> !p.param.isEmpty() && p.param.equalsIgnoreCase(StringUtils.trim(v))).findFirst().orElse(ALL);
        }
    }

    public enum Sort {
        NAME("name"), CATEGORY("category"), QTY("qty"), COST("cost"), DATE("date");
        private final String param;
        Sort(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Sort> parse(String v) { return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(StringUtils.trim(v))).findFirst(); }
    }

    public static DestroyedListQuery parse(MultiValueMap<String, String> params) {
        List<String> categories = params.get("categories");
        return new DestroyedListQuery(Period.parse(params.getFirst("period")), date(params.getFirst("from")), date(params.getFirst("to")),
                categories == null ? List.of() : categories, conditions(params.get("conditions")), params.getFirst("q"),
                Sort.parse(params.getFirst("sort")).orElse(null),
                Arrays.stream(WarehouseListQuery.Direction.values()).filter(d -> d.name().equalsIgnoreCase(StringUtils.trim(params.getFirst("dir")))).findFirst().orElse(null),
                page(params.getFirst("page")));
    }

    public Sort effectiveSort() { return sort != null ? sort : Sort.DATE; }

    public WarehouseListQuery.Direction effectiveDir() {
        if (dir != null) return dir;
        return effectiveSort() == Sort.DATE ? WarehouseListQuery.Direction.DESC : WarehouseListQuery.Direction.ASC;
    }

    public boolean hasCustomRange() { return from != null || to != null; }

    public boolean isFiltered() { return period != Period.ALL || hasCustomRange() || !categories.isEmpty() || !conditions.isEmpty() || q != null; }

    public boolean includes(LocalDate destroyedAt, LocalDate today) {
        if (hasCustomRange()) {
            return destroyedAt != null && (from == null || !destroyedAt.isBefore(from)) && (to == null || !destroyedAt.isAfter(to));
        }
        LocalDate[] range = period.range(today);
        return range == null || (destroyedAt != null && !destroyedAt.isBefore(range[0]) && !destroyedAt.isAfter(range[1]));
    }

    public DestroyedListQuery withPeriod(Period p) { return new DestroyedListQuery(p, null, null, categories, conditions, q, sort, dir, 1); }

    public DestroyedListQuery withRange(LocalDate f, LocalDate t) { return new DestroyedListQuery(Period.ALL, f, t, categories, conditions, q, sort, dir, 1); }

    public DestroyedListQuery toggleCategory(String c) {
        List<String> next = new ArrayList<>(categories);
        if (!next.remove(c)) next.add(c);
        return new DestroyedListQuery(period, from, to, next, conditions, q, sort, dir, 1);
    }

    public DestroyedListQuery withoutCategory(String c) {
        List<String> next = new ArrayList<>(categories);
        next.remove(c);
        return new DestroyedListQuery(period, from, to, next, conditions, q, sort, dir, 1);
    }

    public DestroyedListQuery toggleCondition(ItemCondition c) {
        List<ItemCondition> next = new ArrayList<>(conditions);
        if (!next.remove(c)) next.add(c);
        return new DestroyedListQuery(period, from, to, categories, next, q, sort, dir, 1);
    }

    public DestroyedListQuery withoutCondition(ItemCondition c) {
        List<ItemCondition> next = new ArrayList<>(conditions);
        next.remove(c);
        return new DestroyedListQuery(period, from, to, categories, next, q, sort, dir, 1);
    }

    /** An item stored without a condition is a sealed one (the item page shows it as "Fabrycznie nowy"). */
    public boolean includesCondition(ItemCondition c) {
        return conditions.isEmpty() || conditions.contains(c == null ? ItemCondition.Sealed : c);
    }

    public DestroyedListQuery withQ(String newQ) { return new DestroyedListQuery(period, from, to, categories, conditions, newQ, sort, dir, 1); }

    public DestroyedListQuery withPage(int n) { return new DestroyedListQuery(period, from, to, categories, conditions, q, sort, dir, n); }

    public DestroyedListQuery cleared() { return new DestroyedListQuery(Period.ALL, null, null, List.of(), List.of(), null, sort, dir, 1); }

    public DestroyedListQuery toggleSort(Sort column) {
        WarehouseListQuery.Direction next = effectiveSort() == column ? effectiveDir().flipped()
                : column == Sort.DATE ? WarehouseListQuery.Direction.DESC : WarehouseListQuery.Direction.ASC;
        return new DestroyedListQuery(period, from, to, categories, conditions, q, column, next, 1);
    }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (period != Period.ALL) parts.add("period=" + period.param());
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        categories.forEach(c -> parts.add("categories=" + encode(c)));
        conditions.forEach(c -> parts.add("conditions=" + c.name()));
        if (q != null) parts.add("q=" + encode(q));
        if (sort != null) parts.add("sort=" + sort.param());
        if (dir != null) parts.add("dir=" + dir.param());
        if (page > 1) parts.add("page=" + page);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static List<ItemCondition> conditions(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(StringUtils::trimToNull).filter(Objects::nonNull)
                .flatMap(v -> Arrays.stream(ItemCondition.values()).filter(c -> c.name().equalsIgnoreCase(v))).toList();
    }

    private static LocalDate date(String value) {
        try {
            return StringUtils.isBlank(value) ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int page(String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
