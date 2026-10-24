package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DiscountCategoryGroup(String catalogName, List<Choice> categories) {

    public record Choice(String name, List<String> alsoIn) {
    }

    public static List<DiscountCategoryGroup> of(List<ProductCatalog> catalogs) {
        Map<String, List<ProductCatalog>> catalogsOfCategory = new LinkedHashMap<>();
        catalogs.forEach(catalog -> namesIn(catalog).forEach(name ->
                catalogsOfCategory.computeIfAbsent(name, key -> new ArrayList<>()).add(catalog)));
        List<DiscountCategoryGroup> groups = new ArrayList<>();
        for (ProductCatalog catalog : catalogs) {
            List<Choice> choices = namesIn(catalog).stream()
                    .filter(name -> catalogsOfCategory.get(name).get(0) == catalog)
                    .map(name -> new Choice(name, catalogsOfCategory.get(name).stream()
                            .skip(1)
                            .map(ProductCatalog::getName)
                            .toList()))
                    .toList();
            if (!choices.isEmpty()) {
                groups.add(new DiscountCategoryGroup(catalog.getName(), choices));
            }
        }
        return groups;
    }

    private static List<String> namesIn(ProductCatalog catalog) {
        return catalog.getCategories().stream()
                .map(CategoryDefinition::getName)
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .toList();
    }
}
