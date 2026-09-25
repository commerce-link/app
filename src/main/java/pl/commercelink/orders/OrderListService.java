package pl.commercelink.orders;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.filters.services.OrderFiltersService;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.orders.OrderListQuery.Direction;
import pl.commercelink.web.orders.OrderListQuery.Sort;
import pl.commercelink.web.orders.OrderRow;
import pl.commercelink.web.orders.OrderRowMapper;
import pl.commercelink.web.orders.OrdersPageModel;
import pl.commercelink.web.orders.OrdersPageModel.Chip;
import pl.commercelink.web.orders.OrdersPageModel.Condition;
import pl.commercelink.web.orders.OrdersPageModel.EmptyState;
import pl.commercelink.web.orders.OrdersPageModel.FilterOption;
import pl.commercelink.web.orders.OrdersPageModel.Segment;
import pl.commercelink.web.orders.OrdersPageModel.SortHeader;
import pl.commercelink.web.orders.OrdersPageModel.Tile;
import pl.commercelink.web.orders.Pagination;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Builds the orders list page from one query of the store's orders (spec §8.2): tiles from all open orders,
 * segment counts within the custom filter and the search, rows within status and focus, then sort and page.
 */
@Service
public class OrderListService {

    static final List<OrderStatus> OPEN = List.of(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly,
            OrderStatus.Assembled, OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);
    static final List<OrderStatus> HISTORY = List.of(OrderStatus.Completed, OrderStatus.Cancelled);

    private final OrdersRepository ordersRepository;
    private final OrderFiltersService orderFilters;
    private final MessageSource messages;

    public OrderListService(OrdersRepository ordersRepository, OrderFiltersService orderFilters, MessageSource messages) {
        this.ordersRepository = ordersRepository;
        this.orderFilters = orderFilters;
        this.messages = messages;
    }

