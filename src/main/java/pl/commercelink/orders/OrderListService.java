package pl.commercelink.orders;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.filters.FilterActor;
import pl.commercelink.orders.filters.OrderFilterField;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.model.OrderFilterCondition;
import pl.commercelink.orders.filters.services.ListOrderFiltersView;
import pl.commercelink.orders.filters.services.OrderFiltersService;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.orders.OrderListQuery.Direction;
import pl.commercelink.web.orders.OrderListQuery.Sort;
import pl.commercelink.web.orders.OrderRow;
import pl.commercelink.web.orders.OrderRowMapper;
import pl.commercelink.web.orders.OrdersPageModel;
import pl.commercelink.web.orders.OrdersPageModel.Chip;
import pl.commercelink.web.orders.OrdersPageModel.EmptyState;
import pl.commercelink.web.orders.OrdersPageModel.FilterOption;
import pl.commercelink.web.orders.OrdersPageModel.StatusOption;
import pl.commercelink.web.orders.OrdersPageModel.SortHeader;
import pl.commercelink.web.orders.OrdersPageModel.Tile;
import pl.commercelink.web.orders.Pagination;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Builds the orders list page from one query of the store's open orders (spec §8.2, §23): tiles from all open orders,
 * status counts within the custom filter and the search, rows within the ticked statuses, then sort and page.
 */
@Service
public class OrderListService {

    public static final List<OrderStatus> OPEN = List.of(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly,
            OrderStatus.Assembled, OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);

    private final OrdersRepository ordersRepository;
    private final OrderFiltersService orderFilters;
    private final MessageSource messages;
    private final StoresRepository storesRepository;

    public OrderListService(OrdersRepository ordersRepository, OrderFiltersService orderFilters, MessageSource messages,
                            StoresRepository storesRepository) {
        this.ordersRepository = ordersRepository;
        this.orderFilters = orderFilters;
        this.messages = messages;
        this.storesRepository = storesRepository;
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
                .sorted(comparator(query.effectiveSort(), query.effectiveDir()))
                .toList();

        Pagination pagination = Pagination.of(query.page(), inStatus.size(), OrderListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        Store store = storesRepository.findById(actor.storeId());
        OrderRowMapper mapper = new OrderRowMapper(messages, locale, store != null && store.hasDocumentsGenerationEnabled());
        List<OrderRow> rows = inStatus.subList(pagination.fromIndex(), pagination.toIndex()).stream()
                .map(order -> mapper.map(order, today)).toList();

        return new OrdersPageModel(
                query,
                tiles(open, today, locale),
                statusOptions(filtered, OPEN, query, locale),
                statusSummary(query, locale),
                filterOptions(filters, query),
                activeFilter,
                chips(query, activeFilter, locale),
                text("orders.list.results", locale, inStatus.size()),
                sortHeaders(query),
                rows,
                pagination,
                rows.isEmpty() ? emptyState(query, activeFilter, locale) : null);
    }

    /**
     * A custom filter's Status condition ticks the Status menu when the filter is chosen ({@link #filterStatus}); from
     * then on the menu decides the status, and the list applies the filter's other conditions (spec §7.5, D3).
     */
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
            case STATUS -> byStatus(Comparator.naturalOrder());
        };
        if (dir == Direction.ASC) {
            return base;
        }
        // the lifecycle order flips, the most urgent order stays on top within each status
        if (sort == Sort.STATUS) {
            return byStatus(Comparator.reverseOrder());
        }
        // "no date" stays last whichever way the dates run
        if (sort == Sort.DUE) {
            return Comparator.comparing((Order o) -> o.getShippingDueAt() == null ? 1 : 0)
                    .thenComparing(Comparator.comparing(Order::getShippingDueAt, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder())))
                    .thenComparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return base.reversed();
    }

    private List<Tile> tiles(List<Order> open, LocalDate today, Locale locale) {
        return Arrays.stream(OrderAttention.values()).map(kind -> {
            String key = "orders.list.attention." + kind.param();
            return new Tile(text(key, locale), open.stream().filter(o -> kind.matches(o, today)).count(), text(key + ".hint", locale));
        }).toList();
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
        OrderListQuery chosen = query.withFilterId(filter.getId());
        String href = filterStatus(filter).map(chosen::withStatus).orElse(chosen).href();
        return new FilterOption(filter.getId(), filter.getLabel(), shared, filter.getId().equals(query.filterId()), href);
    }

    /** The open status a filter's Status condition names; a closed one (saved before the list dropped history) is ignored. */
    public static Optional<OrderStatus> filterStatus(OrderFilter filter) {
        return filter.getConditions().stream()
                .filter(c -> c.getField() == OrderFilterField.Status)
                .map(c -> c.getField().normalize(c.getValue()))
                .flatMap(value -> OPEN.stream().filter(s -> s.name().equalsIgnoreCase(value)))
                .findFirst();
    }

    private List<Chip> chips(OrderListQuery query, Optional<OrderFilter> activeFilter, Locale locale) {
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
        if (query.q() != null) {
            String label = text("orders.list.chip.search", locale, query.q());
            chips.add(new Chip(label, query.withQ(null).href(), text("orders.list.chip.clearLabel", locale, label)));
        }
        return chips;
    }

    /** Statuses in lifecycle order (the OrderStatus declaration: New … Delivered), then due first within one status. */
    private static Comparator<Order> byStatus(Comparator<OrderStatus> statusOrder) {
        return Comparator.comparing(Order::getStatus, Comparator.nullsLast(statusOrder))
                .thenComparing(Order::getShippingDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder()));
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

    private String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
