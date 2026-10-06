package pl.commercelink.web.inventory;

import org.springframework.lang.Nullable;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.web.catalog.CatalogPaths;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The "Kategoria" cell: the PIM path (ancestors grey, leaf bold) and, for an admin, the store-catalog categories the
 * product fits and the ones it already sits in. An id the PIM tree does not know shows the taxonomy's category text
 * instead of an empty cell.
 */
public record CategoryLine(List<String> pimAncestors, String pimLeaf, String pimFullPath, String catalogLabel,
                           int catalogMore, boolean catalogUnmatched, String inCatalogHref, List<String> catalogLabels,
                           List<String> inCatalogLabels, boolean addableElsewhere) {

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
            return new CategoryLine(List.copyOf(ancestors), leaf, fullPath, null, 0, false, null, List.of(), List.of(), false);
        }
        List<CatalogPlacement.Target> targets = placement.targetsFor(categoryId);
        List<CatalogPlacement.Existing> existing = placement.existing(key);
        String inCatalogHref = existing.stream().findFirst()
                .map(entry -> CatalogPaths.product(entry.catalogId(), entry.categoryId(), entry.productId()))
                .orElse(null);
        // One place per catalog category: two same-named categories in different catalogs still count as two.
        List<String> inCatalogLabels = existing.stream()
                .filter(distinctBy(entry -> entry.catalogId() + "/" + entry.categoryId()))
                .map(entry -> label(placement, entry))
                .toList();
        boolean addableElsewhere = !existing.isEmpty()
                && targets.stream().anyMatch(target -> !placement.isIn(target.categoryId(), key));
        return new CategoryLine(List.copyOf(ancestors), leaf, fullPath,
                targets.isEmpty() ? null : targets.get(0).label(), Math.max(0, targets.size() - 1), targets.isEmpty(),
                inCatalogHref, targets.stream().map(CatalogPlacement.Target::label).toList(), inCatalogLabels,
                addableElsewhere);
    }

    private static String label(CatalogPlacement.StorePlacement placement, CatalogPlacement.Existing entry) {
        return placement.targets().stream()
                .filter(target -> target.catalogId().equals(entry.catalogId()) && target.categoryId().equals(entry.categoryId()))
                .findFirst()
                .map(CatalogPlacement.Target::label)
                // Defensive: existing entries are loaded from the targets themselves, so this is not expected to happen.
                .orElse(entry.catalogId() + SEPARATOR + entry.categoryId());
    }

    private static Predicate<CatalogPlacement.Existing> distinctBy(Function<CatalogPlacement.Existing, String> key) {
        Set<String> seen = new HashSet<>();
        return entry -> seen.add(key.apply(entry));
    }
}
