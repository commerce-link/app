package pl.commercelink.products;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record CategoryNames(Map<String, String> namesById) {

    public CategoryNames {
        namesById = Map.copyOf(namesById);
    }

    public List<String> namesOf(Collection<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return categoryIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .map(namesById::get)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(PimCategoryOptions.POLISH_COLLATOR)
                .toList();
    }

    public String joinedNamesOf(Collection<String> categoryIds) {
        return String.join(", ", namesOf(categoryIds));
    }

    public CategorySelection selectionOf(Collection<String> categoryIds) {
        return CategorySelection.of(categoryIds);
    }
}
