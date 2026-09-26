package pl.commercelink.orders;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.filters.services.OrderFiltersService;
import pl.commercelink.web.orders.FilterConditionLabels;
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
import pl.commercelink.web.orders.OrdersPageModel.StatusOption;
import pl.commercelink.web.orders.OrdersPageModel.SortHeader;
import pl.commercelink.web.orders.OrdersPageModel.Tile;
import pl.commercelink.web.orders.Pagination;

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
 * status counts within the custom filter and the search, rows within the ticked statuses and focus, then sort and page.
 */
@Service
public class OrderListService {

    static final List<OrderStatus> OPEN = List.of(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly,
            OrderStatus.Assembled, OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);

    private final OrdersRepository ordersRepository;
    private final OrderFiltersService orderFilters;
    private final MessageSource messages;

    public OrderListService(OrdersRepository ordersRepository, OrderFiltersService orderFilters, MessageSource messages) {
        this.ordersRepository = ordersRepository;
        this.orderFilters = orderFilters;
        this.messages = messages;
    }

    public OrdersPageModel page(FilterActor actor, OrderListQuery query, LocalDate today, Locale locale) {
        // Only open orders are read (StoreIdStatusIndex): Completed and Cancelled are not part of this list, so a store's
        // growing history costs nothing here.
        List<Order> open = ordersRepository.findByStoreAndStatuses(actor.storeId(), OPEN);
        ListOrderFiltersView filters = orderFilters.list(actor);
        Optional<OrderFilter> activeFilter = query.hasFilter() ? filters.byId(query.filterId()) : Optional.empty();

        List<Order> filtered = open.stream()
                .filter(order -> activeFilter.map(f -> matchesIgnoringStatus(f, order, today)).orElse(true))
                .filter(order -> OrderSearch.matches(order, query.q()))
                .toList();
        List<Order> inStatus = filtered.stream()
                .filter(order -> query.isOpen() || query.statuses().contains(order.getStatus()))
                .filter(order -> query.focus() == null || query.focus().matches(order, today))
                .sorted(comparator(query.effectiveSort(), query.effectiveDir()))
                .toList();

        Pagination pagination = Pagination.of(query.page(), inStatus.size(), OrderListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        OrderRowMapper mapper = new OrderRowMapper(messages, locale);
        List<OrderRow> rows = inStatus.subList(pagination.fromIndex(), pagination.toIndex()).stream()
                .map(order -> mapper.map(order, today)).toList();

        return new OrdersPageModel(
                query,
                tiles(open, query, today, locale),
                statusOptions(filtered, OPEN, query, locale),
                statusSummary(query, locale),
                filterOptions(filters, query),
                activeFilter,
                chips(query, activeFilter, inStatus.size(), locale),
                text("orders.list.results", locale, inStatus.size()),
                sortHeaders(query),
                rows,
                pagination,
                rows.isEmpty() ? emptyState(query, activeFilter, locale) : null,
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
        long newCount = open.stream().filter(o -> o.getStatus() == OrderStatus.New).count();
        long blockedCount = open.stream().filter(o -> o.getStatus() == OrderStatus.Blocked).count();
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
            tiles.add(new Tile(kind, text(key, locale), count, null, hint, query.withFocus(pressed ? null : kind).href(), pressed, true, tone));
        }
        return tiles;
    }

    private List<StatusOption> statusOptions(List<Order> filtered, List<OrderStatus> statuses, OrderListQuery query, Locale locale) {
        return statuses.stream().map(status -> new StatusOption(status.name(), text("OrderStatus." + status.name(), locale),
                filtered.stream().filter(o -> o.getStatus() == status).count(), query.statuses().contains(status))).toList();
    }

    /** What the Status button says: "Otwarte", the one ticked status, or "3 wybrane". */
    private String statusSummary(OrderListQuery query, Locale locale) {
        if (query.isOpen()) {
            return text("orders.list.segment.open", locale);
        }
        return query.single().map(s -> text("OrderStatus." + s.name(), locale))
                .orElseGet(() -> text("orders.list.status.selected", locale, query.statuses().size()));
    }

    private List<FilterOption> filterOptions(ListOrderFiltersView filters, OrderListQuery query) {
        return Stream.concat(
                        filters.sharedWithStore().stream().map(f -> option(f, true, query)),
                        filters.own().stream().map(f -> option(f, false, query)))
                .toList();
    }

    private static FilterOption option(OrderFilter filter, boolean shared, OrderListQuery query) {
        return new FilterOption(filter.getId(), filter.getLabel(), shared,
                filter.getId().equals(query.filterId()));
    }

    private List<Chip> chips(OrderListQuery query, Optional<OrderFilter> activeFilter, int count, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        // one chip per ticked status, so its "×" drops just that status; dropping the last one returns to all open
        for (OrderStatus status : query.statuses()) {
            String label = text("orders.list.chip.status", locale, text("OrderStatus." + status.name(), locale));
            chips.add(new Chip(label, query.toggleStatus(status).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        activeFilter.ifPresent(f -> {
            String label = text("orders.list.chip.filter", locale, f.getLabel());
            chips.add(new Chip(label, query.withFilterId(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        });
        if (query.focus() != null) {
            String label = text("orders.list.chip.focus", locale, text("orders.list.attention." + query.focus().param(), locale), count);
            chips.add(new Chip(label, query.withFocus(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        if (query.q() != null) {
            String label = text("orders.list.chip.search", locale, query.q());
            chips.add(new Chip(label, query.withQ(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        return chips;
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

    private EmptyState emptyState(OrderListQuery query, Optional<OrderFilter> activeFilter, Locale locale) {
        if (query.q() != null) {
            return new EmptyState(text("orders.list.empty.search", locale, query.q()),
                    text("orders.list.empty.search.clear", locale), query.withQ(null).href());
        }
        if (query.focus() != null) {
            return new EmptyState(text("orders.list.empty." + query.focus().param(), locale),
                    text("orders.list.empty.showOpen", locale), query.withFocus(null).withStatus(null).href());
        }
        if (activeFilter.isPresent()) {
            return new EmptyState(text("orders.list.empty.filter", locale),
                    text("orders.list.empty.filter.clear", locale), query.withFilterId(null).href());
        }
        if (!query.isOpen()) {
            String message = query.single().map(s -> text("orders.list.empty.status", locale, text("OrderStatus." + s.name(), locale)))
                    .orElseGet(() -> text("orders.list.empty.statuses", locale));
            return new EmptyState(message, text("orders.list.empty.showOpen", locale), query.withStatus(null).href());
        }
        // only open orders are read, so "none open" is all the list can say (a new store and a quiet day look the same)
        return new EmptyState(text("orders.list.empty.open", locale), null, null);
    }

    /**
     * What "Save this view" would store: the ticked status plus the active filter's other conditions. A saved filter
     * holds one status, so with several ticked the status is left out rather than silently reduced to the first.
     */
    private List<Condition> saveViewConditions(OrderListQuery query, Optional<OrderFilter> activeFilter, Locale locale) {
        List<Condition> conditions = new ArrayList<>();
        query.single().ifPresent(status -> conditions.add(new Condition(OrderFilterField.Status.name(), status.name(),
                text("orders.filters.field.status", locale) + ": " + text("OrderStatus." + status.name(), locale))));
        activeFilter.ifPresent(f -> f.getConditions().stream()
                .filter(c -> c.getField() != OrderFilterField.Status)
                .forEach(c -> conditions.add(new Condition(c.getField().name(), c.getValue(), conditionLabel(c, locale)))));
        return conditions;
    }

    private String conditionLabel(OrderFilterCondition c, Locale locale) {
        String field = text("orders.filters.field." + FilterConditionLabels.fieldKey(c.getField()), locale);
        String value = FilterConditionLabels.value(c, key -> text(key, locale));
        return field + ": " + value;
    }

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
