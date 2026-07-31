package pl.commercelink.products;

import pl.commercelink.taxonomy.Taxonomy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public record CategorySelection(Set<String> categoryIds, Set<String> categoryNames) {

    public static final CategorySelection EMPTY = new CategorySelection(Set.of(), Set.of());

    public CategorySelection {
        categoryIds = Set.copyOf(categoryIds);
        categoryNames = Set.copyOf(categoryNames);
    }

    public static CategorySelection of(Collection<String> ids, Collection<String> names) {
        return new CategorySelection(sanitised(ids), sanitised(names));
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
        if (id != null && !id.isBlank()) {
            return categoryIds.contains(id);
        }
        return taxonomy.category() != null && categoryNames.contains(taxonomy.category());
    }

    public boolean isEmpty() {
        return categoryIds.isEmpty() && categoryNames.isEmpty();
    }
}
