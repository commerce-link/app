package pl.commercelink.web.inventory;

import org.springframework.lang.Nullable;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The "Kategoria w PIM" cell: the PIM path (ancestors grey, leaf bold); an id the PIM tree does not know shows the
 * taxonomy's category text instead of an empty cell. For an admin it also carries where the product already sits in the
 * store's catalog, for the check icon of the "W katalogu" column.
 */
public record CategoryLine(List<String> pimAncestors, String pimLeaf, String pimFullPath, List<String> inCatalogLabels) {

    private static final String SEPARATOR = " \u203a ";

    public boolean inCatalog() {
        return !inCatalogLabels.isEmpty();
    }

    /** One place per line, for the tooltip of the check icon ({@code .cl-tooltip.is-lines}). */
    public String inCatalogPlaces() {
        return String.join("\n", inCatalogLabels);
    }

    public static CategoryLine of(PimCategoryTree tree, @Nullable CatalogPlacement.StorePlacement placement,
                                  String categoryId, String categoryText, InventoryKey key, boolean leafOnly) {
        List<String> path = tree.pathNames(categoryId);
        List<String> ancestors = path.isEmpty() || leafOnly ? List.of() : path.subList(0, path.size() - 1);
        String leaf = path.isEmpty() ? categoryText : path.get(path.size() - 1);
        String fullPath = path.isEmpty() ? categoryText : String.join(SEPARATOR, path);
        if (placement == null) {
            return new CategoryLine(List.copyOf(ancestors), leaf, fullPath, List.of());
        }
        List<CatalogPlacement.Existing> existing = placement.existing(key);
        // One place per catalog category: two same-named categories in different catalogs still count as two.
        List<String> inCatalogLabels = existing.stream()
                .filter(distinctBy(entry -> entry.catalogId() + "/" + entry.categoryId()))
                .map(entry -> label(placement, entry))
                .toList();
        return new CategoryLine(List.copyOf(ancestors), leaf, fullPath, inCatalogLabels);
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
