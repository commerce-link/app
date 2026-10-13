package pl.commercelink.web.rma;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.orders.rma.RmaCursor;
import pl.commercelink.web.orders.OrderArchiveQuery.Period;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The state of the RMA list, read from and written back to the address (spec §4). Unknown values are ignored so a
 * hand-edited address still opens; any change of a filter starts again from the newest RMA. Without a search the list
 * shows one scope (active by default); a search covers both until a scope is chosen.
 */
public record RmaListQuery(Scope scope, Set<RMAStatus> statuses, String q, Period period, LocalDate from, LocalDate to,
                           RmaCursor after, RmaCursor before, int offset) {

    public static final String PATH = "/dashboard/rma";
    public static final String FRAGMENT_PATH = PATH + "/list";
    public static final int PAGE_SIZE = 25;
    public static final int MAX_Q = 100;
    public static final List<RMAStatus> ACTIVE_STATUSES = Arrays.stream(RMAStatus.values()).filter(s -> !s.isClosed()).toList();
    public static final List<RMAStatus> CLOSED_STATUSES = Arrays.stream(RMAStatus.values()).filter(RMAStatus::isClosed).toList();

    private static final Pattern ID_LIKE = Pattern.compile("[0-9a-fA-F-]{4,36}");

    public enum Scope {
        ACTIVE("active"), CLOSED("closed");

        private final String param;

        Scope(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }

        static Scope parse(String value) {
            return Arrays.stream(values()).filter(s -> s.param.equals(StringUtils.trimToEmpty(value))).findFirst().orElse(null);
        }
    }

    public RmaListQuery {
        q = StringUtils.trimToNull(q) == null ? null : StringUtils.left(q.trim(), MAX_Q).trim();
        Scope effective = q == null && scope == null ? Scope.ACTIVE : scope;
        List<RMAStatus> allowed = allowed(effective);
        Set<RMAStatus> kept = new TreeSet<>();
        if (statuses != null) {
            statuses.stream().filter(Objects::nonNull).filter(allowed::contains).forEach(kept::add);
        }
        statuses = kept.containsAll(allowed) ? Set.of() : Collections.unmodifiableSet(kept);
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        if (from != null || to != null) {
            period = null;
        }
        // a search and the active list page forward from a position or an offset; only the closed list walks back
        if (q != null || effective == Scope.ACTIVE || after != null) {
            before = null;
        }
        if (effective == Scope.ACTIVE && q == null) {
            after = null;
            offset = Math.max(0, offset);
        } else {
            offset = after == null && before == null ? 0 : Math.max(0, offset);
        }
    }

    public static RmaListQuery parse(MultiValueMap<String, String> params) {
        List<String> statusValues = params.getOrDefault("status", List.of());
        return new RmaListQuery(
                Scope.parse(params.getFirst("scope")),
                statuses(statusValues.stream()),
                params.getFirst("q"),
                Period.parse(params.getFirst("period")).orElse(null),
                date(params.getFirst("from")),
                date(params.getFirst("to")),
                RmaCursor.decode(params.getFirst("after")).orElse(null),
                RmaCursor.decode(params.getFirst("before")).orElse(null),
                number(params.getFirst("offset")));
    }

    /**
     * The search form and status pills of the list before the redesign: the RMA id, order id or e-mail become the
     * search, the creation days the date range, the comma-joined statuses the scope and status filter.
     */
    public static Optional<String> legacyHref(MultiValueMap<String, String> params) {
        List<String> legacy = List.of("rmaId", "orderId", "email", "createdAtStart", "createdAtEnd", "statuses");
        if (legacy.stream().noneMatch(params::containsKey)) {
            return Optional.empty();
        }
        String text = Stream.of("rmaId", "orderId", "email").map(params::getFirst).filter(StringUtils::isNotBlank).findFirst().orElse(null);
        Set<RMAStatus> statuses = statuses(Stream.ofNullable(params.getFirst("statuses")).flatMap(v -> Arrays.stream(v.split(","))));
        Scope scope = statuses.isEmpty() ? null : statuses.stream().allMatch(RMAStatus::isClosed) ? Scope.CLOSED : Scope.ACTIVE;
        return Optional.of(new RmaListQuery(scope, statuses, text, null, date(params.getFirst("createdAtStart")),
                date(params.getFirst("createdAtEnd")), null, null, 0).href());
    }

    /** The scope shown: null while a search covers both. */
    public Scope effectiveScope() {
        return q == null && scope == null ? Scope.ACTIVE : scope;
    }

    /** The lower-cased search when it looks like (the start of) an RMA id, as in OrderArchiveQuery.searchMode; else null. */
    public String idPrefix() {
        return q != null && ID_LIKE.matcher(q).matches() && q.chars().anyMatch(Character::isDigit) ? q.toLowerCase(Locale.ROOT) : null;
    }

    public boolean searchesActive() {
        return effectiveScope() != Scope.CLOSED;
    }

    public boolean searchesClosed() {
        return effectiveScope() != Scope.ACTIVE;
    }

    public List<RMAStatus> allowedStatuses() {
        return allowed(effectiveScope());
    }

    public boolean accepts(RMAStatus status) {
        // an RMA stored without a status is a new one
        RMAStatus normalized = status == null ? RMAStatus.New : status;
        return statuses.isEmpty() ? allowedStatuses().contains(normalized) : statuses.contains(normalized);
    }

    public LocalDate effectiveFrom(LocalDate today) {
        if (period == null) return from;
        return switch (period) {
            case LAST_30 -> today.minusDays(30);
            case LAST_90 -> today.minusDays(90);
            case THIS_YEAR -> today.withDayOfYear(1);
        };
    }

    public LocalDate effectiveTo() {
        return to;
    }

    public boolean hasDates() {
        return period != null || from != null || to != null;
    }

    public boolean isFiltered() {
        return q != null || hasDates() || !statuses.isEmpty();
    }

    public int activeFilterCount() {
        return (statuses.isEmpty() ? 0 : 1) + (hasDates() ? 1 : 0);
    }

    public List<String> statusParams() {
        return statuses.stream().map(Enum::name).toList();
    }

    public RmaListQuery withScope(Scope s) { return new RmaListQuery(s, statuses, q, period, from, to, null, null, 0); }
    public RmaListQuery withStatuses(Set<RMAStatus> set) { return new RmaListQuery(scope, set, q, period, from, to, null, null, 0); }
    public RmaListQuery forTile(Set<RMAStatus> set) { return new RmaListQuery(Scope.ACTIVE, set, q, period, from, to, null, null, 0); }
    public RmaListQuery withQ(String text) { return new RmaListQuery(scope, statuses, text, period, from, to, null, null, 0); }
    public RmaListQuery withPeriod(Period p) { return new RmaListQuery(scope, statuses, q, p, null, null, null, null, 0); }
    public RmaListQuery withoutDates() { return new RmaListQuery(scope, statuses, q, null, null, null, null, null, 0); }
    public RmaListQuery cleared() { return new RmaListQuery(scope, Set.of(), null, null, null, null, null, null, 0); }
    public RmaListQuery first() { return new RmaListQuery(scope, statuses, q, period, from, to, null, null, 0); }
    public RmaListQuery atOffset(int newOffset) { return new RmaListQuery(scope, statuses, q, period, from, to, null, null, newOffset); }
    public RmaListQuery after(RmaCursor c, int newOffset) { return new RmaListQuery(scope, statuses, q, period, from, to, c, null, newOffset); }
    public RmaListQuery before(RmaCursor c, int newOffset) { return new RmaListQuery(scope, statuses, q, period, from, to, null, c, newOffset); }

    public RmaListQuery withoutStatus(RMAStatus status) {
        Set<RMAStatus> rest = new TreeSet<>(statuses);
        rest.remove(status);
        return withStatuses(rest);
    }

    public String href() {
        List<String> parts = new ArrayList<>();
        // the bare address is the active list, so its scope is written only next to a search
        if (scope != null && !(scope == Scope.ACTIVE && q == null)) parts.add("scope=" + scope.param());
        statuses.forEach(s -> parts.add("status=" + s.name()));
        if (q != null) parts.add("q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        if (period != null) parts.add("period=" + period.param());
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        if (after != null) parts.add("after=" + after.encode());
        if (before != null) parts.add("before=" + before.encode());
        if (offset > 0) parts.add("offset=" + offset);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static List<RMAStatus> allowed(Scope scope) {
        if (scope == null) return List.of(RMAStatus.values());
        return scope == Scope.ACTIVE ? ACTIVE_STATUSES : CLOSED_STATUSES;
    }

    private static Set<RMAStatus> statuses(Stream<String> names) {
        Set<RMAStatus> out = new TreeSet<>();
        names.map(String::trim).forEach(name -> Arrays.stream(RMAStatus.values())
                .filter(s -> s.name().equals(name)).findFirst().ifPresent(out::add));
        return out;
    }

    private static LocalDate date(String value) {
        try {
            return StringUtils.isBlank(value) ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int number(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
