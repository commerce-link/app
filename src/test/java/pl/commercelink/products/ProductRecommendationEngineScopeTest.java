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
}
