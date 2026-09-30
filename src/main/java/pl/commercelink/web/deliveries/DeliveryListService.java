package pl.commercelink.web.deliveries;

import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.deliveries.DeliveriesPageModel.*;
import pl.commercelink.web.deliveries.DeliveryListQuery.Scope;
import pl.commercelink.web.deliveries.DeliveryListQuery.Settle;
import pl.commercelink.web.deliveries.DeliveryListQuery.Sort;
import pl.commercelink.web.orders.Pagination;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The deliveries list (spec §5–§6): reads only the part of the store the scope needs (on their way, the settlement
 * backlog, a window of the history), then narrows, counts, sorts and pages it in memory. The super admin gets the
 * GLOBAL deliveries of every store, one store's key range at a time instead of a table scan.
 */
@Service
@RequiredArgsConstructor
public class DeliveryListService {

    private static final Pattern NUMBER = Pattern.compile("^[0-9a-fA-F-]{4,36}$");

    public record ListActor(String storeId, boolean superAdmin, boolean admin) {
    }

    private final DeliveriesRepository deliveries;
    private final SupplierLabels supplierLabels;
    private final StoresRepository stores;
    private final MessageSource messages;

    public DeliveriesPageModel page(ListActor actor, DeliveryListQuery query, LocalDate today, Locale locale) {
        List<String> storeIds = actor.superAdmin()
                ? stores.findAll().stream().map(Store::getStoreId).toList()
                : List.of(actor.storeId());
        List<Delivery> transit = read(storeIds, deliveries::findInTransit, actor);
        // the backlog is read only when its list is opened; the tile counts it with a cheap COUNT
        boolean invoiceFocus = query.focus() == DeliveryAttention.INVOICE && !actor.superAdmin();
        List<Delivery> toSettle = invoiceFocus ? read(storeIds, deliveries::findToSettle, actor) : List.of();
        List<Delivery> history = List.of();
        if (query.scope().includesHistory()) {
            history = query.focus() == DeliveryAttention.INVOICE ? toSettle
                    : read(storeIds, id -> deliveries.findReceivedBetween(id, query.historyFrom(today), query.to()), actor);
        }
        if (query.q() != null && NUMBER.matcher(query.q()).matches() && query.scope().includesHistory()) {
            String prefix = query.q().toLowerCase(Locale.ROOT);
            history = distinct(history, read(storeIds, id -> deliveries.findByDeliveryIdPrefix(id, prefix), actor).stream()
                    .filter(d -> d.getReceivedAt() != null).toList());
        }
        SupplierLabelMap labels = actor.superAdmin()
                ? supplierLabels.forStoreIds(Stream.concat(transit.stream(), history.stream()).map(Delivery::getStoreId).distinct().toList())
                : supplierLabels.forStoreId(actor.storeId());

        List<Tile> tiles = tiles(actor, query, transit, toSettle, today, locale);
        List<Delivery> inScope = switch (query.scope()) {
            case TRANSIT -> transit.stream().filter(d -> orderedWithin(d, query)).toList();
            case RECEIVED -> history;
            case ALL -> Stream.concat(transit.stream(), history.stream()).toList();
        };
        List<Delivery> base = inScope.stream()
                .filter(d -> query.focus() == null || query.focus().matches(d, DeliveryListState.of(d), today))
                .filter(d -> matchesSearch(d, query.q()))
                .toList();
        // every menu counts what the list would show after picking its option, so it leaves out only its own filter
        List<Delivery> forStates = base.stream().filter(d -> matchesSettle(d, query.settle()) && matchesProviders(d, query.providers())).toList();
        List<Delivery> forProviders = base.stream().filter(d -> matchesSettle(d, query.settle()) && matchesStates(d, query.states())).toList();
        List<Delivery> forSettle = base.stream().filter(d -> matchesStates(d, query.states()) && matchesProviders(d, query.providers())).toList();
        List<Delivery> shown = forSettle.stream().filter(d -> matchesSettle(d, query.settle()))
                .sorted(comparator(query, labels)).toList();

        Pagination pagination = Pagination.of(query.page(), shown.size(), DeliveryListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        DeliveryRowMapper mapper = new DeliveryRowMapper(messages, locale, labels, actor.superAdmin());
        List<DeliveryRow> rows = shown.subList(pagination.fromIndex(), pagination.toIndex()).stream()
                .map(d -> mapper.map(d, today)).toList();
        List<Option> providerOptions = providerOptions(query, forProviders, labels, actor, locale);
        Map<String, String> providerNames = providerOptions.stream().collect(Collectors.toMap(Option::value, Option::label));
        List<Chip> chips = chips(query, providerNames, today, locale);
        return new DeliveriesPageModel(query, actor.superAdmin(), actor.admin() || actor.superAdmin(), tiles,
                scopes(query, transit.size(), locale),
                stateOptions(query, forStates, false, locale), stateOptions(query, forStates, true, locale),
                summary(query.states().size(), locale),
                providerOptions, summary(query.providers().size(), locale),
                settleOptions(query, forSettle, locale), summary(query.settle().size(), locale),
                dateMenu(query, today, locale), chips, text(locale, "deliveries.list.results", shown.size()),
                sortHeaders(query), rows, pagination,
                rows.isEmpty() ? emptyState(query, locale) : null,
                chips.size() - (query.focus() == null ? 0 : 1));
    }

    private List<Delivery> read(List<String> storeIds, Function<String, List<Delivery>> reader, ListActor actor) {
        return storeIds.stream().flatMap(id -> reader.apply(id).stream())
                .filter(d -> !actor.superAdmin() || d.getConnectionMode() == ConnectionMode.GLOBAL)
                .toList();
    }

    private static List<Delivery> distinct(List<Delivery> first, List<Delivery> more) {
        Map<String, Delivery> byId = new LinkedHashMap<>();
        Stream.concat(first.stream(), more.stream()).forEach(d -> byId.putIfAbsent(d.getStoreId() + "/" + d.getDeliveryId(), d));
        return new ArrayList<>(byId.values());
    }

    private List<Tile> tiles(ListActor actor, DeliveryListQuery query, List<Delivery> transit, List<Delivery> toSettle,
                             LocalDate today, Locale locale) {
        List<DeliveryAttention> kinds = actor.superAdmin() ? DeliveryAttention.forSuperAdmin() : DeliveryAttention.forStore();
        return kinds.stream().map(kind -> {
            long count = kind == DeliveryAttention.INVOICE ? toSettleCount(actor, query, toSettle)
                    : transit.stream().filter(d -> kind.matches(d, DeliveryListState.of(d), today)).count();
            boolean active = kind == query.focus();
            String key = "deliveries.list.attention." + kind.param();
            return new Tile(text(locale, key), count, text(locale, key + ".hint"),
                    active ? query.withoutFocus().href() : query.withFocus(kind).href(), active);
        }).toList();
    }

    /**
     * The COUNT can include a stale index entry that the full read (recomputed keys) would drop, so the tile may
     * briefly show one more than the list; that is accepted to keep the backlog out of every page view. When the
     * backlog is already loaded for its own list, its size is exact and is used instead.
     */
    private long toSettleCount(ListActor actor, DeliveryListQuery query, List<Delivery> loaded) {
        if (query.focus() == DeliveryAttention.INVOICE) {
            return loaded.size();
        }
        return deliveries.countToSettle(actor.storeId());
    }

    private List<ScopeOption> scopes(DeliveryListQuery query, int transitCount, Locale locale) {
        return Arrays.stream(Scope.values()).map(scope -> new ScopeOption(
                text(locale, "deliveries.list.scope." + scope.param()),
                scope == Scope.TRANSIT ? Long.valueOf(transitCount) : null,
                query.withScope(scope).href(), scope == query.scope())).toList();
    }

    private static boolean orderedWithin(Delivery d, DeliveryListQuery query) {
        LocalDate ordered = d.getOrderedAt() == null ? null : d.getOrderedAt().toLocalDate();
        if (query.from() != null && (ordered == null || ordered.isBefore(query.from()))) return false;
        return query.to() == null || (ordered != null && !ordered.isAfter(query.to()));
    }

    private static boolean matchesSettle(Delivery d, List<Settle> settle) {
        return settle.stream().allMatch(s -> switch (s) {
            // the own warehouse never gets a purchase invoice (DeliveryListSortKey), so it is neither missing one nor unsynced
            case NO_INVOICE -> !d.isInvoiced() && !SupplierRegistry.WAREHOUSE.equals(d.getProvider());
            case NO_SYNC -> d.isInvoiced() && !d.isSynced() && !SupplierRegistry.WAREHOUSE.equals(d.getProvider());
            case UNPAID -> !d.isPaid();
        });
    }

    private static boolean matchesSearch(Delivery d, String q) {
        if (q == null) return true;
        String needle = q.toLowerCase(Locale.ROOT);
        return StringUtils.startsWithIgnoreCase(d.getDeliveryId(), needle)
                || StringUtils.containsIgnoreCase(d.getExternalDeliveryId(), needle)
                || StringUtils.containsIgnoreCase(d.getCounterpartyShortcut(), needle);
    }

    private static boolean matchesStates(Delivery d, List<DeliveryListState> states) {
        return states.isEmpty() || states.contains(DeliveryListState.of(d));
    }

    private static boolean matchesProviders(Delivery d, List<String> providers) {
        return providers.isEmpty() || providers.contains(d.getProvider());
    }

    /** One group of the "Stan" menu: the received states or the ones still on their way; empty outside the query's scope. */
    private List<Option> stateOptions(DeliveryListQuery query, List<Delivery> base, boolean received, Locale locale) {
        Map<DeliveryListState, Long> counts = base.stream().collect(Collectors.groupingBy(DeliveryListState::of, Collectors.counting()));
        return Arrays.stream(DeliveryListState.values())
                .filter(s -> s.isReceived() == received)
                .filter(s -> query.scope() == Scope.ALL || s.isReceived() == (query.scope() == Scope.RECEIVED))
                .map(s -> new Option(s.param(), text(locale, s.messageKey()), counts.getOrDefault(s, 0L),
                        query.states().contains(s), query.toggleState(s).href()))
                .toList();
    }

    private List<Option> providerOptions(DeliveryListQuery query, List<Delivery> base, SupplierLabelMap labels,
                                         ListActor actor, Locale locale) {
        Map<String, Long> counts = base.stream().filter(d -> d.getProvider() != null)
                .collect(Collectors.groupingBy(Delivery::getProvider, Collectors.counting()));
        Map<String, String> choices = new LinkedHashMap<>();
        if (!actor.superAdmin()) {
            labels.options().forEach(o -> choices.put(o.identity(), o.label()));
            choices.put(SupplierRegistry.WAREHOUSE, text(locale, "deliveries.list.warehouse"));
        }
        base.stream().filter(d -> d.getProvider() != null)
                .forEach(d -> choices.computeIfAbsent(d.getProvider(), key -> providerLabel(d.getStoreId(), key, labels, actor, locale)));
        query.providers().forEach(p -> choices.computeIfAbsent(p, key -> providerLabel(actor.storeId(), key, labels, actor, locale)));
        return choices.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(e -> new Option(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0L),
                        query.providers().contains(e.getKey()), query.toggleProvider(e.getKey()).href()))
                .toList();
    }

