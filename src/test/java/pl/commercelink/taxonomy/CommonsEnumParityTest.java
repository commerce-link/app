package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CommonsEnumParityTest {

    @Test
    void categoriesMatchTheCommonsEnumNamesInDeclarationOrder() {
        // when / then
        assertThat(ProductCategories.ALL)
                .containsExactlyElementsOf(Arrays.stream(ProductCategory.values()).map(Enum::name).toList());
    }

    @Test
    void groupMappingMatchesTheCommonsEnum() {
        // when / then
        for (ProductCategory category : ProductCategory.values()) {
            assertThat(ProductCategories.groupOf(category.name()))
                    .contains(category.getProductGroup().name());
        }
    }

    @Test
    void groupsMatchTheCommonsEnumNamesInDeclarationOrder() {
        // when / then
        assertThat(ProductGroups.ALL)
                .containsExactlyElementsOf(Arrays.stream(ProductGroup.values()).map(Enum::name).toList());
    }
}
