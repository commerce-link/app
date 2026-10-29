package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.SupplierScope;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLevelsMergeTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private ProductCatalog catalog;

    @InjectMocks
    private StockLevels stockLevels;

    @Test
    void copyWithoutAThresholdKeepsTheThresholdOfTheEarlierCopy() {
        // when
        StockProductLevel level = mergeCopiesWithThresholds(80, 0);

        // then
        assertThat(level.getRestockPricePromo()).isEqualTo(80);
        assertThat(level.getRestockPriceStandard()).isEqualTo(80);
    }

    @Test
    void laterCopyWithAThresholdReplacesTheMissingOne() {
        // when
        StockProductLevel level = mergeCopiesWithThresholds(0, 80);

        // then
        assertThat(level.getRestockPricePromo()).isEqualTo(80);
        assertThat(level.getRestockPriceStandard()).isEqualTo(80);
    }

    @Test
    void lowerOfTwoSetThresholdsWins() {
        // when
        StockProductLevel level = mergeCopiesWithThresholds(120, 80);

        // then
        assertThat(level.getRestockPricePromo()).isEqualTo(80);
        assertThat(level.getRestockPriceStandard()).isEqualTo(80);
    }

    @Test
    void copiesWithoutThresholdsStayWithoutOne() {
        // when
        StockProductLevel level = mergeCopiesWithThresholds(0, 0);

        // then
        assertThat(level.getRestockPricePromo()).isZero();
        assertThat(level.getRestockPriceStandard()).isZero();
    }

    private StockProductLevel mergeCopiesWithThresholds(int firstCopyThreshold, int secondCopyThreshold) {
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(category("cat-1", "Karty graficzne"), category("cat-2", "Promocje")));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-1")).thenReturn(List.of(copy("cat-1", firstCopyThreshold)));
        when(productRepository.findAll("cat-2")).thenReturn(List.of(copy("cat-2", secondCopyThreshold)));
        when(rollingPriceAggregateRepository.loadForStore(STORE_ID)).thenReturn(Map.of());

        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);
        assertThat(levels).hasSize(1);
        return levels.get(0);
    }

    private static CategoryDefinition category(String categoryId, String name) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId(categoryId);
        definition.setName(name);
        return definition;
    }

    private static Product copy(String categoryId, int threshold) {
        Product product = new Product(categoryId);
        product.setName("GPU");
        product.setManufacturerCode("MFN-1");
        product.setStockExpectedQty(1);
        product.setRestockPricePromo(threshold);
        product.setRestockPriceStandard(threshold);
        return product;
    }
}
