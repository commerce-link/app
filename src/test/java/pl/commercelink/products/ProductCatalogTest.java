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

    private CategoryDefinition definition(String name, String category, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setCategory(category);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }
}
