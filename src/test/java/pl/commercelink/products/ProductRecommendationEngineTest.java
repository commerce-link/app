package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationEngineTest {

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryView inventory;

    @Mock
    private PimCategoryOptions pimCategoryOptions;

    @InjectMocks
    private ProductRecommendationEngine engine;

    @Test
    void queriesInventoryWithTheResolvedSelection() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId().withCategories("1234");
        CategorySelection selection = CategorySelection.of(List.of("1234"), List.of("Karty graficzne"));
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(pimCategoryOptions.selectionOf(List.of("1234"))).thenReturn(selection);
        when(inventory.findAllByProductCategories(selection)).thenReturn(List.of());

        // when
        engine.getRecommendations(definition, inventory);

        // then
        verify(inventory).findAllByProductCategories(selection);
    }

    @Test
    void queriesInventoryOnceForSeveralCategories() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId().withCategories("1234", "5678");
        CategorySelection selection = CategorySelection.of(
                List.of("1234", "5678"), List.of("Karty graficzne", "Procesory"));
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(pimCategoryOptions.selectionOf(List.of("1234", "5678"))).thenReturn(selection);
        when(inventory.findAllByProductCategories(selection)).thenReturn(List.of());

        // when
        engine.getRecommendations(definition, inventory);

        // then
        verify(inventory, times(1)).findAllByProductCategories(selection);
    }

    @Test
    void definitionWithoutCategoriesGetsNoRecommendationsAndNeverQueriesInventory() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition().withGeneratedId();

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(unmappedDefinition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }

    @Test
    void definitionWhoseSelectionResolvesToNothingNeverQueriesInventory() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId().withCategories("gone");
        when(pimCategoryOptions.selectionOf(List.of("gone"))).thenReturn(CategorySelection.EMPTY);

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(definition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }
}
