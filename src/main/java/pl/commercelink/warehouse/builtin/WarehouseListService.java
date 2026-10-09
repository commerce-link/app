package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import pl.commercelink.documents.DocumentReason;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryRedirectResolver;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;
import pl.commercelink.warehouse.builtin.WarehousePageModel.*;
import pl.commercelink.web.orders.Money;
import pl.commercelink.web.orders.Pagination;

import java.text.Collator;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pl.commercelink.orders.FulfilmentStatus.*;

/**
 * The warehouse list (spec §4): one read of the store's visible and destroyed items, then tiles, menu counts, search,
 * sort and paging in memory. Every menu counts what the list would show after picking its option, so it leaves out only
 * its own filter (deliveries list rule).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseListService {

    static final List<DocumentReason> DESTROY_REASONS =
            List.of(DocumentReason.StockAdjustment, DocumentReason.Destruction, DocumentReason.InternalUse, DocumentReason.Theft);

    private final WarehouseRepository repository;
    private final DeliveriesRepository deliveries;
    private final DeliveryRedirectResolver redirects;
    private final MessageSource messages;
    private final SupplierLabels supplierLabels;

    WarehousePageModel page(String storeId, boolean wms, boolean admin, WarehouseListQuery query, Locale locale) {
        List<FulfilmentStatus> visible = WarehouseStatuses.visible(wms);
        List<FulfilmentStatus> read = Stream.concat(visible.stream(), Stream.of(Destroyed)).toList();
        List<WarehouseItem> all = repository.findAllFiltered(storeId, null, read);
        long destroyed = all.stream().filter(i -> i.getStatus() == Destroyed).count();
        List<WarehouseItem> store = all.stream().filter(i -> visible.contains(i.getStatus())).toList();

        List<WarehouseItem> searched = store.stream().filter(i -> matchesSearch(i, query.q())).toList();
        List<WarehouseItem> forStatuses = searched.stream().filter(i -> matchesCategories(i, query.categories())).toList();
        List<WarehouseItem> forCategories = searched.stream().filter(i -> query.statuses().contains(i.getStatus())).toList();
        Collator collator = Collator.getInstance(locale);
        List<WarehouseItem> shown = forCategories.stream().filter(i -> matchesCategories(i, query.categories()))
                .sorted(comparator(query, collator, visible)).toList();

        Pagination pagination = Pagination.of(query.page(), shown.size(), WarehouseListQuery.PAGE_SIZE, n -> query.withPage(n).href());
        List<WarehouseItem> pageItems = shown.subList(pagination.fromIndex(), pagination.toIndex());
        SupplierLabelMap labels = supplierLabels.forStoreId(storeId);
        WarehouseRowMapper mapper = new WarehouseRowMapper(messages, locale, redirects, deliveries(storeId, pageItems), labels::of,
                labels::has, admin);
        List<WarehouseItemRow> rows = pageItems.stream().map(mapper::map).toList();

        List<Chip> chips = chips(query, locale);
        return new WarehousePageModel(query, admin, wms, tiles(query, store, wms, locale),
                statusOptions(query, forStatuses, locale), statusSummary(query, locale),
                categoryOptions(query, forCategories, collator, locale), summary(query.categories().size(), categoryLabels(query, locale), locale),
                chips, results(shown, locale),
                sortHeaders(query), rows, pagination,
                rows.isEmpty() ? emptyState(query, store.isEmpty(), locale) : null, store.isEmpty(),
                chips.size(), destroyed,
                WarehouseBulkAction.menu().stream().map(a -> view(a, locale)).toList(), view(WarehouseBulkAction.DESTROY, locale),
                DESTROY_REASONS.stream().map(r -> new Option(r.name(), text(locale, "DocumentReason." + r.name()), 0, false, null)).toList());
    }

    /** The store's deliveries behind the page's items, one read per distinct id; an id with no record is left out. */
    private Map<String, Delivery> deliveries(String storeId, List<WarehouseItem> items) {
        Map<String, Delivery> byId = new HashMap<>();
        items.stream().map(WarehouseItem::getDeliveryId).filter(StringUtils::isNotBlank).distinct().forEach(id -> {
            Delivery delivery = deliveries.findById(storeId, id);
            if (delivery != null) {
                byId.put(id, delivery);
            }
        });
        return byId;
    }

    private Results results(List<WarehouseItem> shown, Locale locale) {
        if (shown.isEmpty()) {
            return new Results(text(locale, "warehouse.list.results.empty"), null, null, null);
        }
        return new Results(text(locale, "warehouse.list.results.count", shown.size(), shown.stream().mapToInt(WarehouseItem::getQty).sum()),
                text(locale, "warehouse.list.results.amount", Money.format(shown.stream().mapToDouble(i -> i.totalUnitCost().netValue()).sum())),
                text(locale, "warehouse.list.results.gross"),
                text(locale, "warehouse.list.results.amount", Money.format(shown.stream().mapToDouble(i -> i.totalUnitCost().grossValue()).sum())));
    }

    private List<Tile> tiles(WarehouseListQuery query, List<WarehouseItem> store, boolean wms, Locale locale) {
        if (wms) {
            return List.of(unitsTile(query, store, List.of(Ordered), "warehouse.tile.ordered", null, locale),
                    unitsTile(query, store, List.of(Allocation), "warehouse.tile.allocation", null, locale),
                    unitsTile(query, store, List.of(New), "warehouse.tile.new", null, locale),
                    unitsTile(query, store, WarehouseStatuses.visible(true), "warehouse.tile.all", "warehouse.tile.all.hint", locale));
        }
        return List.of(unitsTile(query, store, List.of(Delivered), "warehouse.tile.stock", null, locale),
                unitsTile(query, store, List.of(Allocation, Ordered), "warehouse.tile.toReceive", "warehouse.tile.toReceive.hint", locale),
                unitsTile(query, store, List.of(Reserved), "warehouse.tile.reserved", null, locale),
                unitsTile(query, store, List.of(InRMA, InExternalService), "warehouse.tile.attention", "warehouse.tile.attention.hint", locale));
    }

    private Tile unitsTile(WarehouseListQuery query, List<WarehouseItem> store, List<FulfilmentStatus> statuses, String key,
                           String hintKey, Locale locale) {
        List<WarehouseItem> counted = store.stream().filter(i -> statuses.contains(i.getStatus())).toList();
        boolean active = query.statuses().equals(statuses) && query.categories().isEmpty() && query.q() == null;
        String hint = hintKey != null ? text(locale, hintKey) : text(locale, "warehouse.tile.items", counted.size());
        // an active tile clicked again switches its filter off, back to the default view (orders, deliveries, payments)
        String href = active ? query.cleared().href() : query.withStatuses(statuses).href();
        return new Tile(text(locale, key), text(locale, "warehouse.tile.units", counted.stream().mapToInt(WarehouseItem::getQty).sum()),
                hint, href, active);
    }

    private List<Option> statusOptions(WarehouseListQuery query, List<WarehouseItem> base, Locale locale) {
        Map<FulfilmentStatus, Long> counts = base.stream().collect(Collectors.groupingBy(WarehouseItem::getStatus, Collectors.counting()));
        // with every status ticked nothing narrows the list, so the menu shows no tick at all ("Wszystkie")
        boolean all = query.isAllStatuses();
        return WarehouseStatuses.visible(query.wms()).stream()
                .map(s -> new Option(s.name(), text(locale, WarehouseStatuses.labelKey(s)), counts.getOrDefault(s, 0L),
                        !all && query.statuses().contains(s), (all ? query.withStatuses(List.of(s)) : query.toggleStatus(s)).href()))
                .toList();
    }

    private String statusSummary(WarehouseListQuery query, Locale locale) {
        if (query.isAllStatuses()) {
            return text(locale, "warehouse.list.menu.all");
        }
        return query.statuses().size() == 1 ? text(locale, WarehouseStatuses.labelKey(query.statuses().get(0)))
                : text(locale, "warehouse.list.menu.selected", query.statuses().size());
    }

    private List<Option> categoryOptions(WarehouseListQuery query, List<WarehouseItem> base, Collator collator, Locale locale) {
        Map<String, Long> counts = base.stream().collect(Collectors.groupingBy(i -> WarehouseRowMapper.categoryValue(i.getCategory()), Collectors.counting()));
        Set<String> named = new TreeSet<>(collator);
        base.stream().map(i -> WarehouseRowMapper.categoryValue(i.getCategory())).filter(c -> !c.equals(WarehouseListQuery.NO_CATEGORY)).forEach(named::add);
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

    private List<String> categoryLabels(WarehouseListQuery query, Locale locale) {
        return query.categories().stream().map(c -> c.equals(WarehouseListQuery.NO_CATEGORY) ? text(locale, "warehouse.category.none") : c).toList();
    }

    private String summary(int selected, List<String> labels, Locale locale) {
        if (selected == 0) return text(locale, "warehouse.list.menu.all");
        return selected == 1 ? labels.get(0) : text(locale, "warehouse.list.menu.selected", selected);
    }

    private List<Chip> chips(WarehouseListQuery query, Locale locale) {
        List<Chip> chips = new ArrayList<>();
        // the default status has no chip: the Status menu and the active tile already tell it, and with a chip "Wyczyść filtry"
        // put back the very filter the chip's × had just removed (orders list rule)
        if (query.isAllStatuses() && !query.isDefaultStatuses()) {
            chip(chips, text(locale, "warehouse.list.chip.status", text(locale, "warehouse.list.menu.all")), query.withDefaultStatuses().href(), locale);
        } else if (!query.isDefaultStatuses()) {
            query.statuses().forEach(s -> chip(chips, text(locale, "warehouse.list.chip.status", text(locale, WarehouseStatuses.labelKey(s))),
                    query.withoutStatus(s).href(), locale));
        }
        List<String> labels = categoryLabels(query, locale);
        for (int i = 0; i < labels.size(); i++) {
            chip(chips, text(locale, "warehouse.list.chip.category", labels.get(i)), query.withoutCategory(query.categories().get(i)).href(), locale);
        }
        if (query.q() != null) {
            chip(chips, text(locale, "warehouse.list.chip.search", query.q()), query.withQ(null).href(), locale);
        }
        return chips;
    }

    private void chip(List<Chip> chips, String label, String href, Locale locale) {
        chips.add(new Chip(label, href, text(locale, "warehouse.list.chip.clearLabel", label)));
    }

    private static boolean matchesCategories(WarehouseItem item, List<String> categories) {
        return categories.isEmpty() || categories.contains(WarehouseRowMapper.categoryValue(item.getCategory()));
    }

    private static boolean matchesSearch(WarehouseItem item, String q) {
        if (q == null) return true;
        // an EAN is stored without its leading zeros, so a scanned or typed EAN-13 is also tried in that form
        String asEan = UnifiedProductIdentifiers.unifyEan(q);
        return Stream.of(item.getName(), item.getEan(), item.getManufacturerCode(), item.getDeliveryId(), item.getSerialNo(), item.getComment())
                .map(field -> StringUtils.stripAccents(StringUtils.defaultString(field)))
                .anyMatch(field -> StringUtils.containsIgnoreCase(field, StringUtils.stripAccents(q)) || StringUtils.containsIgnoreCase(field, asEan));
    }

    private static Comparator<WarehouseItem> comparator(WarehouseListQuery query, Collator collator, List<FulfilmentStatus> visible) {
        Comparator<WarehouseItem> byName = Comparator.comparing(i -> StringUtils.defaultString(i.getName()), collator);
        Comparator<WarehouseItem> byCategory = Comparator.<WarehouseItem, Boolean>comparing(i -> WarehouseRowMapper.uncategorized(i.getCategory()))
                .thenComparing(i -> StringUtils.defaultString(i.getCategory()), collator);
        Comparator<WarehouseItem> column = switch (query.effectiveSort()) {
            case NAME -> byName;
            case CATEGORY -> byCategory;
            case QTY -> Comparator.comparingInt(WarehouseItem::getQty);
            case COST -> Comparator.comparingDouble(WarehouseItem::getCost);
            case STATUS -> Comparator.comparingInt(i -> visible.indexOf(i.getStatus()));
        };
        if (query.effectiveDir() == WarehouseListQuery.Direction.DESC) {
            column = column.reversed();
        }
        return column.thenComparing(byCategory).thenComparing(byName).thenComparing(WarehouseItem::getItemId);
    }

    private Map<WarehouseListQuery.Sort, SortHeader> sortHeaders(WarehouseListQuery query) {
        Map<WarehouseListQuery.Sort, SortHeader> headers = new EnumMap<>(WarehouseListQuery.Sort.class);
        for (WarehouseListQuery.Sort sort : WarehouseListQuery.Sort.values()) {
            String aria = query.effectiveSort() != sort ? "none"
                    : query.effectiveDir() == WarehouseListQuery.Direction.ASC ? "ascending" : "descending";
            headers.put(sort, new SortHeader(query.toggleSort(sort).href(), aria));
        }
        return headers;
    }

    private EmptyState emptyState(WarehouseListQuery query, boolean storeEmpty, Locale locale) {
        if (storeEmpty) {
            return new EmptyState(text(locale, "warehouse.list.empty.store"), text(locale, "warehouse.action.add.item"),
                    "/dashboard/warehouse/items/new");
        }
        return new EmptyState(text(locale, "warehouse.list.empty.filtered"), text(locale, "general.clear.filters"), query.cleared().href());
    }

    private BulkActionView view(WarehouseBulkAction action, Locale locale) {
        String prefix = "warehouse.bulk." + action.key();
        String statuses = action.allowed().stream().map(s -> text(locale, WarehouseStatuses.labelKey(s))).collect(Collectors.joining(", "));
        return new BulkActionView(action.key(), action.path(), text(locale, prefix + ".label"),
                action.allowed().stream().map(Enum::name).collect(Collectors.joining(" ")), action.needsQuantity(),
                action.needsConfirm(), action.sameSource(), action.danger(), text(locale, "warehouse.bulk.reason.status", statuses),
                action.needsConfirm() ? messages.getMessage(prefix + ".confirm.title", null, locale) : null,
                action.needsConfirm() ? messages.getMessage(prefix + ".confirm.message", null, locale) : null,
                text(locale, prefix + ".label"),
                // the quantity dialog's question, with {k} for the number of checked rows filled in by selection-actions.js
                action.needsQuantity() ? text(locale, prefix + ".dialog.title", "{k}") : null);
    }

    private String text(Locale locale, String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
