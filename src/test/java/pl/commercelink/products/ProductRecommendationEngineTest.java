package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @InjectMocks
    private ProductRecommendationEngine engine;

    @Test
    void queriesInventoryWithAllSelectedCategoryIds() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("194", "195")));
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("194", "195"))).thenReturn(Map.of());

        // when
        engine.getRecommendations(definition, inventory);

        // then
        verify(inventory).findAllByProductCategoryIds(List.of("194", "195"));
    }

    @Test
    void legacyNameAloneNoLongerQueriesInventory() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setCategory("Karty graficzne");

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(definition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }

    @Test
    void definitionWithoutCategoryMappingGetsNoRecommendationsAndNeverQueriesInventory() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition().withGeneratedId();

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(unmappedDefinition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }
}
