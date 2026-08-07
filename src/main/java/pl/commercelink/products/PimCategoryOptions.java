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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PimCategoryOptions {

    public record CategoryOption(String id, String name) {
    }

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

    public List<CategoryOption> leafOptionsUnder(Collection<String> topLevelNames, Collection<String> currentIds) {
        PimCategories categories = categories();
        Map<String, CategoryOption> optionsById = new LinkedHashMap<>();
        categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.name()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .forEach(leaf -> optionsById.putIfAbsent(leaf.id(), new CategoryOption(leaf.id(), leaf.name())));
        Map<String, String> namesById = namesById(categories);
        currentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(id -> optionsById.putIfAbsent(id, new CategoryOption(id, namesById.getOrDefault(id, id))));
        return optionsById.values().stream()
                .sorted(Comparator.comparing(CategoryOption::name, POLISH_COLLATOR))
                .toList();
    }

    public String nameOf(String categoryId) {
        return namesOf(List.of(categoryId)).getFirst();
    }

    public List<String> namesOf(Collection<String> categoryIds) {
        Map<String, String> namesById = namesById(categories());
        return categoryIds.stream()
                .map(id -> namesById.getOrDefault(id, id))
                .toList();
    }

    private Map<String, String> namesById(PimCategories categories) {
        return categories.all().stream()
                .filter(category -> category.name() != null)
                .collect(Collectors.toMap(PimCategory::id, PimCategory::name, (first, second) -> first));
    }

    private PimCategories categories() {
        return new PimCategories(pimCatalog.allCategories().stream()
                .filter(category -> LANG.equals(category.lang()))
                .toList());
    }
}
