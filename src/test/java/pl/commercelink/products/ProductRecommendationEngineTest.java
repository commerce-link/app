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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
        List<ProductRecommendation> recommendations = engine.getRecommendationsNotInCategory(pim, definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getEan).isEqualTo("5901000000043");
    }

    @Test
    void recommendationsKeepWhatTheCategoryStoresInTheProductsTable() {
        // given: a product left in the table with the group's EAN, as after a manual category turned automatic
        CategoryDefinition definition = mappedTo("989");
        lenient().when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of(
                new Product(definition.getCategoryId(), null, "5901000000043", "SHOE-1", "Nike", "l", "Nike 43", "Default")));
        offers(new InventoryKey("5901000000043", "SHOE-1"));
        priced("5901000000043", "SHOE-1", "Nike 43");

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(pim, definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getEan).isEqualTo("5901000000043");
        verifyNoInteractions(productRepository);
    }

    @Test
    void recommendationsNotInCategorySkipAProductWithTheSameEan() {
        // given
        CategoryDefinition definition = mappedTo("989");
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of(
                new Product(definition.getCategoryId(), null, "5901000000043", "OTHER-CODE", "Nike", "l", "Nike 43", "Default")));
        offers(new InventoryKey("5901000000043", "SHOE-1"));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendationsNotInCategory(pim, definition, inventory);

        // then
        assertThat(recommendations).isEmpty();
    }

    @Test
    void recommendationsNotInCategorySkipAProductWithTheSamePimId() {
        // given: the operator changed the product's EAN, its PIM entry is still the group's
        CategoryDefinition definition = mappedTo("989");
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of(
                new Product(definition.getCategoryId(), "pim-43", "5901000000999", "SHOE-1", "Nike", "l", "Nike 43", "Default")));
        InventoryKey key = InventoryKey.fromPimIdAndEan("pim-43", "5901000000043");
        offers(key);

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendationsNotInCategory(pim, definition, inventory);

        // then
        assertThat(recommendations).isEmpty();
    }

    @Test
    void recommendationsNotInCategoryOfACategoryWithoutMappingReadNothing() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition().withGeneratedId();

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendationsNotInCategory(pim, unmappedDefinition, inventory);

        // then
        assertThat(recommendations).isEmpty();
        verifyNoInteractions(productRepository, inventory);
    }

    /** The contract the conversion to a manual category stands on: only rows with a PIM entry and a listed label. */
    @Test
    void mappedRecommendationsNeedAPimEntryAndAListedLabel() {
        // given
        CategoryDefinition definition = mappedTo("170");
        definition.setGroupingOrder(new LinkedList<>(List.of("RTX 5070")));
        PimEntry listed = new PimEntry("pim-5070", List.of(new PimIdentifier("5901000000070", PimIdentifierType.GTIN)),
                "MSI", "MSI RTX 5070", "GPU", "RTX 5070", true, null, null);
        PimEntry offTheList = new PimEntry("pim-5060", List.of(new PimIdentifier("5901000000060", PimIdentifierType.GTIN)),
                "MSI", "MSI RTX 5060", "GPU", "RTX 5060", true, null, null);
        PimEntryIndex index = new PimEntryIndex(List.of(listed, offTheList), UnaryOperator.identity(), UnaryOperator.identity());
        List<MatchedInventory> groups = List.of(
                groupOf("5901000000070", "MSI RTX 5070"),
                groupOf("5901000000060", "MSI RTX 5060"),
                groupOf("5901000000050", "MSI RTX 5050"));
        when(inventory.findAllByProductCategoryIds(List.of("170"))).thenReturn(Map.of("170", groups));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendationsForMappedProducts(index.global(), definition, inventory);

        // then
        assertThat(recommendations).singleElement().extracting(ProductRecommendation::getPimId).isEqualTo("pim-5070");
    }

    private static CategoryDefinition mappedTo(String pimCategoryId) {
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of(pimCategoryId)));
        return definition;
    }

    /** The category's PIM category holds one group with offers, under {@code key}. */
    private void offers(InventoryKey key) {
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(group)));
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(key);
    }

    /** What the engine reads of a group it recommends. */
    private void priced(String ean, String code, String name) {
        when(group.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(group.getTaxonomy()).thenReturn(new Taxonomy(ean, code, "Nike", name, "Shoes", 1, null, null, null, "989"));
    }

    /** One of several groups a test needs, so built here rather than declared as a field. */
    private static MatchedInventory groupOf(String ean, String name) {
        MatchedInventory group = mock(MatchedInventory.class);
        when(group.hasAnyOffers()).thenReturn(true);
        when(group.getInventoryKey()).thenReturn(InventoryKey.fromEan(ean));
        when(group.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(group.getTaxonomy()).thenReturn(new Taxonomy(ean, null, "MSI", name, "GPU", 1, null, null, null, "170"));
        return group;
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
