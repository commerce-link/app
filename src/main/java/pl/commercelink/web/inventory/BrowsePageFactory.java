package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.BrowseCriteria;
import pl.commercelink.inventory.BrowseIndex;
import pl.commercelink.inventory.BrowseResult;
import pl.commercelink.inventory.BrowseRow;
import pl.commercelink.inventory.BrowseSummary;
import pl.commercelink.inventory.InventoryBrowse;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.orders.Pagination;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowsePageFactory {

    private final InventoryBrowse inventoryBrowse;
    private final PimCategoryTree tree;
    private final CatalogPlacement catalogPlacement;
    private final SupplierLabels supplierLabels;
    private final WarehouseStockLookup warehouseStock;

    public BrowsePage build(@Nullable String storeId, BrowseQuery query, boolean admin, boolean superAdmin) {
        return build(storeId, query, admin, superAdmin, false);
    }

    /** @param freshPlacement read "W katalogu" past the cache: the request comes back from a save of products */
    public BrowsePage build(@Nullable String storeId, BrowseQuery query, boolean admin, boolean superAdmin,
                            boolean freshPlacement) {
        if (!inventoryBrowse.isReady()) {
            return BrowsePage.of(BrowsePage.Status.BUILDING, query, admin);
        }
        try {
            // Read once here, so a PIM that does not answer is a state of the page rather than an error halfway through.
            tree.topLevels();
        } catch (RuntimeException e) {
            log.warn("PIM category tree unavailable for the inventory page: {}", e.getMessage());
            return BrowsePage.of(BrowsePage.Status.PIM_UNAVAILABLE, query, admin);
        }
        BrowseSummary summary = inventoryBrowse.summary(storeId);
        Set<String> unknownIds = unknownCategoryIds(summary.byCategory());
        Map<String, Integer> counts = counts(summary.byCategory(), unknownIds);
        // Offers carry connection identities (Kosatec-k7f3a9c2); without a store they read as their legacy shape.
        SupplierLabelMap labels = storeId == null ? supplierLabels.forStore(null) : supplierLabels.forStoreId(storeId);
        boolean withCatalog = admin && !superAdmin && storeId != null;
        String category = query.category();
        boolean noSuppliers = summary.total() == 0 && !superAdmin;
        if (!noSuppliers && category != null && !BrowseIndex.UNASSIGNED.equals(category) && tree.find(category).isEmpty()) {
            return BrowsePage.of(BrowsePage.Status.UNKNOWN_CATEGORY, query, admin);
        }
        CatalogPlacement.StorePlacement placement = !withCatalog ? null
                : freshPlacement ? catalogPlacement.forStoreFresh(storeId) : catalogPlacement.forStore(storeId);
        // Every count beside the list follows the filters its links carry; without filters the store's summary is exact.
        Filters filters = new Filters(storeId, Set.copyOf(query.suppliers()), query.textTooShort() ? null : query.q2(),
                unknownIds, summary.total() > 0);

        List<BrowsePage.RowView> rows = List.of();
        int total = 0;
        boolean truncated = false;
        Pagination pagination = Pagination.of(1, 0, BrowseQuery.PAGE_SIZE, page -> query.withPage(page).href());
        if (!query.isStart() && !query.textTooShort() && summary.total() > 0) {
            Set<String> categoryIds = categoryIds(category, unknownIds);
            BrowseCriteria criteria = query.toCriteria(categoryIds);
            // A bookmarked page past the end comes back as the last page, and Pagination clamps to the same page.
            BrowseResult result = inventoryBrowse.browse(storeId, criteria);
            pagination = Pagination.of(query.page(), result.total(), BrowseQuery.PAGE_SIZE,
                    page -> query.withPage(page).href());
            boolean leaf = category != null && tree.childrenOf(category).isEmpty();
            Map<String, Long> inStock = warehouseStock.inStockByMfn(storeId, productCodes(result.rows()));
            rows = result.rows().stream().map(row -> rowView(row, query, placement, leaf, labels, inStock)).toList();
            total = result.total();
            truncated = result.truncated();
        }

        return new BrowsePage(query, admin, noSuppliers, query.textTooShort(),
                title(category), crumbs(category, query), subnav(category, counts, filters, query), isSiblings(category),
                tiles(query, counts, filters), supplierOptions(summary, supplierCounts(summary, category, filters), query, labels),
                chips(query, labels), query.cleared().href(),
                rows, total, truncated, pagination, sortHeaders(query), BrowsePage.Status.READY);
    }

    private record Filters(String storeId, Set<String> suppliers, String text, Set<String> unknownIds, boolean any) {

        boolean active() {
            return !suppliers.isEmpty() || text != null;
        }
    }

    /** Products per category with the parents counting their subtree, and unknown PIM ids counted as unassigned. */
    private Map<String, Integer> counts(Map<String, Integer> byCategory, Set<String> unknownIds) {
        Map<String, Integer> counts = new HashMap<>(CategoryCounts.rollUp(byCategory, tree));
        // A category id the PIM tree does not know cannot be reached by drill-down, so it browses as "Bez kategorii PIM".
        int unassigned = byCategory.getOrDefault(BrowseIndex.UNASSIGNED, 0)
                + unknownIds.stream().mapToInt(id -> byCategory.getOrDefault(id, 0)).sum();
        counts.put(BrowseIndex.UNASSIGNED, unassigned);
        return counts;
    }

    private Map<String, Integer> filteredCounts(Set<String> categoryIds, Filters filters) {
        return counts(inventoryBrowse.facets(filters.storeId(), categoryIds, filters.suppliers(), filters.text())
                .byCategory(), filters.unknownIds());
    }

    /** The supplier menu counts the current category and phrase, whichever suppliers are ticked. */
    private Map<String, Integer> supplierCounts(BrowseSummary summary, String category, Filters filters) {
        if ((category == null && filters.text() == null) || !filters.any()) {
            return summary.bySupplier();
        }
        return inventoryBrowse.facets(filters.storeId(), categoryIds(category, filters.unknownIds()), Set.of(),
                filters.text()).bySupplier();
    }

    private Set<String> categoryIds(String category, Set<String> unknownIds) {
        if (category == null) {
            return null;
        }
        if (BrowseIndex.UNASSIGNED.equals(category)) {
            Set<String> ids = new HashSet<>(unknownIds);
            ids.add(BrowseIndex.UNASSIGNED);
            return ids;
        }
        Set<String> subtree = tree.selfAndDescendants(category);
        return subtree.isEmpty() ? Set.of(category) : subtree;
    }

    private Set<String> unknownCategoryIds(Map<String, Integer> byCategory) {
        return byCategory.keySet().stream()
                .filter(id -> !BrowseIndex.UNASSIGNED.equals(id) && tree.find(id).isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private BrowsePage.RowView rowView(BrowseRow row, BrowseQuery query, CatalogPlacement.StorePlacement placement,
                                       boolean leafSelected, SupplierLabelMap labels, Map<String, Long> inStock) {
        CategoryLine category = CategoryLine.of(tree, placement, row.categoryId(), row.categoryText(), row.catalogKey(),
                leafSelected);
        String code = row.ean() != null ? row.ean() : row.mfn();
        String detailHref = InventoryPageController.PRICES_PATH + "?q=" + encode(code) + "&from=" + encode(query.href());
        String addHref = row.ean() == null || row.ean().isBlank() ? null : query.hrefWith("open=add&ean=" + encode(row.ean()));
        return new BrowsePage.RowView(row.name(), row.brand(), row.ean(), row.mfn(), detailHref, category,
                row.lowestDeliveredNet(), row.deliveryKnown(), labels.of(row.lowestSupplier()), row.qty(), row.suppliers(),
                warehouseQty(row, inStock), addHref);
    }

    private static Set<String> productCodes(List<BrowseRow> rows) {
        return rows.stream().flatMap(row -> row.key().getProductCodes().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static long warehouseQty(BrowseRow row, Map<String, Long> inStock) {
        return row.key().getProductCodes().stream().mapToLong(code -> inStock.getOrDefault(code, 0L)).sum();
    }

    private String title(String category) {
        if (category == null) {
            return null;
        }
        if (BrowseIndex.UNASSIGNED.equals(category)) {
            return null;
        }
        return tree.find(category).map(PimCategory::name).orElse(category);
    }

    /** Going up keeps the filters, the phrase and the sort, as going down through the pills does. */
    private List<BrowsePage.Crumb> crumbs(String category, BrowseQuery query) {
        List<BrowsePage.Crumb> crumbs = new ArrayList<>();
        crumbs.add(new BrowsePage.Crumb(null, "inventory.browse.all", query.withCategory(null).href()));
        if (category == null) {
            return crumbs;
        }
        if (BrowseIndex.UNASSIGNED.equals(category)) {
            crumbs.add(new BrowsePage.Crumb(null, "inventory.browse.unassigned", null));
            return crumbs;
        }
        List<String> ids = new ArrayList<>();
        String current = category;
        while (current != null && !ids.contains(current) && tree.find(current).isPresent()) {
            ids.add(0, current);
            current = tree.parentIdOf(current).orElse(null);
        }
        for (String id : ids) {
            String name = tree.find(id).map(PimCategory::name).orElse(id);
            crumbs.add(new BrowsePage.Crumb(name, null, id.equals(category) ? null : query.withCategory(id).href()));
        }
        return crumbs;
    }

    private boolean isSiblings(String category) {
        return category != null && tree.find(category).isPresent() && tree.childrenOf(category).isEmpty();
    }

    private List<BrowsePage.NavItem> subnav(String category, Map<String, Integer> storeCounts, Filters filters,
                                            BrowseQuery query) {
        if (category == null || tree.find(category).isEmpty()) {
            return List.of();
        }
        boolean siblings = tree.childrenOf(category).isEmpty();
        List<PimCategory> items = siblings ? tree.siblingsOf(category) : tree.childrenOf(category);
        Map<String, Integer> counts = storeCounts;
        if (filters.active() && filters.any() && !items.isEmpty()) {
            // Siblings live under the parent, outside the current category; a top-level leaf's siblings are the top levels.
            Set<String> scope = !siblings ? tree.selfAndDescendants(category)
                    : tree.parentIdOf(category).map(tree::selfAndDescendants).orElse(null);
            counts = filteredCounts(scope, filters);
        }
        Map<String, Integer> shown = counts;
        return items.stream()
                .filter(item -> shown.getOrDefault(item.id(), 0) > 0 || item.id().equals(category))
                .map(item -> new BrowsePage.NavItem(item.name(), shown.getOrDefault(item.id(), 0),
                        query.withCategory(item.id()).href(), item.id().equals(category)))
                .toList();
    }

    private List<BrowsePage.Tile> tiles(BrowseQuery query, Map<String, Integer> storeCounts, Filters filters) {
        if (!query.isStart()) {
            return List.of();
        }
        Map<String, Integer> counts = filters.active() && filters.any() ? filteredCounts(null, filters) : storeCounts;
        List<BrowsePage.Tile> tiles = new ArrayList<>(tree.topLevels().stream()
                .filter(top -> counts.getOrDefault(top.id(), 0) > 0)
                .sorted(Comparator.comparingInt((PimCategory top) -> counts.get(top.id())).reversed())
                .map(top -> new BrowsePage.Tile(top.name(), null, counts.get(top.id()), largestChildren(top.id(), counts),
                        query.withCategory(top.id()).href()))
                .toList());
        int unassigned = counts.getOrDefault(BrowseIndex.UNASSIGNED, 0);
        if (unassigned > 0) {
            tiles.add(new BrowsePage.Tile(null, "inventory.browse.unassigned", unassigned, null,
                    query.withCategory(BrowseIndex.UNASSIGNED).href()));
        }
        return tiles;
    }

    private String largestChildren(String id, Map<String, Integer> counts) {
        List<String> names = tree.childrenOf(id).stream()
                .filter(child -> counts.getOrDefault(child.id(), 0) > 0)
                .sorted(Comparator.comparingInt((PimCategory child) -> counts.get(child.id())).reversed())
                .limit(3)
                .map(PimCategory::name)
                .toList();
        return names.isEmpty() ? null : String.join(", ", names);
    }

    /** The store's suppliers that have products here, and the ticked ones even when they have none, to untick them. */
    private static List<BrowsePage.MenuOption> supplierOptions(BrowseSummary summary, Map<String, Integer> counts,
                                                               BrowseQuery query, SupplierLabelMap labels) {
        List<String> shown = summary.bySupplier().keySet().stream()
                .filter(supplier -> counts.getOrDefault(supplier, 0) > 0 || query.suppliers().contains(supplier))
                .toList();
        Map<String, Long> labelUses = shown.stream()
                .collect(Collectors.groupingBy(labels::of, Collectors.counting()));
        return shown.stream()
                .map(supplier -> {
                    String label = labels.of(supplier);
                    // Two connections under one label would be two identical checkboxes; the identity tells them apart.
                    String text = labelUses.get(label) > 1 ? label + " (" + supplier + ")" : label;
                    return new BrowsePage.MenuOption(supplier, text, counts.getOrDefault(supplier, 0),
                            query.suppliers().contains(supplier));
                })
                .sorted(Comparator.comparing(BrowsePage.MenuOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<BrowsePage.Chip> chips(BrowseQuery query, SupplierLabelMap labels) {
        List<BrowsePage.Chip> chips = new ArrayList<>();
        if (query.q2() != null) {
            chips.add(new BrowsePage.Chip("inventory.browse.chip.text", query.q2(), query.withoutText().href()));
        }
        query.suppliers().forEach(supplier -> chips.add(new BrowsePage.Chip("inventory.browse.chip.supplier",
                labels.of(supplier), query.withoutSupplier(supplier).href())));
        return chips;
    }

    private static Map<String, BrowsePage.SortHeader> sortHeaders(BrowseQuery query) {
        Map<String, BrowsePage.SortHeader> headers = new LinkedHashMap<>();
        for (BrowseCriteria.Sort sort : BrowseCriteria.Sort.values()) {
            String aria = query.sort() != sort ? "none" : query.descending() ? "descending" : "ascending";
            headers.put(sort.name(), new BrowsePage.SortHeader(query.toggleSort(sort).href(), aria));
        }
        return headers;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
