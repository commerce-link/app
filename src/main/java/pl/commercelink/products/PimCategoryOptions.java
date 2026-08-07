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

    /**
     * One row of the category picker.
     *
     * @param id       what the form submits — the PIM leaf id for the multi picker, the category name for the
     *                 name-based single picker
     * @param name     the leaf name shown as the row title
     * @param parentId the direct parent in the PIM category tree, or {@code null} when the category cannot be
     *                 resolved there; the browser walks it up to render the breadcrumb
     */
    public record CategoryOption(String id, String name, String parentId) {
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

    /**
     * The same set as {@link #categoryOptions(Collection, Collection)}, but carrying each category's parent so the
     * picker can draw a breadcrumb. The submitted value stays the name, which is what these filters match on.
     */
    public List<CategoryOption> namedOptions(Collection<String> topLevelNames, Collection<String> currentValues) {
        PimCategories categories = categories();
        Map<String, String> parentIdsByName = categories.all().stream()
                .filter(category -> category.name() != null)
                .collect(Collectors.toMap(PimCategory::name, category ->
                        category.parentId() == null ? "" : category.parentId(), (first, second) -> first));
        return categoryOptions(topLevelNames, currentValues).stream()
                .map(name -> new CategoryOption(name, name, emptyToNull(parentIdsByName.get(name))))
                .toList();
    }

    public List<CategoryOption> leafOptionsUnder(Collection<String> topLevelNames, Collection<String> currentIds) {
        PimCategories categories = categories();
        Map<String, CategoryOption> optionsById = new LinkedHashMap<>();
        categories.topLevels().stream()
                .filter(top -> topLevelNames.contains(top.name()))
                .flatMap(top -> categories.leavesUnder(top.id()).stream())
                .forEach(leaf -> optionsById.putIfAbsent(leaf.id(), option(leaf)));
        Map<String, PimCategory> byId = byId(categories);
        currentIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(id -> optionsById.putIfAbsent(id, optionOf(id, byId)));
        return optionsById.values().stream()
                .sorted(Comparator.comparing(CategoryOption::name, POLISH_COLLATOR))
                .toList();
    }

    /**
     * Options for already selected ids, in the given order — the chips next to the multi picker.
     */
    public List<CategoryOption> optionsOf(Collection<String> categoryIds) {
        Map<String, PimCategory> byId = byId(categories());
        return categoryIds.stream().map(id -> optionOf(id, byId)).toList();
    }

    /**
     * Branch nodes sitting on the paths of the given categories — everything the picker needs to render their
     * breadcrumbs, rather than the whole tree.
     */
    public List<CategoryOption> ancestorsOf(Collection<String> categoryIds) {
        Map<String, PimCategory> byId = byId(categories());
        Map<String, CategoryOption> onPaths = new LinkedHashMap<>();
        for (String categoryId : categoryIds) {
            PimCategory category = byId.get(categoryId);
            String parentId = category == null ? null : category.parentId();
            while (parentId != null && !onPaths.containsKey(parentId)) {
                PimCategory parent = byId.get(parentId);
                if (parent == null) {
                    break;
                }
                onPaths.put(parentId, option(parent));
                parentId = parent.parentId();
            }
        }
        return List.copyOf(onPaths.values());
    }

    /**
     * Ancestors for categories identified by name — the name-based picker's counterpart of
     * {@link #ancestorsOf(Collection)}.
     */
    public List<CategoryOption> ancestorsOfNames(Collection<String> categoryNames) {
        Map<String, String> idsByName = categories().all().stream()
                .filter(category -> category.name() != null)
                .collect(Collectors.toMap(PimCategory::name, PimCategory::id, (first, second) -> first));
        return ancestorsOf(categoryNames.stream().map(idsByName::get).filter(id -> id != null).toList());
    }

    private static CategoryOption option(PimCategory category) {
        return new CategoryOption(category.id(), category.name(), category.parentId());
    }

    private static CategoryOption optionOf(String id, Map<String, PimCategory> byId) {
        PimCategory category = byId.get(id);
        return category == null ? new CategoryOption(id, id, null) : option(category);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private Map<String, PimCategory> byId(PimCategories categories) {
        return categories.all().stream()
                .collect(Collectors.toMap(PimCategory::id, category -> category, (first, second) -> first));
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
