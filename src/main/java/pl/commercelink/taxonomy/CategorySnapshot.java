package pl.commercelink.taxonomy;

/**
 * Derives the frozen per-item categorization snapshot — sort sequence and product/service type — from a
 * category string key, delegating to {@link CategoryCatalog}. Order/basket items persist these primitives
 * instead of the enum, so the read path reproduces the legacy sort order and Services branching without
 * naming the category enum. Unknown or missing keys degrade to the {@code Other} slot / {@code PRODUCT}.
 */
public final class CategorySnapshot {

    private CategorySnapshot() {
    }

    public static int sequenceOfKey(String categoryKey) {
        return CategoryCatalog.sequenceOf(categoryKey);
    }

    public static ItemType typeOfKey(String categoryKey) {
        return CategoryCatalog.itemTypeOf(categoryKey);
    }
}
