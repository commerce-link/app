package pl.commercelink.web.inventory;

import java.util.List;

/**
 * "Kategoria katalogu" on "Uzupełnij dane" opened from the inventory: the manual catalog categories the chosen products
 * can go to, those matching their PIM category first.
 *
 * @param count             the chosen products the inventory still has
 * @param preselectedValue  the category chosen when the review opens, or null when none matches
 * @param pimCategoryName   the PIM category all the products share, or null
 */
public record CatalogTargetOptions(int count, List<Option> matching, List<Group> others, String preselectedValue,
                                   boolean noManualCategories, String pimCategoryName) {

    /** Nothing matches the products' PIM category: the field starts empty and says why. */
    public boolean unmatched() {
        return matching.isEmpty();
    }

    /** @param alreadyIn how many of the products the category holds already */
    public record Option(String value, String label, int alreadyIn) {
    }

    public record Group(String catalogName, List<Option> options) {
    }
}
