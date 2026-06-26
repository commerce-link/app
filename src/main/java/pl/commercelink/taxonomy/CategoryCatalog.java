package pl.commercelink.taxonomy;

import pl.commercelink.starter.localization.EnumLocalizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The single app-side facade onto the {@code commons} {@link ProductCategory}/{@link ProductGroup}
 * enums. Every other class in {@code app} addresses categories by their string key (the stable
 * {@code enum.name()}); this class is the ONLY place that resolves a key back to enum-derived data —
 * sort sequence, product group, product/service type, vocabulary and localized display.
 *
 * <p>Keys are exact-case {@code enum.name()} values. Unknown or blank keys degrade exactly as the
 * legacy read path did: sequence to the {@code Other} slot, type to {@code PRODUCT}, group to none.
 * Swapping the backing source (e.g. to the {@code /PIM/Categories} dictionary) is a later micro-step
 * that touches only this file.
 */
public final class CategoryCatalog {

    private CategoryCatalog() {
    }

    public static boolean isKnown(String key) {
        return categoryOrNull(key) != null;
    }

    public static String defaultKey() {
        return ProductCategory.Other.name();
    }

    public static String knownOrDefault(String key) {
        return isKnown(key) ? key : defaultKey();
    }

    public static int sequenceOf(String key) {
        ProductCategory category = categoryOrNull(key);
        return category != null ? category.ordinal() : ProductCategory.Other.ordinal();
    }

    public static String groupKeyOf(String key) {
        ProductCategory category = categoryOrNull(key);
        return category != null ? category.getProductGroup().name() : null;
    }

    public static ItemType itemTypeOf(String key) {
        return ItemType.of(groupKeyOf(key));
    }

    public static Set<String> allKeys() {
        return Stream.of(ProductCategory.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<String> orderedKeys() {
        return Stream.of(ProductCategory.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    public static List<String> orderedGroupKeys() {
        return Stream.of(ProductGroup.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    public static List<String> keysInGroups(Collection<String> groupKeys) {
        Set<String> wanted = new HashSet<>(groupKeys);
        return Stream.of(ProductCategory.values())
                .filter(category -> wanted.contains(category.getProductGroup().name()))
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    public static String displayNameOfGroup(EnumLocalizer localizer, String groupKey) {
        return localizer.localize(ProductGroup.valueOf(groupKey));
    }

    public static String groupDisplayNameOfCategory(EnumLocalizer localizer, String categoryKey) {
        return localizer.localize(ProductCategory.valueOf(categoryKey).getProductGroup());
    }

    public static String displayNameOfCategory(EnumLocalizer localizer, String categoryKey, String suffix) {
        return localizer.localize(ProductCategory.valueOf(categoryKey), suffix);
    }

    /**
     * Bridge for the not-yet-decoupled supplier-api {@code Taxonomy.category} record component (enum,
     * removed in Phase C). Returns the constant for a known key, the {@code Other} constant otherwise.
     * Used ONLY to feed that legacy component so no other app file has to name the enum.
     */
    public static ProductCategory legacyCategoryOf(String key) {
        ProductCategory category = categoryOrNull(key);
        return category != null ? category : ProductCategory.Other;
    }

    /**
     * Bridge variant for {@code Taxonomy.category}: resolves a known key, otherwise keeps the supplied
     * legacy constant (used when a correction overlays only the key and the legacy enum must follow it
     * when resolvable, else stay as it was).
     */
    public static ProductCategory legacyCategoryOrKeep(String key, ProductCategory fallback) {
        ProductCategory category = categoryOrNull(key);
        return category != null ? category : fallback;
    }

    private static ProductCategory categoryOrNull(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            return ProductCategory.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
