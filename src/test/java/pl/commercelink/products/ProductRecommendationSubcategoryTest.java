package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization of the T5 rename label -> subcategory on the VIEW model
 * {@link ProductRecommendation}. FREEZES that the recommendation's subcategory is sourced from
 * PimEntry.subcategory() when present and is carried into the built Product via toProduct().
 */
@ExtendWith(MockitoExtension.class)
class ProductRecommendationSubcategoryTest {

    @Mock
    private CategoryDefinition categoryDefinition;

    @Mock
    private MatchedInventory matchedInventory;

    /** FROZEN: subcategory is read from PimEntry.subcategory() and flows into Product.getSubcategory(). */
    @Test
    void subcategoryIsSourcedFromPimEntryAndCarriedIntoProduct() {
        // given
        Taxonomy taxonomy = new Taxonomy("ean-1", "mfn-1", "Brand", "Name", ProductCategory.Laptops, 0);
        PimEntry pimEntry = new PimEntry("pim-1", List.of(), "Brand", "Name", "Laptops", "Gaming Laptops", true, null, null);
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy);
        when(matchedInventory.getLowestPrice()).thenReturn(Price.fromGross(100.0));
        when(categoryDefinition.getPriceDefinitions()).thenReturn(List.of());

        // when
        ProductRecommendation recommendation =
                new ProductRecommendation(categoryDefinition, matchedInventory, Optional.of(pimEntry));

        // then
        assertEquals("Gaming Laptops", recommendation.getSubcategory());
        assertEquals("Gaming Laptops", recommendation.toProduct().getSubcategory());
    }
}
