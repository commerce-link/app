package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.builtin.DestroyedListQuery.Period;
import pl.commercelink.warehouse.builtin.WarehousePageModel.*;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.Pagination;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The destroyed archive (spec §6): one read of the store's destroyed items, as before the redesign, then tiles, menu
 * counts, search, sort and paging in memory. No delivery is read: the row mapper builds the Delivery cell from the item.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DestroyedListService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<Period> TILES = List.of(Period.MONTH, Period.PREVIOUS, Period.YEAR, Period.ALL);

    private final WarehouseRepository repository;
    private final DeliveryRedirectResolver redirects;
    private final MessageSource messages;
    private final SupplierLabels supplierLabels;
    private final SupplierRegistry suppliers;

    DestroyedPageModel page(String storeId, boolean admin, DestroyedListQuery query, LocalDate today, Locale locale) {
        List<WarehouseItem> all = repository.findAll(storeId, FulfilmentStatus.Destroyed);
        List<WarehouseItem> forCategories = all.stream()
                .filter(i -> query.includes(i.getDestroyedAt(), today))
                .filter(i -> WarehouseListService.matchesSearch(i, query.q())).toList();
        Collator collator = Collator.getInstance(locale);
        List<WarehouseItem> shown = forCategories.stream()
                .filter(i -> query.categories().isEmpty() || query.categories().contains(WarehouseRowMapper.categoryValue(i.getCategory())))
                .sorted(comparator(query, collator)).toList();

        Pagination pagination = Pagination.of(query.page(), shown.size(), DestroyedListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        SupplierLabelMap labels = supplierLabels.forStoreId(storeId);
        WarehouseRowMapper mapper = new WarehouseRowMapper(messages, locale, redirects, labels::of, labels::has,
                id -> labels.has(id) || suppliers.exists(id), admin, query.href());
        List<DestroyedPageModel.Row> rows = shown.subList(pagination.fromIndex(), pagination.toIndex()).stream()
                .map(i -> row(i, mapper.map(i), locale)).toList();

        List<Chip> chips = chips(query, locale);
        return new DestroyedPageModel(query, tiles(query, all, today, locale), dateSummary(query, locale),
                query.from() == null ? null : query.from().toString(), query.to() == null ? null : query.to().toString(),
                categoryOptions(query, forCategories, collator, locale), categorySummary(query, locale), chips,
                text(locale, "warehouse.destroyed.results", shown.size(), shown.stream().mapToInt(WarehouseItem::getQty).sum(), net(shown)),
                sortHeaders(query), rows, pagination,
                rows.isEmpty() ? emptyState(query, all.isEmpty(), locale) : null, all.isEmpty(), chips.size());
    }

    private List<Tile> tiles(DestroyedListQuery query, List<WarehouseItem> all, LocalDate today, Locale locale) {
        return TILES.stream().map(p -> {
            DestroyedListQuery periodQuery = query.cleared().withPeriod(p);
            List<WarehouseItem> counted = all.stream().filter(i -> periodQuery.includes(i.getDestroyedAt(), today)).toList();
            return new Tile(text(locale, "warehouse.destroyed.period." + key(p)), net(counted), text(locale, "warehouse.tile.value.unit"),
                    text(locale, "warehouse.destroyed.tile.hint", counted.stream().mapToInt(WarehouseItem::getQty).sum()),
                    query.withPeriod(p).href(), query.period() == p && !query.hasCustomRange());
        }).toList();
    }

    private DestroyedPageModel.Row row(WarehouseItem item, WarehouseItemRow row, Locale locale) {
        String documents = row.hasDelivery() && StringUtils.isNotBlank(item.getManufacturerCode())
                ? "/dashboard/warehouse-documents/delivery-mfn-history?deliveryId=" + encode(item.getDeliveryId())
                  + "&mfn=" + encode(item.getManufacturerCode()) + "&from=item&itemId=" + encode(item.getItemId())
                : null;
        String serial = Arrays.stream(StringUtils.defaultString(item.getSerialNo()).split(","))
                .map(String::trim).filter(StringUtils::isNotEmpty).findFirst().orElse(null);
        return new DestroyedPageModel.Row(row, item.getDestroyedAt() == null ? "—" : DATE.format(item.getDestroyedAt()), documents,
                serial == null ? null : "/dashboard/item/history?serialNo=" + encode(serial),
                text(locale, "warehouse.destroyed.row.menu", item.getName()));
    }

    private List<Option> categoryOptions(DestroyedListQuery query, List<WarehouseItem> base, Collator collator, Locale locale) {
        Map<String, Long> counts = base.stream().collect(Collectors.groupingBy(i -> WarehouseRowMapper.categoryValue(i.getCategory()), Collectors.counting()));
        Set<String> named = new TreeSet<>(collator);
        counts.keySet().stream().filter(c -> !c.equals(WarehouseListQuery.NO_CATEGORY)).forEach(named::add);
        query.categories().stream().filter(c -> !c.equals(WarehouseListQuery.NO_CATEGORY)).forEach(named::add);
        List<Option> options = new ArrayList<>();
        named.forEach(c -> options.add(new Option(c, c, counts.getOrDefault(c, 0L), query.categories().contains(c), query.toggleCategory(c).href())));
        long none = counts.getOrDefault(WarehouseListQuery.NO_CATEGORY, 0L);
        if (none > 0 || query.categories().contains(WarehouseListQuery.NO_CATEGORY)) {
            options.add(new Option(WarehouseListQuery.NO_CATEGORY, text(locale, "warehouse.category.none"), none,
                    query.categories().contains(WarehouseListQuery.NO_CATEGORY), query.toggleCategory(WarehouseListQuery.NO_CATEGORY).href()));
        }
        return options;
    }

    private String categorySummary(DestroyedListQuery query, Locale locale) {
        if (query.categories().isEmpty()) return text(locale, "warehouse.list.menu.all");
        return query.categories().size() == 1 ? categoryLabel(query.categories().get(0), locale)
                : text(locale, "warehouse.list.menu.selected", query.categories().size());
    }

    private String dateSummary(DestroyedListQuery query, Locale locale) {
        if (query.hasCustomRange()) {
            return text(locale, "warehouse.destroyed.dates.range",
                    query.from() == null ? "…" : DATE.format(query.from()), query.to() == null ? "…" : DATE.format(query.to()));
        }
        return text(locale, "warehouse.destroyed.period." + key(query.period()));
    }

    private List<Chip> chips(DestroyedListQuery query, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        if (query.hasCustomRange() || query.period() != Period.ALL) {
            chip(chips, text(locale, "warehouse.destroyed.chip.date", dateSummary(query, locale)), query.withPeriod(Period.ALL).href(), locale);
        }
        query.categories().forEach(c -> chip(chips, text(locale, "warehouse.list.chip.category", categoryLabel(c, locale)),
                query.withoutCategory(c).href(), locale));
        if (query.q() != null) {
            chip(chips, text(locale, "warehouse.list.chip.search", query.q()), query.withQ(null).href(), locale);
        }
        return chips;
    }

    private void chip(List<Chip> chips, String label, String href, Locale locale) {
        chips.add(new Chip(label, href, text(locale, "warehouse.list.chip.clearLabel", label)));
    }

    private Map<DestroyedListQuery.Sort, SortHeader> sortHeaders(DestroyedListQuery query) {
        Map<DestroyedListQuery.Sort, SortHeader> headers = new EnumMap<>(DestroyedListQuery.Sort.class);
        for (DestroyedListQuery.Sort sort : DestroyedListQuery.Sort.values()) {
            String aria = query.effectiveSort() != sort ? "none"
                    : query.effectiveDir() == WarehouseListQuery.Direction.ASC ? "ascending" : "descending";
            headers.put(sort, new SortHeader(query.toggleSort(sort).href(), aria));
        }
        return headers;
    }

    private static Comparator<WarehouseItem> comparator(DestroyedListQuery query, Collator collator) {
        Comparator<WarehouseItem> byName = Comparator.comparing(i -> StringUtils.defaultString(i.getName()), collator);
        boolean desc = query.effectiveDir() == WarehouseListQuery.Direction.DESC;
        Comparator<WarehouseItem> column = switch (query.effectiveSort()) {
            case NAME -> byName;
            case CATEGORY -> Comparator.<WarehouseItem, Boolean>comparing(i -> WarehouseRowMapper.uncategorized(i.getCategory()))
                    .thenComparing(i -> StringUtils.defaultString(i.getCategory()), collator);
            case QTY -> Comparator.comparingInt(WarehouseItem::getQty);
            case COST -> Comparator.comparingDouble(WarehouseItem::getCost);
            case DATE -> Comparator.comparing(WarehouseItem::getDestroyedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
        };
        if (desc) column = column.reversed();
        // an item without a date (data from before destroyedAt existed) goes last in both directions
        Comparator<WarehouseItem> undatedLast = Comparator.comparing(i -> query.effectiveSort() == DestroyedListQuery.Sort.DATE && i.getDestroyedAt() == null);
        return undatedLast.thenComparing(column).thenComparing(byName).thenComparing(WarehouseItem::getItemId);
    }

    private WarehousePageModel.EmptyState emptyState(DestroyedListQuery query, boolean storeEmpty, Locale locale) {
        return storeEmpty ? new EmptyState(text(locale, "warehouse.destroyed.empty.store"), null, null)
                : new EmptyState(text(locale, "warehouse.destroyed.empty.filtered"), text(locale, "general.clear.filters"), query.cleared().href());
    }

    private String categoryLabel(String value, Locale locale) {
        return value.equals(WarehouseListQuery.NO_CATEGORY) ? text(locale, "warehouse.category.none") : value;
    }

    private static String net(List<WarehouseItem> items) {
        return Money.format(items.stream().mapToDouble(i -> i.totalUnitCost().netValue()).sum());
    }

    private static String key(Period p) {
        return p == Period.ALL ? "all" : p.param();
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
