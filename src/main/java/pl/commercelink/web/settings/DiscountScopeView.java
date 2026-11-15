package pl.commercelink.web.settings;

import pl.commercelink.discounts.DiscountScope;
import pl.commercelink.discounts.TargetValue;

import java.util.ArrayList;
import java.util.List;

/** The lists of a scope that narrow it, in the order they are shown; none means every product. */
public record DiscountScopeView(List<Part> parts) {

    public static final String CATEGORIES = "categories";
    public static final String BRANDS = "brands";
    public static final String PRODUCTS = "products";

    private static final int SHOWN_VALUES = 3;

    /** kind: the list's name, also the end of its label key; shownNames: the first few names, more: how many follow. */
    public record Part(String kind, List<TargetValue> values, List<String> shownNames, int more) {
    }

    public static DiscountScopeView of(DiscountScope scope) {
        List<Part> parts = new ArrayList<>();
        add(parts, CATEGORIES, named(scope.getCategories()));
        add(parts, BRANDS, named(scope.getBrands()));
        add(parts, PRODUCTS, scope.getProducts());
        return new DiscountScopeView(List.copyOf(parts));
    }

    public boolean everything() {
        return parts.isEmpty();
    }

    private static void add(List<Part> parts, String kind, List<TargetValue> values) {
        if (values.isEmpty()) {
            return;
        }
        List<String> names = values.stream().map(TargetValue::getName).toList();
        parts.add(new Part(kind, List.copyOf(values), names.stream().limit(SHOWN_VALUES).toList(),
                Math.max(0, names.size() - SHOWN_VALUES)));
    }

    private static List<TargetValue> named(List<String> names) {
        return names.stream().map(name -> new TargetValue(name, name)).toList();
    }
}
