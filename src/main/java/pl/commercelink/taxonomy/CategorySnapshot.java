package pl.commercelink.taxonomy;

/**
 * Derives the frozen per-item categorization snapshot — sort sequence and product/service type — from a
 * {@link ProductCategory} or its string key. Order/basket items persist these primitives instead of the
 * enum, so the read path no longer depends on {@code ProductCategory} while reproducing the legacy sort
 * order and Services branching. Unknown or missing input degrades to the {@code Other} slot / {@code PRODUCT}.
 */
public final class CategorySnapshot {

    private CategorySnapshot() {
    }

    public static int sequenceOf(ProductCategory category) {
        return category != null ? category.ordinal() : ProductCategory.Other.ordinal();
    }

    public static int sequenceOfKey(String categoryKey) {
        return sequenceOf(categoryOrNull(categoryKey));
    }

    public static ItemType typeOf(ProductCategory category) {
        return category != null ? ItemType.of(category.getProductGroup()) : ItemType.PRODUCT;
    }

    public static ItemType typeOfKey(String categoryKey) {
        return typeOf(categoryOrNull(categoryKey));
    }

    private static ProductCategory categoryOrNull(String categoryKey) {
        if (categoryKey == null) {
            return null;
        }
        try {
            return ProductCategory.valueOf(categoryKey);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
