package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimEntryIndex;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductRecommendationEngineScopeTest {

    private static final String EAN = "5901234567890";
    private static final String MFN = "MFN-1";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryView inventory;

    @Mock
    private MatchedInventory matchedInventory;

    @InjectMocks
    private ProductRecommendationEngine engine;

    @Test
    void resolvesPimEntryFromTheLookupItWasGiven() {
        // given
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        InventoryKey key = new InventoryKey(EAN, MFN);
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(matchedInventory)));
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getInventoryKey()).thenReturn(key);
        when(matchedInventory.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(matchedInventory.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));

        PimEntry storeEntry = new PimEntry("store-pim-1",
                List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN), new PimIdentifier(MFN, PimIdentifierType.MPN)),
                "Intel", "Core i7", "CPU", null, true, null, null, null, "s1");
        PimEntryIndex index = new PimEntryIndex(List.of(storeEntry), UnaryOperator.identity(), UnaryOperator.identity());

        // when
        List<ProductRecommendation> ownerView = engine.getRecommendations(index.forStore("s1"), definition, inventory);
        List<ProductRecommendation> otherStoreView = engine.getRecommendations(index.forStore("s2"), definition, inventory);

        // then
        assertThat(ownerView).singleElement().extracting(ProductRecommendation::getPimId).isEqualTo("store-pim-1");
        assertThat(otherStoreView).singleElement().extracting(ProductRecommendation::getPimId).isNull();
    }

    @Test
    void storeEntryShadowingTheGlobalPimIdOfTheInventoryKeyWins() {
        // given: the inventory key was built from the global entry, the store has its own entry for the same EAN
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        PimEntry globalEntry = new PimEntry("global-pim-1", List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN)),
                "Intel", "Core i7", "CPU", null, true, null, null);
        PimEntry storeEntry = new PimEntry("store-pim-1", List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN)),
                "Intel", "Core i7 (store)", "CPU", null, true, null, null, null, "s1");
        PimEntryIndex index = new PimEntryIndex(List.of(globalEntry, storeEntry), UnaryOperator.identity(), UnaryOperator.identity());
        InventoryKey key = InventoryKey.fromPimEntry(globalEntry);
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(matchedInventory)));
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getInventoryKey()).thenReturn(key);
        when(matchedInventory.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(matchedInventory.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> ownerView = engine.getRecommendations(index.forStore("s1"), definition, inventory);
        List<ProductRecommendation> otherStoreView = engine.getRecommendations(index.forStore("s2"), definition, inventory);

        // then
        assertThat(ownerView).singleElement().extracting(ProductRecommendation::getPimId).isEqualTo("store-pim-1");
        assertThat(otherStoreView).singleElement().extracting(ProductRecommendation::getPimId).isEqualTo("global-pim-1");
    }

    @Test
    void twoInventoryGroupsResolvingToOneEntryAreRecommendedOnceAtTheLowerPrice() {
        // given: a store entry with the first group's EAN and, by bad feed data, a code of the second group
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        PimEntry storeEntry = new PimEntry("store-pim-1",
                List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN), new PimIdentifier("OWN-1", PimIdentifierType.MPN)),
                "Intel", "Core i7 (store)", "CPU", null, true, null, null, null, "s1");
        PimEntryIndex index = new PimEntryIndex(List.of(storeEntry), UnaryOperator.identity(), UnaryOperator.identity());
        MatchedInventory second = org.mockito.Mockito.mock(MatchedInventory.class);
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(matchedInventory, second)));
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getInventoryKey()).thenReturn(new InventoryKey(EAN, MFN));
        when(matchedInventory.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(matchedInventory.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));
        when(second.hasAnyOffers()).thenReturn(true);
        when(second.getInventoryKey()).thenReturn(new InventoryKey("5900000000999", "OWN-1"));
        when(second.getLowestPrice()).thenReturn(Price.fromNet(80.0));
        when(second.getTaxonomy()).thenReturn(new Taxonomy("5900000000999", "OWN-1", "Intel", "i7", "CPU", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> recommendations = engine.getRecommendations(index.forStore("s1"), definition, inventory);

        // then
        assertThat(recommendations).singleElement().satisfies(r -> {
            assertThat(r.getPimId()).isEqualTo("store-pim-1");
            assertThat(r.getLowestGrossPrice()).isEqualTo(Price.fromNet(80.0).grossValue());
        });
    }

    @Test
    void twoPriceGroupsOfOneGlobalEntryStayTwoRecommendationsAsBeforeStageTwo() {
        // given: InventoryAutoDiscovery split one global entry's offers into two price groups without a pim id;
        // both resolve to the entry through its two EANs
        CategoryDefinition definition = new CategoryDefinition().withGeneratedId();
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        PimEntry globalEntry = new PimEntry("global-pim-1",
                List.of(new PimIdentifier(EAN, PimIdentifierType.GTIN), new PimIdentifier("5900000000999", PimIdentifierType.GTIN)),
                "Intel", "Core i7", "CPU", null, true, null, null);
        PimEntryIndex index = new PimEntryIndex(List.of(globalEntry), UnaryOperator.identity(), UnaryOperator.identity());
        MatchedInventory cheaper = org.mockito.Mockito.mock(MatchedInventory.class);
        when(productRepository.findAll(definition.getCategoryId())).thenReturn(List.of());
        when(inventory.findAllByProductCategoryIds(List.of("989"))).thenReturn(Map.of("989", List.of(matchedInventory, cheaper)));
        when(matchedInventory.hasAnyOffers()).thenReturn(true);
        when(matchedInventory.getInventoryKey()).thenReturn(new InventoryKey(EAN, MFN));
        when(matchedInventory.getLowestPrice()).thenReturn(Price.fromNet(1000.0));
        when(matchedInventory.getTaxonomy()).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));
        when(cheaper.hasAnyOffers()).thenReturn(true);
        when(cheaper.getInventoryKey()).thenReturn(new InventoryKey("5900000000999", "MFN-2"));
        when(cheaper.getLowestPrice()).thenReturn(Price.fromNet(100.0));
        when(cheaper.getTaxonomy()).thenReturn(new Taxonomy("5900000000999", "MFN-2", "Intel", "i7", "CPU", 1, null, null, null, "989"));

        // when
        List<ProductRecommendation> global = engine.getRecommendations(index.global(), definition, inventory);
        List<ProductRecommendation> storeView = engine.getRecommendations(index.forStore("s1"), definition, inventory);

        // then
        assertThat(global).extracting(ProductRecommendation::getPimId).containsExactly("global-pim-1", "global-pim-1");
        assertThat(storeView).extracting(ProductRecommendation::getPimId).containsExactly("global-pim-1", "global-pim-1");
    }
}