    private String providerLabel(String storeId, String provider, SupplierLabelMap labels, ListActor actor, Locale locale) {
        if (SupplierRegistry.WAREHOUSE.equals(provider)) {
            return text(locale, "deliveries.list.warehouse");
        }
        // the super admin's map has no default store and identities are per store, hence the delivery's own store
        return actor.superAdmin() || labels.has(storeId, provider) ? labels.of(storeId, provider)
                : text(locale, "deliveries.list.supplier.typed", provider);
    }

    private List<Option> settleOptions(DeliveryListQuery query, List<Delivery> base, Locale locale) {
        return Arrays.stream(Settle.values()).map(s -> {
            List<Settle> chosen = Stream.concat(query.settle().stream(), Stream.of(s)).distinct().toList();
            return new Option(s.param(), text(locale, "deliveries.list.settle." + s.param()),
                    base.stream().filter(d -> matchesSettle(d, chosen)).count(),
                    query.settle().contains(s), query.toggleSettle(s).href());
        }).toList();
    }

    private String summary(int selected, Locale locale) {
        return selected == 0 ? text(locale, "deliveries.list.menu.all") : text(locale, "deliveries.list.menu.selected", selected);
    }

    private DateMenu dateMenu(DeliveryListQuery query, LocalDate today, Locale locale) {
        boolean history = query.scope().includesHistory();
        String key = text(locale, history ? "deliveries.list.dates.received" : "deliveries.list.dates.ordered");
        boolean window = history && query.from() == null && query.to() == null && !query.allHistory()
                && query.focus() != DeliveryAttention.INVOICE;
        String value = query.from() != null || query.to() != null
                ? (query.from() == null ? "…" : query.from().toString()) + " – " + (query.to() == null ? "…" : query.to().toString())
                : window ? text(locale, "deliveries.list.dates.window")
                : history ? text(locale, "deliveries.list.dates.allHistory") : text(locale, "deliveries.list.dates.any");
        return new DateMenu(key, value, query.from() == null ? null : query.from().toString(),
                query.to() == null ? null : query.to().toString(), query.withAllHistory().href(), window);
    }

