package pl.commercelink.offer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.products.CategoryDefinition;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoryTreeTest {

    @Test
    @DisplayName("builds path from catalog name and definition name")
    void buildsPathFromCatalogAndDefinitionName() {
        // given
        CategoryDefinition definition = definition("Procesory", "Gaming CPUs");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, "Elektronika");

        // then
        assertThat(tree.getPath()).isEqualTo("Elektronika/Gaming CPUs");
        assertThat(tree.getCategoryId()).isEqualTo("cat-1");
        assertThat(tree.getName()).isEqualTo("Gaming CPUs");
        assertThat(tree.getGroupingOrder()).isEqualTo(List.of("A", "B"));
        assertThat(tree.getMaxQty()).isEqualTo(3);
        assertThat(tree.getSequenceNumber()).isEqualTo(7);
        assertThat(tree.isRequiredDuringOrder()).isTrue();
    }

    @Test
    @DisplayName("falls back to definition name when catalog has no name")
    void fallsBackToDefinitionNameWhenCatalogHasNoName() {
        // given
        CategoryDefinition definition = definition("Procesory", "Gaming CPUs");

        // when
        ProductCategoryTree tree = new ProductCategoryTree(definition, null);

        // then
        assertThat(tree.getPath()).isEqualTo("Gaming CPUs");
    }

    @Test
    @DisplayName("serializes exactly the agreed JSON field set")
    void serializesExactlyTheAgreedJsonFieldSet() throws Exception {
        // given
        ProductCategoryTree tree = new ProductCategoryTree(definition("Procesory", "Gaming CPUs"), "Elektronika");

        // when
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(tree));

        // then
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "categoryId", "name", "path", "requiredDuringOrder", "sequenceNumber", "groupingOrder", "maxQty");
        assertThat(json.get("path").asText()).isEqualTo("Elektronika/Gaming CPUs");
    }

    private CategoryDefinition definition(String categoryKey, String name) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setName(name);
        definition.setCategory(categoryKey);
        definition.setGroupingOrder(List.of("A", "B"));
        definition.setMaxQty(3);
        definition.setSequenceNumber(7);
        definition.setRequiredDuringOrder(true);
        return definition;
    }
}
