package pl.commercelink.web.inventory;

/**
 * The category block of a product found by code: the same two lines as the browse list, plus the add action, offered
 * when the product is in no catalog yet or is missing from one of the catalog categories it fits.
 */
public record ProductCategoryView(CategoryLine line, String ean, boolean canAdd, String addHref) {
}
