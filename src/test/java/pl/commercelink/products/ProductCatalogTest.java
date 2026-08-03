package pl.commercelink.products;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
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
    void legacyCategorySurvivesASaveThatOnlySendsTheNewIdList() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Katalog");
        CategoryDefinition existing = new CategoryDefinition().withGeneratedId().withName("Procesory");
        existing.setCategory("Procesory");
        catalog.addOrUpdateCategoryDefinition(existing);
        CategoryDefinition posted = new CategoryDefinition().withName("Procesory");
        posted.setCategoryId(existing.getCategoryId());
        posted.setPimCategoryIds(new LinkedList<>(List.of("989")));

        // when
        catalog.addOrUpdateCategoryDefinition(posted);

        // then
        assertThat(catalog.findCategoryDefinition(existing.getCategoryId()).getCategory()).isEqualTo("Procesory");
        assertThat(catalog.findCategoryDefinition(existing.getCategoryId()).getPimCategoryIds()).containsExactly("989");
    }

    @Test
    void existingMappingIsImmutableButAMissingOneCanBeAdded() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Katalog");
        CategoryDefinition mapped = new CategoryDefinition().withGeneratedId().withName("Procesory");
        mapped.setPimCategoryIds(new LinkedList<>(List.of("989")));
        catalog.addOrUpdateCategoryDefinition(mapped);
        CategoryDefinition remapAttempt = new CategoryDefinition().withName("Procesory");
        remapAttempt.setCategoryId(mapped.getCategoryId());
        remapAttempt.setPimCategoryIds(new LinkedList<>(List.of("170")));

        CategoryDefinition unmapped = new CategoryDefinition().withGeneratedId().withName("Klawiatury");
        catalog.addOrUpdateCategoryDefinition(unmapped);
        CategoryDefinition firstMapping = new CategoryDefinition().withName("Klawiatury");
        firstMapping.setCategoryId(unmapped.getCategoryId());
        firstMapping.setPimCategoryIds(new LinkedList<>(List.of("194", "195")));

        // when
        catalog.addOrUpdateCategoryDefinition(remapAttempt);
        catalog.addOrUpdateCategoryDefinition(firstMapping);

        // then
        assertThat(catalog.findCategoryDefinition(mapped.getCategoryId()).getPimCategoryIds()).containsExactly("989");
        assertThat(catalog.findCategoryDefinition(unmapped.getCategoryId()).getPimCategoryIds()).containsExactly("194", "195");
    }

    @Test
    void pimCategoryIdsAreTrimmedAndDeduplicatedOnSave() {
        // given
        ProductCatalog catalog = new ProductCatalog("store-1", "Katalog");
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId().withName("Peryferia");
        definition.setPimCategoryIds(new LinkedList<>(List.of(" 194 ", "194", "", "195")));

        // when
        catalog.addOrUpdateCategoryDefinition(definition);

        // then
        assertThat(catalog.findCategoryDefinition(definition.getCategoryId()).getPimCategoryIds())
                .containsExactly("194", "195");
    }

    private CategoryDefinition definition(String name, String category, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setCategory(category);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }
}
