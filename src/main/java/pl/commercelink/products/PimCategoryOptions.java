package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategories;
import pl.commercelink.pim.api.PimCategory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PimCategoryOptions {

    private static final Collator POLISH_COLLATOR = Collator.getInstance(Locale.forLanguageTag("pl-PL"));

    private static final String LANG = "pl";

    private final PimCatalog pimCatalog;

    public List<String> topLevelNames() {
        return categories().topLevels().stream()
                .map(PimCategory::name)
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public List<String> leafNamesUnder(Collection<String> topLevelNames) {
        PimCategories categories = categories();
        return categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.name()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .map(PimCategory::name)
                .distinct()
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public List<String> categoryOptions(Collection<String> topLevelNames, Collection<String> currentValues) {
        List<String> options = new ArrayList<>(leafNamesUnder(topLevelNames));
        currentValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !options.contains(value))
                .distinct()
                .forEach(options::add);
        options.sort(POLISH_COLLATOR);
        return List.copyOf(options);
    }

    public List<CategoryOption> leafOptionsUnder(Collection<String> topLevelNames) {
        PimCategories categories = categories();
        Map<String, CategoryOption> byId = new LinkedHashMap<>();
        categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.name()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .forEach(leaf -> byId.putIfAbsent(leaf.id(), new CategoryOption(leaf.id(), leaf.name())));
        return sortedByName(byId.values());
    }

    public List<CategoryOption> categoryOptionsById(Collection<String> topLevelNames, Collection<String> currentIds) {
        Map<String, CategoryOption> byIdMap = new LinkedHashMap<>();
        leafOptionsUnder(topLevelNames).forEach(option -> byIdMap.putIfAbsent(option.id(), option));
        Map<String, String> namesById = namesById();
        if (currentIds != null) {
            currentIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .filter(id -> !byIdMap.containsKey(id))
                    .filter(namesById::containsKey)
                    .forEach(id -> byIdMap.put(id, new CategoryOption(id, namesById.get(id))));
        }
        return sortedByName(byIdMap.values());
    }

    public List<String> namesOf(Collection<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> namesById = namesById();
        return categoryIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .map(namesById::get)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    public String joinedNamesOf(Collection<String> categoryIds) {
        return String.join(", ", namesOf(categoryIds));
    }

    public CategorySelection selectionOf(Collection<String> categoryIds) {
        return CategorySelection.of(categoryIds, namesOf(categoryIds));
    }

    private List<CategoryOption> sortedByName(Collection<CategoryOption> options) {
        List<CategoryOption> sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparing(CategoryOption::name, POLISH_COLLATOR));
        return List.copyOf(sorted);
    }

    private Map<String, String> namesById() {
        Map<String, String> namesById = new LinkedHashMap<>();
        categories().all().stream()
                .filter(category -> category.id() != null && category.name() != null)
                .forEach(category -> namesById.putIfAbsent(category.id(), category.name()));
        return namesById;
    }

    private PimCategories categories() {
        return new PimCategories(pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()))
                .toList());
    }
}
