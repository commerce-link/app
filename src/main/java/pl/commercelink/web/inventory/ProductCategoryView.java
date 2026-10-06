package pl.commercelink.web.inventory;

/** The category block of a product found by code: the same two lines as the browse list, plus the add action. */
public record ProductCategoryView(CategoryLine line, String ean, boolean canAdd, String addHref) {
}