    private List<Chip> chips(DeliveryListQuery query, Map<String, String> providerNames, LocalDate today, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (query.focus() != null) {
            chip(chips, text(locale, "deliveries.list.attention." + query.focus().param()), query.withoutFocus().href(), locale);
        }
        query.states().forEach(s -> chip(chips, text(locale, "deliveries.list.chip.state", text(locale, s.messageKey())),
                query.withoutState(s).href(), locale));
        query.providers().forEach(p -> chip(chips, text(locale, "deliveries.list.chip.provider",
                providerNames.getOrDefault(p, p)),
                query.withoutProvider(p).href(), locale));
        query.settle().forEach(s -> chip(chips, text(locale, "deliveries.list.chip.settle", text(locale, "deliveries.list.settle." + s.param())),
                query.withoutSettle(s).href(), locale));
        if (query.from() != null || query.to() != null) {
            DateMenu dates = dateMenu(query, today, locale);
            chip(chips, text(locale, "deliveries.list.chip.dates", dates.key(),
                    query.from() == null ? "…" : query.from().toString(), query.to() == null ? "…" : query.to().toString()),
                    query.withoutDates().href(), locale);
        }
        if (query.allHistory()) {
            chip(chips, text(locale, "deliveries.list.dates.allHistory"), query.withoutDates().href(), locale);
        }
        if (query.q() != null) {
            chip(chips, text(locale, "deliveries.list.chip.search", query.q()), query.withQ(null).href(), locale);
        }
        return chips;
    }

