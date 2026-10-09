package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.ArchiveCursor;
import pl.commercelink.orders.OrderStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The state of the orders archive, read from and written back to the address (spec §4). Unknown values are ignored so
 * a hand-edited address still opens; any change of a filter starts again from the newest order.
 */
public record OrderArchiveQuery(OrderStatus status, String q, Period period, LocalDate from, LocalDate to,
                                ArchiveCursor after, ArchiveCursor before, int offset) {

    public static final String PATH = "/dashboard/orders/archive";
    public static final String FRAGMENT_PATH = PATH + "/list";
    public static final int PAGE_SIZE = 25;
    public static final int MAX_Q = 100;
    private static final Set<OrderStatus> CLOSED = Set.of(OrderStatus.Completed, OrderStatus.Cancelled);
    // a shortened number on the list and cards is the first characters of the UUID; four is the shortest worth a key query
    private static final Pattern NUMBER = Pattern.compile("[0-9a-fA-F-]{4,36}");

    public enum SearchMode { NUMBER, EMAIL, TEXT }

    public enum Period {
        LAST_30("30d"), LAST_90("90d"), THIS_YEAR("year");

        private final String param;

        Period(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }

        public static Optional<Period> parse(String value) {
            return Arrays.stream(values()).filter(p -> p.param.equals(StringUtils.trimToEmpty(value))).findFirst();
        }
    }

    public OrderArchiveQuery {
        if (status != null && !CLOSED.contains(status)) {
            status = null;
        }
        q = StringUtils.trimToNull(q) == null ? null : StringUtils.left(q.trim(), MAX_Q);
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate earlier = to;
            to = from;
            from = earlier;
        }
        if (from != null || to != null) {
            period = null;
        }
        if (after != null && before != null) {
            before = null;
        }
        offset = after == null && before == null ? 0 : Math.max(0, offset);
    }

    public static OrderArchiveQuery parse(MultiValueMap<String, String> params) {
        return new OrderArchiveQuery(
                status(params.getFirst("status")),
                params.getFirst("q"),
                Period.parse(params.getFirst("period")).orElse(null),
                date(params.getFirst("from")),
                date(params.getFirst("to")),
                ArchiveCursor.decode(params.getFirst("after")).orElse(null),
                ArchiveCursor.decode(params.getFirst("before")).orElse(null),
                number(params.getFirst("offset")));
    }

    /** The search form of the old "Klienci" page: order id or e-mail become the search, the order days the date range. */
    public static String legacyHref(String orderId, String email, LocalDate orderedAtStart, LocalDate orderedAtEnd) {
        String text = StringUtils.isNotBlank(orderId) ? orderId : email;
        return new OrderArchiveQuery(null, text, null, orderedAtStart, orderedAtEnd, null, null, 0).href();
    }

    public SearchMode searchMode() {
        if (q == null) return null;
        if (q.contains("@")) return SearchMode.EMAIL;
        return NUMBER.matcher(q).matches() && q.chars().anyMatch(Character::isDigit) ? SearchMode.NUMBER : SearchMode.TEXT;
    }

    public String numberPrefix() {
        return searchMode() == SearchMode.NUMBER ? q.toLowerCase(Locale.ROOT) : null;
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

    public Set<OrderStatus> statuses() {
        return status == null ? CLOSED : Set.of(status);
    }

    public boolean hasDates() {
        return period != null || from != null || to != null;
    }

    public boolean isFiltered() {
        return q != null || hasDates();
    }

    public int activeFilterCount() {
        return hasDates() ? 1 : 0;
    }

    public OrderArchiveQuery withStatus(OrderStatus s) { return new OrderArchiveQuery(s, q, period, from, to, null, null, 0); }
    public OrderArchiveQuery withQ(String text) { return new OrderArchiveQuery(status, text, period, from, to, null, null, 0); }
    public OrderArchiveQuery withPeriod(Period p) { return new OrderArchiveQuery(status, q, p, null, null, null, null, 0); }
    public OrderArchiveQuery withoutDates() { return new OrderArchiveQuery(status, q, null, null, null, null, null, 0); }
    public OrderArchiveQuery cleared() { return new OrderArchiveQuery(status, null, null, null, null, null, null, 0); }
    public OrderArchiveQuery first() { return new OrderArchiveQuery(status, q, period, from, to, null, null, 0); }
    public OrderArchiveQuery after(ArchiveCursor c, int newOffset) { return new OrderArchiveQuery(status, q, period, from, to, c, null, newOffset); }
    public OrderArchiveQuery before(ArchiveCursor c, int newOffset) { return new OrderArchiveQuery(status, q, period, from, to, null, c, newOffset); }

    public String href() {
        List<String> parts = new ArrayList<>();
        if (status != null) parts.add("status=" + status.name());
        if (q != null) parts.add("q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        if (period != null) parts.add("period=" + period.param());
        if (from != null) parts.add("from=" + from);
        if (to != null) parts.add("to=" + to);
        if (after != null) parts.add("after=" + after.encode());
        if (before != null) parts.add("before=" + before.encode());
        if (offset > 0) parts.add("offset=" + offset);
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    private static OrderStatus status(String value) {
        return Arrays.stream(OrderStatus.values()).filter(s -> s.name().equals(StringUtils.trimToEmpty(value)))
                .filter(CLOSED::contains).findFirst().orElse(null);
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
