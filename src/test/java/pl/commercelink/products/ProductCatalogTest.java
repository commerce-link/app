package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogTest {

    @Test
    void categorySequenceNumbersAreKeyedByDefinitionName() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Podzespoły komputerowe");
        catalog.setCategories(List.of(
                definition("Obudowa", "Case", 1),
                definition("Procesor", "CPU", 2)));

        // when
        Map<String, Integer> sequenceNumbers = catalog.getCategorySequenceNumbers();

        // then
        assertThat(sequenceNumbers).containsExactlyInAnyOrderEntriesOf(Map.of("Obudowa", 1, "Procesor", 2));
    }

    @Test
    void categorySequenceNumbersSkipDefinitionsWithoutNameAndKeepLowestNumberForDuplicates() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Podzespoły komputerowe");
        catalog.setCategories(List.of(
                definition(null, "Case", 1),
                definition("Pamięć", "Memory", 2),
                definition("Pamięć", "Storage", 3)));

        // when
        Map<String, Integer> sequenceNumbers = catalog.getCategorySequenceNumbers();

        // then
        assertThat(sequenceNumbers).containsExactlyInAnyOrderEntriesOf(Map.of("Pamięć", 2));
    }

    @Test
    void addOrUpdateTrimsBlankAndDuplicateCategories() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Catalog");
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setCategories(java.util.Arrays.asList(" 1234 ", "", null, "1234", "5678", "  "));

        // when
        catalog.addOrUpdateCategoryDefinition(definition);

        // then
        assertThat(catalog.getCategories().get(0).getCategories()).containsExactly("1234", "5678");
    }

    @Test
    void addOrUpdateKeepsCategoriesEmptyWhenNoneSelected() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Catalog");
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();

        // when
        catalog.addOrUpdateCategoryDefinition(definition);

        // then
        assertThat(catalog.getCategories().get(0).getCategories()).isEmpty();
    }

    @Test
    void addOrUpdateKeepsTheLegacyCategoryOfAnExistingDefinitionWhenOnlyCategoriesArePosted() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Catalog");
        CategoryDefinition stored = new CategoryDefinition().withGeneratedId();
        stored.setCategory("Klawiatury");
        stored.setCategories(List.of("194"));
        catalog.addOrUpdateCategoryDefinition(stored);

        CategoryDefinition posted = new CategoryDefinition();
        posted.setCategoryId(stored.getCategoryId());
        posted.setCategories(List.of("195"));

        // when
        catalog.addOrUpdateCategoryDefinition(posted);

        // then
        CategoryDefinition saved = catalog.findCategoryDefinition(stored.getCategoryId());
        assertThat(saved.getCategory()).isEqualTo("Klawiatury");
        assertThat(saved.getCategories()).containsExactly("195");
    }

    private CategoryDefinition definition(String name, String category, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setCategory(category);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }
}
