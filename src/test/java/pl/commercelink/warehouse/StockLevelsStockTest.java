package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockLevelsStockTest {

    private static final String STORE_ID = "store-1";
    private static final String CATALOG_ID = "catalog-1";
    private static final String EAN = "5901000000011";
    private static final String SECOND_EAN = "5901000000035";
    private static final String OTHER_PRODUCT_EAN = "5901000000028";

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
    @Mock
    private Warehouse warehouse;
    @Mock
    private StockQueryService stockQueryService;

    @InjectMocks
    private StockLevels stockLevels;

    @Test
    void stockOfAnotherProductWithTheSameShortCodeDoesNotCount() {
        // given
        catalogWith(new Product("cat-1", null, EAN, "X1", "Asus", "l", "Asus X1", "Default"));
        when(inventoryView.findByInventoryKey(any())).thenAnswer(call -> new MatchedInventory(call.getArgument(0), null, null));
        warehouseHolds(item(EAN, 1), item(OTHER_PRODUCT_EAN, 2));

        // when
        StockProductLevel level = theOnlyLevel();

        // then
        assertThat(level.getCurrentQuantity()).isEqualTo(1);
    }

    @Test
    void stockUnderTheSecondEanOfTheProductCounts() {
        // given
        catalogWith(new Product("cat-1", "pim-1", EAN, "X1", "Asus", "l", "Asus X1", "Default"));
        InventoryKey groupKey = InventoryKey.fromPimIdAndEan("pim-1", EAN);
        groupKey.addEan(SECOND_EAN);
        when(inventoryView.findByInventoryKey(any())).thenReturn(new MatchedInventory(groupKey, null, null));
        warehouseHolds(item(EAN, 1), item(SECOND_EAN, 2));

        // when
        StockProductLevel level = theOnlyLevel();

        // then
        assertThat(level.getCurrentQuantity()).isEqualTo(3);
    }

    private void catalogWith(Product product) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setName("Karty graficzne");
        product.setStockExpectedQty(3);
        when(productCatalogRepository.findById(STORE_ID, CATALOG_ID)).thenReturn(catalog);
        when(catalog.getCategories()).thenReturn(List.of(definition));
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productRepository.findAll("cat-1")).thenReturn(List.of(product));
        when(rollingPriceAggregateRepository.loadForStore(STORE_ID)).thenReturn(Map.of());
    }

    private void warehouseHolds(WarehouseItemView... items) {
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        when(stockQueryService.searchByEans(eq(STORE_ID), any())).thenAnswer(call -> {
            Collection<String> eans = call.getArgument(1);
            return Stream.of(items).filter(item -> eans.contains(item.getEan())).toList();
        });
    }

    private static WarehouseItemView item(String ean, int qty) {
        return new WarehouseItemView(STORE_ID, "item-" + ean, "GPU", ean, "X1", Price.fromNet(100.0), qty,
                FulfilmentStatus.Delivered, ItemCondition.Sealed);
    }

    private StockProductLevel theOnlyLevel() {
        List<StockProductLevel> levels =
                stockLevels.calculate(STORE_ID, CATALOG_ID, null, RestockScope.ExpectedStockQty, false, false);
        assertThat(levels).hasSize(1);
        return levels.get(0);
    }
}
