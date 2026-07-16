package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;

import static org.mockito.Mockito.verify;
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
    void queriesInventoryWithBridgedCategoryForIcecatLeafName() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setCategory("Karty graficzne");
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategory("GPU")).thenReturn(List.of());

        // when
        engine.getRecommendations(definition, inventory);

        // then
        verify(inventory).findAllByProductCategory("GPU");
    }

    @Test
    void queriesInventoryWithLegacyCategoryUnchanged() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setCategory("Case");
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategory("Case")).thenReturn(List.of());

        // when
        engine.getRecommendations(definition, inventory);

        // then
        verify(inventory).findAllByProductCategory("Case");
    }
}
