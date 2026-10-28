package pl.commercelink.warehouse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pricelist.RollingPriceAggregate;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.SupplierScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLevelsTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String PIM_ID = "pim-1";
    private static final double CURRENT_LOWEST_NET = 81.63;
    private static final double MEDIAN_LOWEST_NET = 100.0;

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

    @BeforeEach
    void catalogWithOneProductAtItsLowestPrice() {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setName("Karty graficzne");

        Product product = new Product("cat-1");
        product.setName("GPU");
        product.setManufacturerCode("MFN-1");
        product.setPimId(PIM_ID);
        product.setStockExpectedQty(1);

        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(definition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-1")).thenReturn(List.of(product));
        when(rollingPriceAggregateRepository.loadForStore(STORE_ID))
                .thenReturn(Map.of(PIM_ID, aggregateAtLowestPrice()));
    }

    @Test
    void offerAtTheCurrentLowestNetPriceFitsTheLowestPriceThreshold() {
        // when
        StockProductLevel level = calculateTheOnlyLevel();

        // then
        assertThat(level.isWithinBudget(CURRENT_LOWEST_NET, RestockPriceCategory.LowestPrice)).isTrue();
    }

    @Test
    void offerAGroszAboveTheCurrentLowestNetPriceMissesTheLowestPriceThreshold() {
        // when
        StockProductLevel level = calculateTheOnlyLevel();

        // then
        assertThat(level.isWithinBudget(CURRENT_LOWEST_NET + 0.01, RestockPriceCategory.LowestPrice)).isFalse();
    }

    @Test
    void offerAtTheCurrentLowestNetPriceFitsTheHotDealThreshold() {
        // when
        StockProductLevel level = calculateTheOnlyLevel();

        // then
        assertThat(level.isWithinBudget(CURRENT_LOWEST_NET, RestockPriceCategory.HotDeal)).isTrue();
    }

    @Test
    void offerAGroszAboveTheCurrentLowestNetPriceMissesTheHotDealThreshold() {
        // when
        StockProductLevel level = calculateTheOnlyLevel();

        // then
        assertThat(level.isWithinBudget(CURRENT_LOWEST_NET + 0.01, RestockPriceCategory.HotDeal)).isFalse();
    }

    @Test
    void goodDealThresholdIsTheNetMedianOfTheLowestPrices() {
        // when
        StockProductLevel level = calculateTheOnlyLevel();

        // then
        assertThat(level.isWithinBudget(MEDIAN_LOWEST_NET, RestockPriceCategory.GoodDeal)).isTrue();
        assertThat(level.isWithinBudget(MEDIAN_LOWEST_NET + 0.01, RestockPriceCategory.GoodDeal)).isFalse();
    }

    private StockProductLevel calculateTheOnlyLevel() {
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.WholeCatalog, false, false);
        assertThat(levels).hasSize(1);
        return levels.get(0);
    }

    private static RollingPriceAggregate aggregateAtLowestPrice() {
        return new RollingPriceAggregate(PIM_ID, MEDIAN_LOWEST_NET, 110.0, 120.0, CURRENT_LOWEST_NET, 100.0,
                CURRENT_LOWEST_NET, 0.0, 0.0, 0, 0, "", "", 30, 0.0, 0.0, 10,
                LocalDate.of(2026, 10, 27), LocalDate.of(2026, 9, 28));
    }
}
