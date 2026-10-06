package pl.commercelink.web.inventory;

import org.springframework.lang.Nullable;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.web.catalog.CatalogPaths;

import java.util.List;

/**
 * The "Kategoria" cell: the PIM path (ancestors grey, leaf bold) and, for an admin, the store-catalog category the
 * product fits. An id the PIM tree does not know shows the taxonomy's category text instead of an empty cell.
 */
public record CategoryLine(List<String> pimAncestors, String pimLeaf, String pimFullPath, String catalogLabel,
                           int catalogMore, boolean catalogUnmatched, String inCatalogHref) {

    private static final String SEPARATOR = " \u203a ";

    public boolean inCatalog() {
        return inCatalogHref != null;
    }

    public static CategoryLine of(PimCategoryTree tree, @Nullable CatalogPlacement.StorePlacement placement,
                                  String categoryId, String categoryText, InventoryKey key, boolean leafOnly) {
        List<String> path = tree.pathNames(categoryId);
        List<String> ancestors = path.isEmpty() || leafOnly ? List.of() : path.subList(0, path.size() - 1);
        String leaf = path.isEmpty() ? categoryText : path.get(path.size() - 1);
        String fullPath = path.isEmpty() ? categoryText : String.join(SEPARATOR, path);
        if (placement == null) {
            return new CategoryLine(List.copyOf(ancestors), leaf, fullPath, null, 0, false, null);
        }
        List<CatalogPlacement.Target> targets = placement.targetsFor(categoryId);
        String inCatalogHref = placement.existing(key).stream().findFirst()
                .map(existing -> CatalogPaths.product(existing.catalogId(), existing.categoryId(), existing.productId()))
                .orElse(null);
        return new CategoryLine(List.copyOf(ancestors), leaf, fullPath,
                targets.isEmpty() ? null : targets.get(0).label(), Math.max(0, targets.size() - 1), targets.isEmpty(),
                inCatalogHref);
    }
}
