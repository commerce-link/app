package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCategoriesTest {

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private StoreCategories storeCategories;

    @Test
    void namesForReturnsDefinitionNamesOrderedByCatalogNameAndSequenceNumber() {
        // given
        ProductCatalog furniture = catalog("Meble biurowe", definition("Biurko", 2), definition("Krzesło", 1));
        ProductCatalog computers = catalog("Podzespoły komputerowe", definition("Obudowa", 1), definition("Procesor", 2));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(computers, furniture));

        // when
        List<String> names = storeCategories.namesFor("store-1");

        // then
        assertThat(names).containsExactly("Krzesło", "Biurko", "Obudowa", "Procesor", "Other");
    }

    @Test
    void namesForSkipsBlankNamesAndDuplicates() {
        // given
        ProductCatalog first = catalog("A", definition("Obudowa", 1), definition(null, 2), definition(" ", 3));
        ProductCatalog second = catalog("B", definition("Obudowa", 1), definition("Procesor", 2));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(first, second));

        // when
        List<String> names = storeCategories.namesFor("store-1");

        // then
        assertThat(names).containsExactly("Obudowa", "Procesor", "Other");
    }

    @Test
    void groupsForReturnsCatalogsWithTheirDefinitionNamesInCatalogAndSequenceOrder() {
        // given
        ProductCatalog furniture = catalog("Meble biurowe", definition("Biurko", 2), definition("Krzesło", 1));
        ProductCatalog computers = catalog("Podzespoły komputerowe", definition("Obudowa", 1), definition(null, 2));
        ProductCatalog empty = catalog("Pusty", definition(" ", 1));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(computers, furniture, empty));

        // when
        List<StoreCategories.Group> groups = storeCategories.groupsFor("store-1");

        // then
        assertThat(groups).extracting(StoreCategories.Group::catalog)
                .containsExactly("Meble biurowe", "Podzespoły komputerowe", "Other");
        assertThat(groups.get(0).names()).containsExactly("Krzesło", "Biurko");
        assertThat(groups.get(1).names()).containsExactly("Obudowa");
        assertThat(groups.get(2).names()).containsExactly("Other");
    }

    @Test
    void groupsForSkipsSyntheticOtherGroupWhenCatalogAlreadyDefinesIt() {
        // given
        ProductCatalog catalog = catalog("Meble biurowe", definition("Krzesło", 1), definition("Other", 2));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        List<StoreCategories.Group> groups = storeCategories.groupsFor("store-1");

        // then
        assertThat(groups).extracting(StoreCategories.Group::catalog).containsExactly("Meble biurowe");
        assertThat(groups.get(0).names()).containsExactly("Krzesło", "Other");
    }

    private ProductCatalog catalog(String name, CategoryDefinition... definitions) {
        ProductCatalog catalog = new ProductCatalog("store-1", name);
        catalog.setCategories(Arrays.asList(definitions));
        return catalog;
    }

    private CategoryDefinition definition(String name, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }
}
