package pl.commercelink.web.catalog;

import pl.commercelink.products.CategoryDefinitionType;

/** UI names of the two category types: Managed = "Ręczna" (products are added by hand), Dynamic = "Automatyczna" (computed from inventory). */
public final class CategoryTypeLabels {

    private CategoryTypeLabels() {
    }

    public static String labelKey(CategoryDefinitionType type) {
        return "catalog.category.type." + type.name();
    }

    public static String descriptionKey(CategoryDefinitionType type) {
        return labelKey(type) + ".desc";
    }

    public static String tone(CategoryDefinitionType type) {
        return type == CategoryDefinitionType.Dynamic ? "is-info" : "is-neutral";
    }
}
