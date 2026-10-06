package pl.commercelink.web.inventory;

/**
 * The category block of a product found by code: the PIM path as in the browse list, plus the add action, offered to an
 * admin of a store on every product (into the catalog, or into another category when it is already there).
 */
public record ProductCategoryView(CategoryLine line, String ean, boolean canAdd, String addHref) {
}
