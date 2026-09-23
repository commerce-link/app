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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /**
     * A category already chosen in the multi picker — one chip. {@code path} names its ancestors, top level first, as
     * the chip shows them, or is {@code null} for a top level or a category the PIM no longer has.
     */
    public record SelectedCategory(String id, String name, String path) {
    }

    /**
     * One checkbox on the store settings page. {@code inCatalogue} is false for a name the store has saved but PIM no
     * longer offers — it still gets a checkbox, otherwise saving the form would drop it without saying so.
     */
    public record TopLevelChoice(String name, boolean selected, boolean inCatalogue) {
    }

    private static final Collator POLISH_COLLATOR = Collator.getInstance(Locale.forLanguageTag("pl-PL"));

    private static final String LANG = "pl";

    /** The separator of a breadcrumb, the same the picker script uses and the chip style appends after the path. */
    private static final String PATH_SEPARATOR = " \u203a ";

    private final PimCatalog pimCatalog;

    List<String> topLevelNames() {
        return categories().topLevels().stream()
                .map(PimCategory::name)
                .sorted(POLISH_COLLATOR)
                .toList();
    }

    /**
     * The top levels the store settings page ticks, in one pass over the catalogue: what PIM offers now, plus whatever
     * the store already has saved, so a category that left the catalogue keeps its box instead of being erased by the
     * next save. Same rule as {@link #categoryOptions(Collection, Collection)} applies to the catalog pickers.
     */
    public List<TopLevelChoice> topLevelChoices(Collection<String> selected) {
        List<String> offered = topLevelNames();
        List<String> names = new ArrayList<>(offered);
        List<String> kept = selected.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        kept.stream().filter(name -> !names.contains(name)).forEach(names::add);
        names.sort(POLISH_COLLATOR);
        return names.stream()
                .map(name -> new TopLevelChoice(name, kept.contains(name), offered.contains(name)))
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
     * The already selected ids, in the given order, with the path of each — the chips next to the multi picker.
     */
    public List<SelectedCategory> selectedOf(Collection<String> categoryIds) {
        Map<String, PimCategory> byId = byId(categories());
        return categoryIds.stream().map(id -> {
            CategoryOption option = optionOf(id, byId);
            return new SelectedCategory(option.id(), option.name(), pathOf(option.parentId(), byId));
        }).toList();
    }

    /** The names above a category, top level first, joined as the picker joins them; {@code null} when there are none. */
    private static String pathOf(String parentId, Map<String, PimCategory> byId) {
        List<String> names = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (parentId != null && byId.containsKey(parentId) && visited.add(parentId)) {
            PimCategory parent = byId.get(parentId);
            names.addFirst(parent.name());
            parentId = parent.parentId();
        }
        return names.isEmpty() ? null : String.join(PATH_SEPARATOR, names);
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
