package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.warehouse.StockLevel;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPricingStrategyTest {

    @Mock
    private InventoryView inventoryView;

    @Mock
    private MatchedInventory matchedInventory;

    @Mock
    private CategoryDefinition categoryDefinition;

    @Mock
    private StockDefinition stockDefinition;

    private ProductPricingStrategy pricingStrategy;
    private Product product;
    private PriceDefinition priceDefinition;

    @BeforeEach
    void setUp() {
        pricingStrategy = new ProductPricingStrategy(inventoryView, Collections.emptyMap());

        product = new Product();
        product.setPricingGroup("Default");

        priceDefinition = new PriceDefinition(1.0, 0, 0, 0, 0, "Default");

        when(inventoryView.findByProduct(any())).thenReturn(matchedInventory);
        when(matchedInventory.canBeFulfilledFromWarehouseAtPricePoint(anyDouble())).thenReturn(true);
        when(categoryDefinition.findPriceDefinition("Default")).thenReturn(priceDefinition);
        when(categoryDefinition.getStockDefinition()).thenReturn(stockDefinition);
    }

    @Test
    void shouldUseLowestPriceWhenStockLevelIsHigh() {
        mockPrices(1000.0, 2000.0);
        mockStockLevel(StockLevel.High);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(1009);
    }

    @Test
    void shouldUseAverageOfLowestAndMedianWhenStockLevelIsMedium() {
        mockPrices(1000.0, 2000.0);
        mockStockLevel(StockLevel.Medium);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(1509);
    }

    @Test
    void shouldUseMedianPriceWhenStockLevelIsLow() {
        mockPrices(1000.0, 2000.0);
        mockStockLevel(StockLevel.Low);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(2009);
    }

    @Test
    void shouldUseMedianPriceWhenStockLevelIsCritical() {
        mockPrices(1000.0, 2000.0);
        mockStockLevel(StockLevel.Critical);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(2009);
    }

    @Test
    void shouldCalculateCorrectAverageForMediumStock() {
        mockPrices(1743.0, 2100.0);
        mockStockLevel(StockLevel.Medium);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(1929);
    }

    @Test
    void shouldCalculateCorrectAverageForMediumStockWithMultipleProviders() {
        mockPrices(1743.3, 2254.88);
        mockStockLevel(StockLevel.Medium);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(2009);
    }

    @Test
    void shouldCalculateCorrectAverageForMediumStockWithRollingPriceAggregate() {
        mockPrices(1801.55, 2065.73);
        mockStockLevel(StockLevel.Medium);

        long price = pricingStrategy.calculateGrossPrice(product, categoryDefinition);

        assertThat(price).isEqualTo(1939);
    }

    private void mockStockLevel(StockLevel stockLevel) {
        when(stockDefinition.getStockLevel(matchedInventory)).thenReturn(stockLevel);
    }

    private void mockPrices(double lowestGross, double medianGross) {
        Price lowestPrice = Price.fromGross(lowestGross);
        Price medianPrice = Price.fromGross(medianGross);

        when(matchedInventory.getLowestPrice(true, null)).thenReturn(lowestPrice);
        when(matchedInventory.getLowestPrice(SupplierType.Retailer)).thenReturn(lowestPrice);
        when(matchedInventory.getLowestPrice(SupplierType.Distributor)).thenReturn(lowestPrice);
        when(matchedInventory.getMedianPrice()).thenReturn(medianPrice);
    }

}
