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
import static org.mockito.Mockito.never;
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
    void serviceProductsAreSkippedInBothLegacyNamedDefinitions() {
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
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getName).containsExactly("Obudowa");
    }

    @Test
    void definitionWithoutANameSortsLastInsteadOfThrowing() {
        // given
        CategoryDefinition namelessDefinition = new CategoryDefinition();
        namelessDefinition.setCategoryId("cat-u");
        CategoryDefinition namedDefinition = new CategoryDefinition();
        namedDefinition.setCategoryId("cat-m");
        namedDefinition.setName("Procesory");

        Product namelessProduct = new Product("cat-u");
        namelessProduct.setName("Zasilacz");
        namelessProduct.setManufacturerCode("MFN-U");
        namelessProduct.setStockExpectedQty(1);
        Product namedProduct = new Product("cat-m");
        namedProduct.setName("CPU");
        namedProduct.setManufacturerCode("MFN-M");
        namedProduct.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(namelessDefinition, namedDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-u")).thenReturn(List.of(namelessProduct));
        when(productRepository.findAll("cat-m")).thenReturn(List.of(namedProduct));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getCategory).containsExactly("Procesory", null);
    }

    @Test
    void rowLabelIsTheDefinitionNameNotThePimMappingOrLegacyName() {
        // given
        CategoryDefinition mappedDefinition = new CategoryDefinition();
        mappedDefinition.setCategoryId("cat-m");
        mappedDefinition.setName("Podzespoły");
        mappedDefinition.setCategory("Legacy name");
        mappedDefinition.setPimCategoryIds(List.of("989", "170"));

        Product product = new Product("cat-m");
        product.setName("CPU");
        product.setManufacturerCode("MFN-M");
        product.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(mappedDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-m")).thenReturn(List.of(product));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getCategory).containsExactly("Podzespoły");
    }

    @Test
    void calculateCallsInventoryWithFulfilmentScope() {
        // given
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setName("Procesory");

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(definition));
        when(productRepository.findAll("cat-1")).thenReturn(List.of());
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);

        // when
        stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);

        // then
        verify(inventory).withEnabledSuppliersOnly(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }

    @Test
    void deliverySuggestionsSkipCategoriesWithoutTheFlag() {
        // given
        CategoryDefinition includedDefinition = new CategoryDefinition();
        includedDefinition.setCategoryId("cat-in");
        includedDefinition.setName("Procesory");
        includedDefinition.setIncludedInDeliverySuggestions(true);
        CategoryDefinition excludedDefinition = new CategoryDefinition();
        excludedDefinition.setCategoryId("cat-out");
        excludedDefinition.setName("Obudowy");

        Product includedProduct = new Product("cat-in");
        includedProduct.setName("CPU");
        includedProduct.setManufacturerCode("MFN-IN");
        includedProduct.setStockExpectedQty(1);
        Product excludedProduct = new Product("cat-out");
        excludedProduct.setName("Obudowa");
        excludedProduct.setManufacturerCode("MFN-OUT");
        excludedProduct.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(includedDefinition, excludedDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-in")).thenReturn(List.of(includedProduct));
        when(productRepository.findAll("cat-out")).thenReturn(List.of(excludedProduct));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, true);

        // then
        assertThat(levels).extracting(StockProductLevel::getName).containsExactly("CPU");
        verify(productRepository, never()).findAll("cat-out");
    }

    @Test
    void warehouseSearchIgnoresTheDeliverySuggestionsFlag() {
        // given
        CategoryDefinition excludedDefinition = new CategoryDefinition();
        excludedDefinition.setCategoryId("cat-out");
        excludedDefinition.setName("Obudowy");

        Product product = new Product("cat-out");
        product.setName("Obudowa");
        product.setManufacturerCode("MFN-OUT");
        product.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(excludedDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-out")).thenReturn(List.of(product));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getName).containsExactly("Obudowa");
    }
}
