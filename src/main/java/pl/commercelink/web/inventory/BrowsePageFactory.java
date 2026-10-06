package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.BrowseCriteria;
import pl.commercelink.inventory.BrowseIndex;
import pl.commercelink.inventory.BrowseResult;
import pl.commercelink.inventory.BrowseRow;
import pl.commercelink.inventory.BrowseSummary;
import pl.commercelink.inventory.InventoryBrowse;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.web.catalog.CatalogPaths;
import pl.commercelink.web.orders.Pagination;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class BrowsePageFactory {

    private final InventoryBrowse inventoryBrowse;
    private final PimCategoryTree tree;
    private final CatalogPlacement catalogPlacement;

    public BrowsePage build(@Nullable String storeId, BrowseQuery query, boolean admin, boolean superAdmin) {
        BrowseSummary summary = inventoryBrowse.summary(storeId);
        Map<String, Integer> counts = CategoryCounts.rollUp(summary.byCategory(), tree);
        boolean withCatalog = admin && !superAdmin && storeId != null;
        CatalogPlacement.StorePlacement placement = withCatalog ? catalogPlacement.forStore(storeId) : null;
        String category = query.category();

        List<BrowsePage.RowView> rows = List.of();
        int total = 0;
        boolean truncated = false;
        Pagination pagination = Pagination.of(1, 0, BrowseQuery.PAGE_SIZE, page -> query.withPage(page).href());
        if (!query.isStart() && !query.textTooShort() && summary.total() > 0) {
            Set<String> categoryIds = categoryIds(category);
            BrowseCriteria criteria = query.toCriteria(categoryIds).withRowFilter(catalogFilter(query, placement));
            BrowseResult result = inventoryBrowse.browse(storeId, criteria);
            pagination = Pagination.of(query.page(), result.total(), BrowseQuery.PAGE_SIZE,
                    page -> query.withPage(page).href());
            if (pagination.page() != query.page()) {
                // A bookmarked page past the end shows the last page instead of an empty table.
                result = inventoryBrowse.browse(storeId, query.withPage(pagination.page()).toCriteria(categoryIds)
                        .withRowFilter(catalogFilter(query, placement)));
            }
            boolean leaf = category != null && tree.childrenOf(category).isEmpty();
            rows = result.rows().stream().map(row -> rowView(row, query, placement, leaf)).toList();
            total = result.total();
            truncated = result.truncated();
        }

        return new BrowsePage(query, admin, superAdmin, summary.total() == 0 && !superAdmin, query.textTooShort(),
                title(category), crumbs(category), subnav(category, counts, query), isSiblings(category),
                tiles(query, counts), supplierOptions(summary, query), stockOptions(query),
                withCatalog ? catalogOptions(query) : List.of(), chips(query), query.cleared().href(),
                rows, total, truncated, pagination, sortHeaders(query), query.href());
    }

    private Set<String> categoryIds(String category) {
        if (category == null) {
            return null;
        }
        Set<String> subtree = tree.selfAndDescendants(category);
        return subtree.isEmpty() ? Set.of(category) : subtree;
    }

    private static Predicate<BrowseRow> catalogFilter(BrowseQuery query, CatalogPlacement.StorePlacement placement) {
        if (placement == null) {
            return row -> true;
        }
        return switch (query.catalog()) {
            case ALL -> row -> true;
            case IN -> row -> !placement.existing(row.key()).isEmpty();
            case OUT -> row -> placement.existing(row.key()).isEmpty();
            case UNMATCHED -> row -> placement.targetsFor(row.categoryId()).isEmpty();
        };
    }

    private BrowsePage.RowView rowView(BrowseRow row, BrowseQuery query, CatalogPlacement.StorePlacement placement,
                                       boolean leafSelected) {
        CategoryLine category = CategoryLine.of(tree, placement, row.categoryId(), row.categoryText(), row.key(),
                leafSelected);
        String code = row.ean() != null ? row.ean() : row.mfn();
        String detailHref = BrowseQuery.PATH + "?q=" + encode(code) + "&from=" + encode(query.href());
        String addHref = query.href() + "&open=add&ean=" + encode(code);
        return new BrowsePage.RowView(row.name(), row.brand(), row.ean(), row.mfn(), detailHref, category,
                row.lowestDeliveredNet(), row.deliveryKnown(), row.lowestSupplier(), row.qty(), row.suppliers(), addHref);
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

    private List<BrowsePage.Crumb> crumbs(String category) {
        List<BrowsePage.Crumb> crumbs = new ArrayList<>();
        crumbs.add(new BrowsePage.Crumb(null, "inventory.browse.all", BrowseQuery.start().href()));
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
            crumbs.add(new BrowsePage.Crumb(name, null, id.equals(category) ? null : BrowseQuery.start().withCategory(id).href()));
        }
        return crumbs;
    }

    private boolean isSiblings(String category) {
        return category != null && tree.find(category).isPresent() && tree.childrenOf(category).isEmpty();
    }

    private List<BrowsePage.NavItem> subnav(String category, Map<String, Integer> counts, BrowseQuery query) {
        if (category == null || tree.find(category).isEmpty()) {
            return List.of();
        }
        List<PimCategory> items = tree.childrenOf(category).isEmpty() ? tree.siblingsOf(category) : tree.childrenOf(category);
        return items.stream()
                .filter(item -> counts.getOrDefault(item.id(), 0) > 0)
                .map(item -> new BrowsePage.NavItem(item.name(), null, counts.get(item.id()),
                        query.withCategory(item.id()).href(), item.id().equals(category)))
                .toList();
    }

    private List<BrowsePage.Tile> tiles(BrowseQuery query, Map<String, Integer> counts) {
        if (!query.isStart()) {
            return List.of();
        }
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

    private static List<BrowsePage.MenuOption> supplierOptions(BrowseSummary summary, BrowseQuery query) {
        return summary.bySupplier().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new BrowsePage.MenuOption(entry.getKey(), entry.getKey(), null, entry.getValue(),
                        query.suppliers().contains(entry.getKey()), null))
                .toList();
    }

    private static List<BrowsePage.MenuOption> stockOptions(BrowseQuery query) {
        List<BrowsePage.MenuOption> options = new ArrayList<>();
        for (BrowseCriteria.Stock stock : BrowseCriteria.Stock.values()) {
            options.add(new BrowsePage.MenuOption(stock.name(), null, "inventory.browse.stock." + stock.name(), 0,
                    query.stock() == stock, query.withStock(stock).href()));
        }
        return options;
    }

    private static List<BrowsePage.MenuOption> catalogOptions(BrowseQuery query) {
        List<BrowsePage.MenuOption> options = new ArrayList<>();
        for (BrowseQuery.CatalogFilter filter : BrowseQuery.CatalogFilter.values()) {
            options.add(new BrowsePage.MenuOption(filter.name(), null, "inventory.browse.catalog." + filter.name(), 0,
                    query.catalog() == filter, query.withCatalog(filter).href()));
        }
        return options;
    }

    private static List<BrowsePage.Chip> chips(BrowseQuery query) {
        List<BrowsePage.Chip> chips = new ArrayList<>();
        if (query.q2() != null) {
            chips.add(new BrowsePage.Chip("inventory.browse.chip.text", query.q2(), null, query.withoutText().href()));
        }
        query.suppliers().forEach(supplier -> chips.add(new BrowsePage.Chip("inventory.browse.chip.supplier", supplier,
                null, query.withoutSupplier(supplier).href())));
        if (query.stock() != BrowseCriteria.Stock.ALL) {
            chips.add(new BrowsePage.Chip("inventory.browse.chip.stock", null, "inventory.browse.stock." + query.stock().name(),
                    query.withStock(BrowseCriteria.Stock.ALL).href()));
        }
        if (query.catalog() != BrowseQuery.CatalogFilter.ALL) {
            chips.add(new BrowsePage.Chip("inventory.browse.chip.catalog", null, "inventory.browse.catalog." + query.catalog().name(),
                    query.withCatalog(BrowseQuery.CatalogFilter.ALL).href()));
        }
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
