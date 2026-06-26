package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.localization.EnumLocalizer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of {@link CategoryCatalog}, the SOLE app-side seam onto the {@code commons}
 * {@link ProductCategory}/{@link ProductGroup} enums. The enum is used here ONLY as the test oracle:
 * every facade method must reproduce, for a string key, exactly what the enum produced for the
 * corresponding constant today. Unknown/blank keys degrade exactly as the legacy {@code CategorySnapshot}
 * path did (sequence -> Other slot, type -> PRODUCT, group -> none).
 */
class CategoryCatalogTest {

    @Test
    void isKnownIsTrueForEveryEnumConstantNameAndFalseOtherwise() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(CategoryCatalog.isKnown(category.name()))
                    .as("known key " + category.name())
                    .isTrue();
        }
        assertThat(CategoryCatalog.isKnown("NotARealCategory")).isFalse();
        assertThat(CategoryCatalog.isKnown("cpu")).as("exact-case, not folded").isFalse();
        assertThat(CategoryCatalog.isKnown(null)).isFalse();
        assertThat(CategoryCatalog.isKnown("")).isFalse();
    }

    @Test
    void sequenceOfMatchesEnumOrdinalForEveryKey() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(CategoryCatalog.sequenceOf(category.name()))
                    .as("sequence of " + category.name())
                    .isEqualTo(category.ordinal());
        }
    }

    @Test
    void sequenceOfDegradesUnknownAndBlankToTheOtherSlot() {
        // given
        int otherSlot = ProductCategory.Other.ordinal();

        // when / then
        assertThat(CategoryCatalog.sequenceOf("NotARealCategory")).isEqualTo(otherSlot);
        assertThat(CategoryCatalog.sequenceOf(null)).isEqualTo(otherSlot);
        assertThat(CategoryCatalog.sequenceOf("")).isEqualTo(otherSlot);
    }

    @Test
    void groupKeyOfMatchesEnumProductGroupNameForEveryKey() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(CategoryCatalog.groupKeyOf(category.name()))
                    .as("group key of " + category.name())
                    .isEqualTo(category.getProductGroup().name());
        }
    }

    @Test
    void groupKeyOfIsNullForUnknownAndBlank() {
        // given / when / then
        assertThat(CategoryCatalog.groupKeyOf("NotARealCategory")).isNull();
        assertThat(CategoryCatalog.groupKeyOf(null)).isNull();
        assertThat(CategoryCatalog.groupKeyOf("")).isNull();
    }

    @Test
    void itemTypeOfMatchesLegacyServiceBranchingForEveryKey() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            ItemType expected = ItemType.of(category.getProductGroup().name());
            assertThat(CategoryCatalog.itemTypeOf(category.name()))
                    .as("item type of " + category.name())
                    .isEqualTo(expected);
        }
        assertThat(CategoryCatalog.itemTypeOf("Services")).isEqualTo(ItemType.SERVICE);
    }

    @Test
    void itemTypeOfDegradesUnknownAndBlankToProduct() {
        // given / when / then
        assertThat(CategoryCatalog.itemTypeOf("NotARealCategory")).isEqualTo(ItemType.PRODUCT);
        assertThat(CategoryCatalog.itemTypeOf(null)).isEqualTo(ItemType.PRODUCT);
        assertThat(CategoryCatalog.itemTypeOf("")).isEqualTo(ItemType.PRODUCT);
    }

    @Test
    void allKeysIsEveryEnumConstantName() {
        // given
        Set<String> expected = Stream.of(ProductCategory.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        // when / then
        assertThat(CategoryCatalog.allKeys()).isEqualTo(expected);
    }

    @Test
    void orderedKeysIsEveryEnumConstantNameInOrdinalOrder() {
        // given
        List<String> expected = Stream.of(ProductCategory.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        // when / then
        assertThat(CategoryCatalog.orderedKeys()).containsExactlyElementsOf(expected);
    }

    @Test
    void defaultKeyIsTheOtherConstantName() {
        // given / when / then
        assertThat(CategoryCatalog.defaultKey()).isEqualTo(ProductCategory.Other.name());
    }

    @Test
    void knownOrDefaultKeepsKnownKeysAndFallsBackToTheDefaultOtherwise() {
        // given / when / then
        assertThat(CategoryCatalog.knownOrDefault("GPU")).isEqualTo("GPU");
        assertThat(CategoryCatalog.knownOrDefault("NotARealCategory")).isEqualTo(ProductCategory.Other.name());
        assertThat(CategoryCatalog.knownOrDefault(null)).isEqualTo(ProductCategory.Other.name());
        assertThat(CategoryCatalog.knownOrDefault("")).isEqualTo(ProductCategory.Other.name());
    }

    @Test
    void legacyCategoryOfResolvesKnownKeysAndDegradesUnknownToOther() {
        // given / when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(CategoryCatalog.legacyCategoryOf(category.name()))
                    .as("legacy enum of " + category.name())
                    .isSameAs(category);
        }
        assertThat(CategoryCatalog.legacyCategoryOf("NotARealCategory")).isSameAs(ProductCategory.Other);
        assertThat(CategoryCatalog.legacyCategoryOf(null)).isSameAs(ProductCategory.Other);
        assertThat(CategoryCatalog.legacyCategoryOf("")).isSameAs(ProductCategory.Other);
    }

    @Test
    void legacyCategoryOrKeepResolvesKnownKeyOtherwiseKeepsFallback() {
        // given / when / then
        assertThat(CategoryCatalog.legacyCategoryOrKeep("GPU", ProductCategory.Other)).isSameAs(ProductCategory.GPU);
        assertThat(CategoryCatalog.legacyCategoryOrKeep("NotARealCategory", ProductCategory.CPU)).isSameAs(ProductCategory.CPU);
        assertThat(CategoryCatalog.legacyCategoryOrKeep(null, ProductCategory.Memory)).isSameAs(ProductCategory.Memory);
    }

    @Test
    void orderedGroupKeysAreEveryProductGroupNameInDeclarationOrder() {
        // given
        List<String> expected = Stream.of(ProductGroup.values()).map(Enum::name).collect(Collectors.toList());

        // when / then
        assertThat(CategoryCatalog.orderedGroupKeys()).containsExactlyElementsOf(expected);
    }

    @Test
    void keysInGroupsAreTheCategoriesOfThoseGroupsInDeclarationOrder() {
        // given
        List<String> expected = ProductCategory.values(List.of(ProductGroup.Peripherals, ProductGroup.Furniture))
                .stream().map(Enum::name).collect(Collectors.toList());

        // when / then
        assertThat(CategoryCatalog.keysInGroups(List.of("Peripherals", "Furniture")))
                .containsExactlyElementsOf(expected);
        assertThat(CategoryCatalog.keysInGroups(List.of())).isEmpty();
    }

    @Test
    void displayNameOfGroupLocalizesTheResolvedProductGroup() {
        // given
        EnumLocalizer localizer = new RecordingLocalizer();

        // when / then
        assertThat(CategoryCatalog.displayNameOfGroup(localizer, "Services")).isEqualTo("ProductGroup:Services");
    }

    @Test
    void groupDisplayNameOfCategoryLocalizesTheCategorysProductGroup() {
        // given
        EnumLocalizer localizer = new RecordingLocalizer();

        // when / then
        assertThat(CategoryCatalog.groupDisplayNameOfCategory(localizer, "CPU")).isEqualTo("ProductGroup:PcComponents");
    }

    @Test
    void displayNameOfCategoryLocalizesTheCategoryWithSuffix() {
        // given
        EnumLocalizer localizer = new RecordingLocalizer();

        // when / then
        assertThat(CategoryCatalog.displayNameOfCategory(localizer, "CPU", "plural")).isEqualTo("ProductCategory:CPU:plural");
    }

    /** Minimal EnumLocalizer that echoes the enum it was handed, so we can assert the facade resolves the right one. */
    private static final class RecordingLocalizer implements EnumLocalizer {
        @Override public String localize(Enum<?> value) {
            return value.getClass().getSimpleName() + ":" + value.name();
        }
        @Override public String localize(Enum<?> value, String suffix) {
            return localize(value) + ":" + suffix;
        }
        @Override public String localize(Enum<?> value, Locale locale) {
            return localize(value);
        }
        @Override public String localize(Enum<?> value, String suffix, Locale locale) {
            return localize(value, suffix);
        }
    }
}