    public OrdersPageModel page(FilterActor actor, OrderListQuery query, LocalDate today, Locale locale) {
        List<Order> all = ordersRepository.findByStore(actor.storeId());
        ListOrderFiltersView filters = orderFilters.list(actor);
        Optional<OrderFilter> activeFilter = query.hasFilter() ? filters.byId(query.filterId()) : Optional.empty();
        boolean starred = activeFilter.map(f -> filters.isDefault(f.getId())).orElse(false);

        List<Order> open = all.stream().filter(OrderAttention::isOpen).toList();
        List<Order> filtered = all.stream()
                .filter(order -> activeFilter.map(f -> matchesIgnoringStatus(f, order, today)).orElse(true))
                .filter(order -> OrderSearch.matches(order, query.q()))
                .toList();
        List<Order> inStatus = filtered.stream()
                .filter(order -> query.isOpen() ? OrderAttention.isOpen(order) : order.getStatus() == query.status())
                .filter(order -> query.focus() == null || query.isHistory() || query.focus().matches(order, today))
                .sorted(comparator(query.effectiveSort(), query.effectiveDir()))
                .toList();

        Pagination pagination = Pagination.of(query.page(), inStatus.size(), OrderListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        OrderRowMapper mapper = new OrderRowMapper(messages, locale);
        List<OrderRow> rows = inStatus.subList(pagination.fromIndex(), pagination.toIndex()).stream()
                .map(order -> mapper.map(order, today)).toList();

        long historyHits = query.q() == null ? 0 : filtered.stream().filter(o -> !OrderAttention.isOpen(o)).count();
        return new OrdersPageModel(
                query,
                tiles(open, query, today, locale),
                openSegments(filtered, query, locale),
                historySegments(filtered, query, locale),
                filterOptions(filters, query),
                activeFilter,
                starred,
                chips(query, activeFilter, starred, inStatus.size(), locale),
                resultsLine(query, inStatus.size(), historyHits, locale),
                sortHeaders(query),
                rows,
                pagination,
                rows.isEmpty() ? emptyState(query, activeFilter, all.isEmpty(), locale) : null,
                saveViewConditions(query, activeFilter, locale));
    }

    /** A custom filter's Status condition only chooses the segment on entry (spec §7.5); the list applies the rest. */
    static boolean matchesIgnoringStatus(OrderFilter filter, Order order, LocalDate today) {
        List<OrderFilterCondition> others = filter.getConditions().stream()
                .filter(c -> c.getField() != OrderFilterField.Status).toList();
        return others.stream().allMatch(c -> c.matches(order, today));
    }

    private static Comparator<Order> comparator(Sort sort, Direction dir) {
        Comparator<Order> base = switch (sort) {
            case DUE -> Comparator.comparing(Order::getShippingDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case AMOUNT -> Comparator.comparingDouble(Order::getTotalPrice);
            case NUMBER -> Comparator.comparing(Order::getOrderId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case ORDERED -> Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()));
        };
        if (dir == Direction.ASC) {
            return base;
        }
        // "no date" stays last whichever way the dates run
        if (sort == Sort.DUE) {
            return Comparator.comparing((Order o) -> o.getShippingDueAt() == null ? 1 : 0)
                    .thenComparing(Comparator.comparing(Order::getShippingDueAt, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                    .thenComparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return base.reversed();
    }

    private List<Tile> tiles(List<Order> open, OrderListQuery query, LocalDate today, Locale locale) {
        boolean enabled = !query.isHistory();
        long newCount = open.stream().filter(o -> o.getStatus() == OrderStatus.New).count();
        long blockedCount = open.stream().filter(o -> o.getStatus() == OrderStatus.Blocked).count();
        double unpaidSum = open.stream().filter(o -> OrderAttention.Unpaid.matches(o, today)).mapToDouble(Order::getUnpaidAmount).sum();
        List<Tile> tiles = new ArrayList<>();
        for (OrderAttention kind : OrderAttention.values()) {
            long count = open.stream().filter(o -> kind.matches(o, today)).count();
            boolean pressed = query.focus() == kind;
            String key = "orders.list.attention." + kind.param();
            String hint = switch (kind) {
                case Decide -> text("orders.list.attention.decide.hint", locale, newCount, blockedCount);
                default -> text(key + ".hint", locale);
            };
            String tone = switch (kind) {
                case Overdue -> count > 0 ? "is-bad" : "";
                case Today -> count > 0 ? "is-warn" : "";
                default -> "";
            };
            tiles.add(new Tile(kind, text(key, locale), count,
                    kind == OrderAttention.Unpaid ? text("general.currency.amount", locale, money(unpaidSum, locale)) : null,
                    hint, enabled ? query.withFocus(pressed ? null : kind).href() : null, pressed, enabled, tone));
        }
        return tiles;
    }

    private List<Segment> openSegments(List<Order> filtered, OrderListQuery query, Locale locale) {
        List<Segment> segments = new ArrayList<>();
        long openCount = filtered.stream().filter(OrderAttention::isOpen).count();
        segments.add(new Segment("open", text("orders.list.segment.open", locale), openCount, query.withStatus(null).href(), query.isOpen()));
        for (OrderStatus status : OPEN) {
            segments.add(segment(filtered, status, query, locale));
        }
        return segments;
    }

    private List<Segment> historySegments(List<Order> filtered, OrderListQuery query, Locale locale) {
        return HISTORY.stream().map(status -> segment(filtered, status, query, locale)).toList();
    }

    private Segment segment(List<Order> filtered, OrderStatus status, OrderListQuery query, Locale locale) {
        long count = filtered.stream().filter(o -> o.getStatus() == status).count();
        return new Segment(status.name(), text("OrderStatus." + status.name(), locale), count,
                query.withStatus(status).href(), query.status() == status);
    }

    private List<FilterOption> filterOptions(ListOrderFiltersView filters, OrderListQuery query) {
        return Stream.concat(
                        filters.sharedWithStore().stream().map(f -> option(f, true, filters, query)),
                        filters.own().stream().map(f -> option(f, false, filters, query)))
                .toList();
    }

    private static FilterOption option(OrderFilter filter, boolean shared, ListOrderFiltersView filters, OrderListQuery query) {
        boolean starred = filters.isDefault(filter.getId());
        return new FilterOption(filter.getId(), (starred ? "★ " : "") + filter.getLabel(), shared, starred,
                filter.getId().equals(query.filterId()));
    }

    private List<Chip> chips(OrderListQuery query, Optional<OrderFilter> activeFilter, boolean starred, int count, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        activeFilter.ifPresent(f -> {
            String label = text("orders.list.chip.filter", locale, f.getLabel() + (starred ? " ★" : ""));
            chips.add(new Chip(label, query.withFilterId("").href(), text("orders.list.chip.clearLabel", locale, label)));
        });
        if (query.focus() != null && !query.isHistory()) {
            String label = text("orders.list.chip.focus", locale, text("orders.list.attention." + query.focus().param(), locale), count);
            chips.add(new Chip(label, query.withFocus(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        if (query.q() != null) {
            String label = text("orders.list.chip.search", locale, query.q());
            chips.add(new Chip(label, query.withQ(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        return chips;
    }

    private String resultsLine(OrderListQuery query, int count, long historyHits, Locale locale) {
        String line = query.q() == null ? text("orders.list.results", locale, count)
                : text("orders.list.results.search", locale, query.q(), count);
        if (query.q() != null && !query.isHistory()) {
            line += " · " + text("orders.list.results.history", locale, historyHits);
        }
        if (query.isHistory() && query.sort() == null) {
            line += " · " + text("orders.list.results.newestFirst", locale);
        }
        return line;
    }

    private static Map<Sort, SortHeader> sortHeaders(OrderListQuery query) {
        Map<Sort, SortHeader> headers = new EnumMap<>(Sort.class);
        for (Sort sort : Sort.values()) {
            String ariaSort = query.effectiveSort() == sort
                    ? (query.effectiveDir() == Direction.ASC ? "ascending" : "descending") : "none";
            headers.put(sort, new SortHeader(query.toggleSort(sort).href(), ariaSort));
        }
        return headers;
    }

    private EmptyState emptyState(OrderListQuery query, Optional<OrderFilter> activeFilter, boolean storeEmpty, Locale locale) {
        if (query.q() != null) {
            return new EmptyState(text("orders.list.empty.search", locale, query.q()),
                    text("orders.list.empty.search.clear", locale), query.withQ(null).href());
        }
        if (query.focus() != null && !query.isHistory()) {
            return new EmptyState(text("orders.list.empty." + query.focus().param(), locale),
                    text("orders.list.empty.showOpen", locale), query.withFocus(null).withStatus(null).href());
        }
        if (activeFilter.isPresent()) {
            return new EmptyState(text("orders.list.empty.filter", locale),
                    text("orders.list.empty.filter.clear", locale), query.withFilterId("").href());
        }
        if (!query.isOpen()) {
            return new EmptyState(text("orders.list.empty.status", locale, text("OrderStatus." + query.status().name(), locale)),
                    text("orders.list.empty.showOpen", locale), query.withStatus(null).href());
        }
        return new EmptyState(text("orders.list.empty.store", locale), null, null);
    }

    /** What "Save this view" would store: the segment's status (if any) plus the active filter's other conditions. */
    private List<Condition> saveViewConditions(OrderListQuery query, Optional<OrderFilter> activeFilter, Locale locale) {
        List<Condition> conditions = new ArrayList<>();
        if (!query.isOpen()) {
            conditions.add(new Condition(OrderFilterField.Status.name(), query.status().name(),
                    text("orders.filters.field.status", locale) + ": " + text("OrderStatus." + query.status().name(), locale)));
        }
        activeFilter.ifPresent(f -> f.getConditions().stream()
                .filter(c -> c.getField() != OrderFilterField.Status)
                .forEach(c -> conditions.add(new Condition(c.getField().name(), c.getValue(), conditionLabel(c, locale)))));
        return conditions;
    }

    private String conditionLabel(OrderFilterCondition c, Locale locale) {
        String field = switch (c.getField()) {
            case ShipmentType -> text("orders.filters.field.shipment.type", locale);
            case PaymentSource -> text("orders.filters.field.payment.source", locale);
            case ShippingDue -> text("orders.filters.field.shipping.due", locale);
            case SourceName -> text("orders.filters.field.marketplace", locale);
            case ShippingPostalCode -> text("orders.filters.field.postal.code", locale);
            case Status -> text("orders.filters.field.status", locale);
        };
        String value = switch (c.getField()) {
            case ShipmentType -> enumLabel(ShipmentType.class, c.getValue(), "ShipmentType.", locale);
            case PaymentSource -> enumLabel(PaymentSource.class, c.getValue(), "PaymentSource.", locale);
            case ShippingDue -> enumLabel(pl.commercelink.orders.filters.ShippingDue.class, c.getValue(), "ShippingDue.", locale);
            case Status -> enumLabel(OrderStatus.class, c.getValue(), "OrderStatus.", locale);
            default -> c.getValue();
        };
        return field + ": " + value;
    }

    /** Stored condition values are trimmed raw strings that may differ in case from the enum name (OrderFilterField
     * normalizes to upper case for matching); resolve the enum case-insensitively before building the label key,
     * falling back to the raw value when nothing matches. */
    private <E extends Enum<E>> String enumLabel(Class<E> type, String rawValue, String keyPrefix, Locale locale) {
        return java.util.Arrays.stream(type.getEnumConstants())
                .filter(v -> v.name().equalsIgnoreCase(rawValue))
                .findFirst()
                .map(v -> text(keyPrefix + v.name(), locale))
                .orElse(rawValue);
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static String money(double value, Locale locale) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }
}
