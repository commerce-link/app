package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationEngineTest {

    @Mock
    private PimView pim;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryView inventory;

    @Mock
    private MatchedInventory group;

    @InjectMocks
    private ProductRecommendationEngine engine;

    @Test
    void recommendationKeepsAGroupSharingOnlyTheCode() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of(
                new Product(definition.getCategoryId(), null, "5901000000042", "SHOE-1", "Nike", "l", "Nike 42", "Default")));
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(group)));
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(new InventoryKey("5901000000043", "SHOE-1"));
        when(group.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(group.getTaxonomy()).thenReturn(new Taxonomy("5901000000043", "SHOE-1", "Nike", "Nike 43", "Shoes", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(pim, definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getEan).isEqualTo("5901000000043");
    }

    @Test
    void recommendationTakesNoEntryByCode() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        PimEntry entryOfAnotherSize = new PimEntry("pim-42",
                List.of(new PimIdentifier("5901000000042", PimIdentifierType.GTIN), new PimIdentifier("SHOE-1", PimIdentifierType.MPN)),
                "Nike", "Nike 42", "Shoes", null, true, null, null);
        PimEntryIndex index = new PimEntryIndex(List.of(entryOfAnotherSize), UnaryOperator.identity(), UnaryOperator.identity());
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(group)));
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(new InventoryKey("5901000000043", "SHOE-1"));
        when(group.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(group.getTaxonomy()).thenReturn(new Taxonomy("5901000000043", "SHOE-1", "Nike", "Nike 43", "Shoes", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(index.global(), definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getPimId).isNull();
    }

    @Test
    void recommendationTakesAnEntryFoundByGtin() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        PimEntry entry = new PimEntry("pim-43",
                List.of(new PimIdentifier("5901000000043", PimIdentifierType.GTIN), new PimIdentifier("SHOE-1", PimIdentifierType.MPN)),
                "Nike", "Nike 43", "Shoes", null, true, null, null);
        PimEntryIndex index = new PimEntryIndex(List.of(entry), UnaryOperator.identity(), UnaryOperator.identity());
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(group)));
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(new InventoryKey("5901000000043", "SHOE-1"));
        when(group.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(group.getTaxonomy()).thenReturn(new Taxonomy("5901000000043", "SHOE-1", "Nike", "Nike 43", "Shoes", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(index.global(), definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getPimId).isEqualTo("pim-43");
    }

    @Test
    void queriesInventoryWithAllSelectedCategoryIds() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("194", "195")));
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("194", "195"))).thenReturn(Map.of());

        // when
        engine.getRecommendations(pim, definition, inventory);

        // then
        verify(inventory).findAllByProductCategoryIds(List.of("194", "195"));
    }

    @Test
    void legacyNameAloneNoLongerQueriesInventory() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setCategory("Karty graficzne");

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(pim, definition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }

    @Test
    void definitionWithoutCategoryMappingGetsNoRecommendationsAndNeverQueriesInventory() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition().withGeneratedId();

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(pim, unmappedDefinition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(inventory);
    }
}
