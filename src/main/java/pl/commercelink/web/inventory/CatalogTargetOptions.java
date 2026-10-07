package pl.commercelink.web.inventory;

import java.util.List;
import java.util.stream.Stream;

/**
 * "Kategoria katalogu" on "Uzupełnij dane" opened from the inventory: the manual catalog categories the chosen products
 * can go to, those matching their PIM category first.
 *
 * @param count             the chosen products the inventory still has
 * @param matching          the categories mapped to the products' PIM category, in the catalogs' order
 * @param others            the other manual categories, by catalog name and then category name
 * @param preselectedValue  the category chosen when the review opens, or null when none matches
 * @param pimCategoryName   the PIM category all the products share, or null
 */
public record CatalogTargetOptions(int count, List<Option> matching, List<Option> others, String preselectedValue,
                                   boolean noManualCategories, String pimCategoryName) {

    /** Nothing matches the products' PIM category: the field starts empty and says why. */
    public boolean unmatched() {
        return matching.isEmpty();
    }

    /** Every manual category is in one catalog, so naming it under each option would only repeat it. */
    public boolean oneCatalog() {
        return Stream.concat(matching.stream(), others.stream()).map(Option::catalogId).distinct().count() == 1;
    }

    /** @param alreadyIn how many of the products the category holds already (it picks the preselected one) */
    public record Option(String catalogId, String categoryId, String catalogName, String categoryName, int alreadyIn) {

        public String value() {
            return catalogId + "/" + categoryId;
        }
    }
}
