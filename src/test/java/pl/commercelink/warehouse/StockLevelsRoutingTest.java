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
import pl.commercelink.products.PimCategoryOptions;
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
    private PimCategoryOptions pimCategoryOptions;
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
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getName).containsExactly("Obudowa");
    }

    @Test
    void definitionWithoutMappingAndWithoutLegacyNameSortsLastInsteadOfThrowing() {
        // given
        CategoryDefinition unmappedDefinition = new CategoryDefinition();
        unmappedDefinition.setCategoryId("cat-u");
        unmappedDefinition.setName("Nowa kategoria");
        CategoryDefinition mappedDefinition = new CategoryDefinition();
        mappedDefinition.setCategoryId("cat-m");
        mappedDefinition.setName("Procesory");
        mappedDefinition.setPimCategoryIds(List.of("989"));

        Product unmappedProduct = new Product("cat-u");
        unmappedProduct.setName("Zasilacz");
        unmappedProduct.setManufacturerCode("MFN-U");
        unmappedProduct.setStockExpectedQty(1);
        Product mappedProduct = new Product("cat-m");
        mappedProduct.setName("CPU");
        mappedProduct.setManufacturerCode("MFN-M");
        mappedProduct.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(unmappedDefinition, mappedDefinition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-u")).thenReturn(List.of(unmappedProduct));
        when(productRepository.findAll("cat-m")).thenReturn(List.of(mappedProduct));
        when(rollingPriceAggregateRepository.loadAll()).thenReturn(Map.of());
        when(pimCategoryOptions.namesOf(List.of("989"))).thenReturn(List.of("Procesory"));

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getCategory).containsExactly("Procesory", null);
    }

    @Test
    void mappedDefinitionLabelJoinsPimCategoryNames() {
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
        when(pimCategoryOptions.namesOf(List.of("989", "170"))).thenReturn(List.of("Procesory", "Karty graficzne"));

        // when
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false);

        // then
        assertThat(levels).extracting(StockProductLevel::getCategory).containsExactly("Procesory, Karty graficzne");
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
