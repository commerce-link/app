package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V010_MigrateCategoryDefinitionCategoriesTest {

    private static final Map<String, String> ID_BY_LEAF_NAME = Map.of(
            "Klawiatury", "194",
            "Myszki", "195",
            "Telewizory", "1584");

    private static AttributeValue definition(String category, List<String> categories) {
        Map<String, AttributeValue> map = new java.util.HashMap<>();
        map.put("categoryId", new AttributeValue().withS("def-1"));
        if (category != null) {
            map.put("category", new AttributeValue().withS(category));
        }
        if (categories != null) {
            map.put("categories", new AttributeValue().withL(
                    categories.stream().map(id -> new AttributeValue().withS(id)).toList()));
        }
        return new AttributeValue().withM(map);
    }

    private static AttributeValue list(AttributeValue... definitions) {
        return new AttributeValue().withL(List.of(definitions));
    }

    private static List<String> categoriesOf(AttributeValue definition) {
        AttributeValue categories = definition.getM().get("categories");
        return categories == null || categories.getL() == null
                ? null
                : categories.getL().stream().map(AttributeValue::getS).toList();
    }

    @Test
    void convertsLegacyNameToLeafId() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("194");
    }

    @Test
    void keepsLegacyNameAttributeIntact() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated.get(0).getM().get("category").getS()).isEqualTo("Klawiatury");
    }

    @Test
    void leavesCategoriesEmptyForNameOutsideTheLeafTree() {
        // given
        AttributeValue categories = list(definition("Services", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).isEmpty();
        assertThat(migrated.get(0).getM().get("category").getS()).isEqualTo("Services");
    }

    @Test
    void resolvesNameThatAlsoExistsAsInternalNodeToTheLeaf() {
        // given
        AttributeValue categories = list(definition("Telewizory", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("1584");
    }

    @Test
    void doesNotTouchDefinitionThatAlreadyHasCategories() {
        // given
        AttributeValue categories = list(definition("Klawiatury", List.of("999")));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("999");
    }

    @Test
    void leavesCategoriesEmptyWhenLegacyNameIsAbsent() {
        // given
        AttributeValue categories = list(definition(null, null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).isEmpty();
    }

    @Test
    void convertsEveryDefinitionInTheCatalog() {
        // given
        AttributeValue categories = list(
                definition("Klawiatury", null),
                definition("Myszki", null),
                definition("Services", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(categoriesOf(migrated.get(0))).containsExactly("194");
        assertThat(categoriesOf(migrated.get(1))).containsExactly("195");
        assertThat(categoriesOf(migrated.get(2))).isEmpty();
    }

    @Test
    void preservesUnrelatedDefinitionAttributes() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated.get(0).getM().get("categoryId").getS()).isEqualTo("def-1");
    }

    @Test
    void returnsEmptyListForMissingCategoriesAttribute() {
        // when
        List<AttributeValue> migrated = V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(null, ID_BY_LEAF_NAME);

        // then
        assertThat(migrated).isEmpty();
    }

    @Test
    void rejectsAnEmptyLeafMap() {
        // given
        AttributeValue categories = list(definition("Klawiatury", null));

        // when / then
        assertThatThrownBy(() -> V010_MigrateCategoryDefinitionCategories
                .migratedDefinitions(categories, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no leaf categories");
    }
}