    private void chip(List<Chip> chips, String label, String clearHref, Locale locale) {
        chips.add(new Chip(label, clearHref, text(locale, "deliveries.list.chip.clearLabel", label)));
    }

    private static Comparator<Delivery> comparator(DeliveryListQuery query, SupplierLabelMap labels) {
        boolean transitDesc = query.effectiveDir() == DeliveryListQuery.Direction.DESC;
        // without a chosen direction the history is newest first, whatever the transit part does
        boolean historyDesc = query.dir() == null || transitDesc;
        Comparator<Delivery> column = switch (query.effectiveSort()) {
            case DUE -> Comparator.comparing(DeliveryListService::sortDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case ORDERED -> Comparator.comparing(Delivery::getOrderedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case NUMBER -> Comparator.comparing(Delivery::getDeliveryId);
            case SUPPLIER -> Comparator.comparing((Delivery d) -> StringUtils.defaultString(labels.of(d.getStoreId(), d.getProvider())),
                    String.CASE_INSENSITIVE_ORDER);
            case STATUS -> Comparator.comparing(d -> DeliveryListState.of(d).ordinal());
            case COST -> Comparator.comparingDouble(Delivery::getTotalCostGross);
        };
        Comparator<Delivery> transitColumn = transitDesc ? column.reversed() : column;
        Comparator<Delivery> historyColumn = historyDesc ? column.reversed() : column;
        Comparator<Delivery> byPart = (a, b) -> a.getReceivedAt() == null ? transitColumn.compare(a, b) : historyColumn.compare(a, b);
        // on their way before received (the "Wszystkie" scope), undated deliveries always last of their part
        Comparator<Delivery> part = Comparator.comparing(d -> d.getReceivedAt() == null ? 0 : 1);
        Comparator<Delivery> undatedLast = Comparator.comparing(d -> query.effectiveSort() == Sort.DUE && sortDate(d) == null ? 1 : 0);
        return part.thenComparing(undatedLast).thenComparing(byPart).thenComparing(Delivery::getDeliveryId);
    }

    private static LocalDate sortDate(Delivery d) {
        return d.getReceivedAt() != null ? d.getReceivedAt().toLocalDate() : d.getEstimatedDeliveryAt();
    }

    private Map<Sort, SortHeader> sortHeaders(DeliveryListQuery query) {
        Map<Sort, SortHeader> headers = new EnumMap<>(Sort.class);
        for (Sort sort : Sort.values()) {
            String aria = query.effectiveSort() != sort ? "none"
                    : query.effectiveDir() == DeliveryListQuery.Direction.ASC ? "ascending" : "descending";
            headers.put(sort, new SortHeader(query.toggleSort(sort).href(), aria));
        }
        return headers;
    }

    private EmptyState emptyState(DeliveryListQuery query, Locale locale) {
        if (query.q() != null && query.scope() == Scope.TRANSIT) {
            return new EmptyState(text(locale, "deliveries.list.empty.searchTransit", query.q()),
                    text(locale, "deliveries.list.empty.searchTransit.action"), query.withScope(Scope.ALL).href());
        }
        if (query.isFiltered()) {
            return new EmptyState(text(locale, "deliveries.list.empty.filtered"), text(locale, "general.clear.filters"),
                    query.cleared().href());
        }
        if (query.scope() == Scope.TRANSIT) {
            return new EmptyState(text(locale, "deliveries.list.empty.transit"), text(locale, "deliveries.list.empty.transit.action"),
                    query.withScope(Scope.RECEIVED).href());
        }
        return query.allHistory()
                ? new EmptyState(text(locale, "deliveries.list.empty.history"), null, null)
                : new EmptyState(text(locale, "deliveries.list.empty.history"), text(locale, "deliveries.list.empty.history.action"),
                query.withAllHistory().href());
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
