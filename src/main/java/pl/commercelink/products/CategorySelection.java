package pl.commercelink.products;

import pl.commercelink.taxonomy.Taxonomy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public record CategorySelection(Set<String> categoryIds) {

    public static final CategorySelection EMPTY = new CategorySelection(Set.of());

    public CategorySelection {
        categoryIds = Set.copyOf(categoryIds);
    }

    public static CategorySelection of(Collection<String> ids) {
        return new CategorySelection(sanitised(ids));
    }

    private static Set<String> sanitised(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    public boolean matches(Taxonomy taxonomy) {
        if (taxonomy == null) {
            return false;
        }
        String id = taxonomy.categoryId();
        return id != null && !id.isBlank() && categoryIds.contains(id);
    }

    public boolean isEmpty() {
        return categoryIds.isEmpty();
    }
}
