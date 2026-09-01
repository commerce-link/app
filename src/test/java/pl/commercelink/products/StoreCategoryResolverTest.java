package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCategoryResolverTest {

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private StoreCategoryResolver storeCategoryResolver;

    @Test
    void returnsMerchantCategoryNameForMappedPimCategoryId() {
        // given
        ProductCatalog catalog = catalog("Podzespoły komputerowe",
                definition("Obudowa", 1, "PIM-100"),
                definition("Procesor", 2, "PIM-200"));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        Optional<String> name = storeCategoryResolver.findCategoryName("store-1", "PIM-200");

        // then
        assertThat(name).contains("Procesor");
    }

    @Test
    void returnsEmptyWhenPimCategoryIdIsNotMapped() {
        // given
        ProductCatalog catalog = catalog("Podzespoły komputerowe", definition("Obudowa", 1, "PIM-100"));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        Optional<String> name = storeCategoryResolver.findCategoryName("store-1", "PIM-999");

        // then
        assertThat(name).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrMissingPimCategoryId() {
        // when / then
        assertThat(storeCategoryResolver.findCategoryName("store-1", null)).isEmpty();
        assertThat(storeCategoryResolver.findCategoryName("store-1", " ")).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrMissingStoreId() {
        // when / then
        assertThat(storeCategoryResolver.findCategoryName(null, "PIM-100")).isEmpty();
        assertThat(storeCategoryResolver.findCategoryName(" ", "PIM-100")).isEmpty();
    }

    @Test
    void resolvesCollisionInFavourOfLowerSequenceNumber() {
        // given
        ProductCatalog catalog = catalog("Podzespoły komputerowe",
                definition("Procesor", 2, "PIM-100"),
                definition("Obudowa", 1, "PIM-100"));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        Optional<String> name = storeCategoryResolver.findCategoryName("store-1", "PIM-100");

        // then
        assertThat(name).contains("Obudowa");
    }

    @Test
    void resolvesCollisionAcrossCatalogsInFavourOfFirstCatalogName() {
        // given
        ProductCatalog second = catalog("B", definition("Procesor", 1, "PIM-100"));
        ProductCatalog first = catalog("A", definition("Obudowa", 1, "PIM-100"));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(second, first));

        // when
        Optional<String> name = storeCategoryResolver.findCategoryName("store-1", "PIM-100");

        // then
        assertThat(name).contains("Obudowa");
    }

    @Test
    void skipsDefinitionsWithBlankName() {
        // given
        ProductCatalog catalog = catalog("Podzespoły komputerowe",
                definition(" ", 1, "PIM-100"),
                definition(null, 2, "PIM-100"),
                definition("Obudowa", 3, "PIM-100"));
        when(productCatalogRepository.findAll("store-1")).thenReturn(List.of(catalog));

        // when
        Optional<String> name = storeCategoryResolver.findCategoryName("store-1", "PIM-100");

        // then
        assertThat(name).contains("Obudowa");
    }

    private ProductCatalog catalog(String name, CategoryDefinition... definitions) {
        ProductCatalog catalog = new ProductCatalog("store-1", name);
        catalog.setCategories(Arrays.asList(definitions));
        return catalog;
    }

    private CategoryDefinition definition(String name, int sequenceNumber, String... pimCategoryIds) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setName(name);
        definition.setSequenceNumber(sequenceNumber);
        definition.setPimCategoryIds(Arrays.asList(pimCategoryIds));
        return definition;
    }
}
