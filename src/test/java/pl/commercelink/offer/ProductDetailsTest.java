package pl.commercelink.offer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDetailsTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String CATEGORY_ID = "category-1";
    private static final String PIM_ID = "pim-1";

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryView inventory;

    @Mock
    private ProductCatalog catalog;

    @Mock
    private MatchedInventory matchedInventory;

    @InjectMocks
    private ProductDetails productDetails;

    @Test
    void managedProductExposesIdentityResolvableThroughCategoryId() {
        // given
        managedDefinition();
        when(productRepository.findByPimId(CATEGORY_ID, PIM_ID)).thenReturn(product());

        // when
        ProductDetailsView view = productDetails.generate(STORE_ID, CATALOG_ID, CATEGORY_ID, PIM_ID);

        // then
        assertThat(view.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(view.getPimId()).isEqualTo(PIM_ID);
        assertThat(view.getName()).isEqualTo("Gigabyte RTX 5070");
        assertThat(view.getBrand()).isEqualTo("Gigabyte");
    }

    @Test
    void dynamicDefinitionBuildsDetailsFromRecommendation() {
        // given
        dynamicDefinition("Karty graficzne");
        when(pimCatalog.findByPimId(PIM_ID)).thenReturn(Optional.of(pimEntry()));
        when(inventory.findByInventoryKey(any())).thenReturn(matchedInventory);
        when(matchedInventory.getTaxonomy()).thenReturn(taxonomy());
        when(matchedInventory.getLowestPrice()).thenReturn(new Price(1000, 1230));
        when(matchedInventory.getInventoryKey()).thenReturn(new InventoryKey(PIM_ID));

        // when
        ProductDetailsView view = productDetails.generate(STORE_ID, CATALOG_ID, CATEGORY_ID, PIM_ID);

        // then
        assertThat(view.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(view.getPimId()).isEqualTo(PIM_ID);
        assertThat(view.getName()).isEqualTo("Gigabyte RTX 5070");
    }

    private void managedDefinition() {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(CATEGORY_ID);
        definition.setType(CategoryDefinitionType.Managed);
        definition.setCategory("Karty graficzne");

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.findCategoryDefinition(CATEGORY_ID)).thenReturn(definition);
    }

    private void dynamicDefinition(String category) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(CATEGORY_ID);
        definition.setType(CategoryDefinitionType.Dynamic);
        definition.setCategory(category);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.findCategoryDefinition(CATEGORY_ID)).thenReturn(definition);
    }

    private Product product() {
        return new Product(CATEGORY_ID, PIM_ID, "5900000000001", "MFN-1", "Gigabyte", "RTX 5070",
                "Gigabyte RTX 5070", "default");
    }

    private PimEntry pimEntry() {
        return new PimEntry(PIM_ID, List.of(), "Gigabyte", "Gigabyte RTX 5070", "GPU", "RTX 5070",
                true, null, null);
    }

    private Taxonomy taxonomy() {
        return new Taxonomy("5900000000001", "MFN-1", "Gigabyte", "Gigabyte RTX 5070", "GPU", 1, null, null);
    }
}
