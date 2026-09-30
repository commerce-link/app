package pl.commercelink.web.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.supplier.SupplierChoice;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * The state of the deliveries list, read from and written back to the address (spec §6.1). Every link on the page is
 * built here, so changing one parameter never loses the others; unknown values are ignored so old bookmarks open.
 */
public record DeliveryListQuery(Scope scope, DeliveryAttention focus, List<DeliveryListState> states, List<String> providers,
                                List<Settle> settle, LocalDate from, LocalDate to, boolean allHistory, String q,
                                Sort sort, Direction dir, int page) {

    public static final String PATH = "/dashboard/deliveries";
    public static final int PAGE_SIZE = 25;
    public static final int MAX_Q = 100;
    public static final int HISTORY_DAYS = 90;

    public DeliveryListQuery {
        // a tile decides the scope it counts in (?focus=invoice alone opens the received deliveries)
        scope = focus != null ? focus.scope() : scope == null ? Scope.TRANSIT : scope;
        states = states == null || states.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(states));
        providers = providers == null ? List.of() : providers.stream().filter(StringUtils::isNotBlank).distinct().toList();
        settle = settle == null || settle.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(settle));
        page = Math.max(1, page);
    }

    public enum Scope {
        TRANSIT("transit"), RECEIVED("received"), ALL("all");
        private final String param;
        Scope(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Scope> parse(String v) { return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(trim(v))).findFirst(); }
        public boolean includesTransit() { return this != RECEIVED; }
        public boolean includesHistory() { return this != TRANSIT; }
    }

    public enum Settle {
        NO_INVOICE("noInvoice"), NO_SYNC("noSync"), UNPAID("unpaid");
        private final String param;
        Settle(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Settle> parse(String v) { return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(trim(v))).findFirst(); }
    }

    public enum Sort {
        NUMBER("number"), SUPPLIER("supplier"), ORDERED("ordered"), DUE("due"), STATUS("status"), COST("cost");
        private final String param;
        Sort(String param) { this.param = param; }
        public String param() { return param; }
        static Optional<Sort> parse(String v) { return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(trim(v))).findFirst(); }
    }

    public enum Direction {
        ASC, DESC;
        static Optional<Direction> parse(String v) { return Arrays.stream(values()).filter(d -> d.name().equalsIgnoreCase(trim(v))).findFirst(); }
        public String param() { return name().toLowerCase(); }
        Direction flipped() { return this == ASC ? DESC : ASC; }
    }

    public static DeliveryListQuery parse(MultiValueMap<String, String> params) {
        return new DeliveryListQuery(
                Scope.parse(params.getFirst("scope")).orElse(Scope.TRANSIT),
                DeliveryAttention.parse(params.getFirst("focus")).orElse(null),
                values(params, "state").stream().map(DeliveryListState::parse).flatMap(Optional::stream).toList(),
                values(params, "provider").stream().map(StringUtils::trimToNull).filter(Objects::nonNull).toList(),
                values(params, "settle").stream().map(Settle::parse).flatMap(Optional::stream).toList(),
                date(params.getFirst("from")), date(params.getFirst("to")),
                "all".equalsIgnoreCase(trim(params.getFirst("period"))),
                normalizeQ(params.getFirst("q")),
                Sort.parse(params.getFirst("sort")).orElse(null),
                Direction.parse(params.getFirst("dir")).orElse(null),
                parsePage(params.getFirst("page")));
    }

    /** The filter form of the old list (before the redesign): bookmarks keep working (spec §6.1). */
    public static Optional<String> legacyRedirect(MultiValueMap<String, String> params) {
        Set<String> legacy = Set.of("showArchived", "showWithoutInvoice", "showWithoutSync", "showAwaitingApproval",
                "deliveryId", "externalDeliveryId", "counterpartyShortcut", "orderedAtStart", "orderedAtEnd", "providerCustom");
        if (params.keySet().stream().noneMatch(legacy::contains)) {
            return Optional.empty();
        }
        boolean archived = flag(params, "showArchived");
        List<Settle> settle = new ArrayList<>();
        if (flag(params, "showWithoutInvoice")) settle.add(Settle.NO_INVOICE);
        if (flag(params, "showWithoutSync")) settle.add(Settle.NO_SYNC);
        List<DeliveryListState> states = flag(params, "showAwaitingApproval") ? List.of(DeliveryListState.AWAITING_APPROVAL) : List.of();
        String provider = trim(params.getFirst("provider"));
        if (SupplierChoice.CUSTOM.equals(provider)) {
            provider = StringUtils.trimToNull(params.getFirst("providerCustom"));
        }
        String q = firstNonBlank(params.getFirst("deliveryId"), params.getFirst("externalDeliveryId"), params.getFirst("counterpartyShortcut"));
        // a number or counterparty searched in the old form could be anywhere in the history
        Scope scope = archived || q != null ? Scope.ALL : Scope.TRANSIT;
        boolean allHistory = archived || q != null;
        DeliveryListQuery target = new DeliveryListQuery(scope, null, states,
                provider == null || provider.isEmpty() ? List.of() : List.of(provider), settle,
                date(params.getFirst("orderedAtStart")), date(params.getFirst("orderedAtEnd")), allHistory,
                normalizeQ(q), null, null, 1);
        return Optional.of(target.href());
    }

    /** The first day of the history read for this query: the typed "from", else 90 days back; null = from the start. */
    public LocalDate historyFrom(LocalDate today) {
        if (from != null) return from;
        if (to != null) return null;
        if (allHistory || focus == DeliveryAttention.INVOICE) return null;
        return today.minusDays(HISTORY_DAYS);
    }

    public boolean isFiltered() {
        return focus != null || !states.isEmpty() || !providers.isEmpty() || !settle.isEmpty()
                || from != null || to != null || q != null;
    }

    public Sort effectiveSort() { return sort != null ? sort : Sort.DUE; }

    public Direction effectiveDir() {
        if (dir != null) return dir;
        return scope == Scope.TRANSIT ? Direction.ASC : Direction.DESC;
    }

    public DeliveryListQuery withScope(Scope s) { return new DeliveryListQuery(s, focus != null && focus.scope() != s ? null : focus, states, providers, settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery withFocus(DeliveryAttention f) { return new DeliveryListQuery(f.scope(), f, List.of(), List.of(), List.of(), null, null, false, null, sort, dir, 1); }
    public DeliveryListQuery withoutFocus() { return new DeliveryListQuery(scope, null, states, providers, settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery toggleState(DeliveryListState s) { return new DeliveryListQuery(scope, focus, toggled(states, s), providers, settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery withoutState(DeliveryListState s) { return new DeliveryListQuery(scope, focus, without(states, s), providers, settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery toggleProvider(String p) { return new DeliveryListQuery(scope, focus, states, toggled(providers, p), settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery withoutProvider(String p) { return new DeliveryListQuery(scope, focus, states, without(providers, p), settle, from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery toggleSettle(Settle s) { return new DeliveryListQuery(scope, focus, states, providers, toggled(settle, s), from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery withoutSettle(Settle s) { return new DeliveryListQuery(scope, focus, states, providers, without(settle, s), from, to, allHistory, q, sort, dir, 1); }
    public DeliveryListQuery withDates(LocalDate f, LocalDate t) { return new DeliveryListQuery(scope, focus, states, providers, settle, f, t, false, q, sort, dir, 1); }
    public DeliveryListQuery withAllHistory() { return new DeliveryListQuery(scope, focus, states, providers, settle, null, null, true, q, sort, dir, 1); }
    public DeliveryListQuery withoutDates() { return new DeliveryListQuery(scope, focus, states, providers, settle, null, null, false, q, sort, dir, 1); }
    public DeliveryListQuery withQ(String newQ) { return new DeliveryListQuery(scope, focus, states, providers, settle, from, to, allHistory, normalizeQ(newQ), sort, dir, 1); }
    public DeliveryListQuery withPage(int n) { return new DeliveryListQuery(scope, focus, states, providers, settle, from, to, allHistory, q, sort, dir, n); }
    public DeliveryListQuery cleared() { return new DeliveryListQuery(scope, null, List.of(), List.of(), List.of(), null, null, false, null, sort, dir, 1); }

    public DeliveryListQuery toggleSort(Sort column) {
        Direction next = effectiveSort() == column ? effectiveDir().flipped() : Direction.ASC;
        return new DeliveryListQuery(scope, focus, states, providers, settle, from, to, allHistory, q, column, next, 1);
    }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (scope != Scope.TRANSIT) parts.add("scope=" + scope.param());
        if (focus != null) parts.add("focus=" + focus.param());
        states.forEach(s -> parts.add("state=" + s.param()));
        providers.forEach(p -> parts.add("provider=" + encode(p)));
        settle.forEach(s -> parts.add("settle=" + s.param()));
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        if (allHistory) parts.add("period=all");
        if (q != null) parts.add("q=" + encode(q));
        if (sort != null) parts.add("sort=" + sort.param());
        if (dir != null) parts.add("dir=" + dir.param());
        if (page > 1) parts.add("page=" + page);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static <T> List<T> toggled(List<T> list, T value) {
        List<T> next = new ArrayList<>(list);
        if (!next.remove(value)) next.add(value);
        return next;
    }

    private static <T> List<T> without(List<T> list, T value) {
        List<T> next = new ArrayList<>(list);
        next.remove(value);
        return next;
    }

    private static List<String> values(MultiValueMap<String, String> params, String name) {
        List<String> raw = params.get(name);
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).toList();
    }

    private static boolean flag(MultiValueMap<String, String> params, String name) {
        return "true".equalsIgnoreCase(trim(params.getFirst(name)));
    }

    private static LocalDate date(String value) {
        try {
            return StringUtils.isBlank(value) ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String normalizeQ(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : StringUtils.left(trimmed, MAX_Q);
    }

    private static int parsePage(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values).map(StringUtils::trimToNull).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
