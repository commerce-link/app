package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockLevelsRoutingTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private Warehouse warehouse;
    @Mock
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;
    @Mock
    private ProductRecommendationEngine recommendationEngine;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private ProductCatalog catalog;

    @InjectMocks
    private StockLevels stockLevels;

    @Test
    void serviceProductsAreSkippedEvenThoughBothDefinitionsAreMapped() {
        // given
        CategoryDefinition mixedDefinition = new CategoryDefinition();
        mixedDefinition.setCategoryId("cat-s");
        mixedDefinition.setName("Montaż");
        mixedDefinition.setCategory("Assembly");
        CategoryDefinition regularDefinition = new CategoryDefinition();
        regularDefinition.setCategoryId("cat-r");
        regularDefinition.setName("Obudowy");
        regularDefinition.setCategory("Case");

        Product serviceProduct = new Product("cat-s");
        serviceProduct.setName("Montaż PC");
        serviceProduct.setService(true);
        serviceProduct.setStockExpectedQty(1);
        Product regularProduct = new Product("cat-r");
        regularProduct.setName("Obudowa");
        regularProduct.setManufacturerCode("MFN-1");
        regularProduct.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(mixedDefinition, regularDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-s")).thenReturn(List.of(serviceProduct));
        when(productRepository.findAll("cat-r")).thenReturn(List.of(regularProduct));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getName).containsExactly("Obudowa");
    }

    @Test
    void calculateCallsInventoryWithFulfilmentScope() {
        // given
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of());
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);

        // when
        stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false);

        // then
        verify(inventory).withEnabledSuppliersOnly(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }
}
