package pl.commercelink.web.orders;

import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.OrderAttention;
import pl.commercelink.orders.OrderStatus;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The state of the orders list, read from and written back to the address (spec §2). Every link on the page is
 * built here, so changing one parameter never loses the others. statuses is empty for "all open" and otherwise the
 * statuses ticked in the Status menu (?status=New&status=Blocked, in enum order); filterId == "" (an
 * older "clear the filter" link) means no filter, like null.
 */
public record OrderListQuery(List<OrderStatus> statuses, String filterId, OrderAttention focus, String q,
                             Sort sort, Direction dir, int page) {

    public static final String PATH = "/dashboard/orders";
    public static final int PAGE_SIZE = 50;
    public static final int MAX_Q = 100;
    static final int MAX_RETURN_TO = 300;

    /** Completed and Cancelled orders are not part of the list: such a status in the address is ignored. */
    private static final Set<OrderStatus> HISTORY = EnumSet.of(OrderStatus.Completed, OrderStatus.Cancelled);

    public OrderListQuery {
        statuses = statuses == null || statuses.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(statuses));
    }

    public enum Sort {
        DUE("due"), AMOUNT("amount"), NUMBER("number"), STATUS("status"), ORDERED("ordered");

        private final String param;

        Sort(String param) {
            this.param = param;
        }

        public String param() {
            return param;
        }

        static Optional<Sort> parse(String value) {
            return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value == null ? "" : value.trim())).findFirst();
        }
    }

    public enum Direction {
        ASC, DESC;

        static Optional<Direction> parse(String value) {
            return Arrays.stream(values()).filter(d -> d.name().equalsIgnoreCase(value == null ? "" : value.trim())).findFirst();
        }

        public String param() {
            return name().toLowerCase();
        }

        Direction flipped() {
            return this == ASC ? DESC : ASC;
        }
    }

    public static OrderListQuery parse(MultiValueMap<String, String> params) {
        String rawFilter = params.getFirst("filterId");
        return new OrderListQuery(
                parseStatuses(params.get("status")),
                rawFilter == null ? null : rawFilter.trim(),
                OrderAttention.parse(params.getFirst("focus")).orElse(null),
                normalizeQ(params.getFirst("q")),
                Sort.parse(params.getFirst("sort")).orElse(null),
                Direction.parse(params.getFirst("dir")).orElse(null),
                parsePage(params.getFirst("page")));
    }

    /** ?statuses=A&statuses=B and ?showAll=true from before the redesign: bookmarks keep working (spec §2). */
    public static Optional<String> legacyRedirect(MultiValueMap<String, String> params) {
        List<String> statuses = params.get("statuses");
        boolean showAll = params.containsKey("showAll");
        if ((statuses == null || statuses.isEmpty()) && !showAll) {
            return Optional.empty();
        }
        String filterId = params.getFirst("filterId");
        OrderListQuery target = new OrderListQuery(parseStatuses(statuses), filterId == null || filterId.isBlank() ? null : filterId.trim(),
                null, null, null, null, 1);
        return Optional.of(target.href());
    }

    /** Repeated ?status= values, each possibly comma-separated; "open", blanks and unknown names are ignored. */
    private static List<OrderStatus> parseStatuses(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null)
                .flatMap(v -> Arrays.stream(v.split(",")))
                .map(OrderListQuery::parseStatus)
                .filter(s -> s != null && !HISTORY.contains(s))
                .toList();
    }

    private static OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank() || "open".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return Arrays.stream(OrderStatus.values()).filter(s -> s.name().equalsIgnoreCase(value.trim())).findFirst().orElse(null);
    }

    private static String normalizeQ(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_Q ? trimmed.substring(0, MAX_Q) : trimmed;
    }

    private static int parsePage(String value) {
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** No status ticked: every open order. */
    public boolean isOpen() {
        return statuses.isEmpty();
    }

    /** The one ticked status, if exactly one is — what a saved filter can hold. */
    public Optional<OrderStatus> single() {
        return statuses.size() == 1 ? Optional.of(statuses.get(0)) : Optional.empty();
    }

    public boolean hasExplicitNoFilter() {
        return filterId != null && filterId.isEmpty();
    }

    public boolean hasFilter() {
        return filterId != null && !filterId.isEmpty();
    }

    public Sort effectiveSort() {
        return sort != null ? sort : Sort.DUE;
    }

    public Direction effectiveDir() {
        if (dir != null) {
            return dir;
        }
        return sort == Sort.ORDERED ? Direction.DESC : Direction.ASC;
    }

    /** Exactly this one status, or all open when null. */
    public OrderListQuery withStatus(OrderStatus newStatus) {
        return withStatuses(newStatus == null ? List.of() : List.of(newStatus));
    }

    public OrderListQuery withStatuses(Collection<OrderStatus> newStatuses) {
        return new OrderListQuery(List.copyOf(newStatuses), filterId, focus, q, sort, dir, 1);
    }

    public OrderListQuery toggleStatus(OrderStatus status) {
        List<OrderStatus> next = new ArrayList<>(statuses);
        if (!next.remove(status)) {
            next.add(status);
        }
        return withStatuses(next);
    }

    public OrderListQuery withFilterId(String newFilterId) {
        return new OrderListQuery(statuses, newFilterId, focus, q, sort, dir, 1);
    }

    public OrderListQuery withFocus(OrderAttention newFocus) {
        return new OrderListQuery(statuses, filterId, newFocus, q, sort, dir, 1);
    }

    public OrderListQuery withQ(String newQ) {
        return new OrderListQuery(statuses, filterId, focus, normalizeQ(newQ), sort, dir, 1);
    }

    public OrderListQuery toggleSort(Sort column) {
        Direction next = effectiveSort() == column ? effectiveDir().flipped()
                : (column == Sort.ORDERED ? Direction.DESC : Direction.ASC);
        return new OrderListQuery(statuses, filterId, focus, q, column, next, 1);
    }

    public OrderListQuery withPage(int newPage) {
        return new OrderListQuery(statuses, filterId, focus, q, sort, dir, Math.max(1, newPage));
    }

    public String href() {
        List<String> parts = new ArrayList<>();
        for (OrderStatus status : statuses) {
            parts.add("status=" + status.name());
        }
        if (filterId != null) {
            parts.add("filterId=" + encode(filterId));
        }
        if (focus != null) {
            parts.add("focus=" + focus.param());
        }
        if (q != null) {
            parts.add("q=" + encode(q));
        }
        if (sort != null) {
            parts.add("sort=" + sort.param());
        }
        if (dir != null) {
            parts.add("dir=" + dir.param());
        }
        if (page > 1) {
            parts.add("page=" + page);
        }
        return parts.isEmpty() ? PATH : PATH + "?" + String.join("&", parts);
    }

    /** Where a dialog form returns to; a runaway address falls back to the bare list (spec §8.1). */
    public String returnTo() {
        String href = href();
        return href.length() > MAX_RETURN_TO ? PATH : href;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
