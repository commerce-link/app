package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.warehouse.StockLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDefinitionTest {

    @Mock
    private MatchedInventory matchedInventory;

    private StockDefinition stockDefinition = new StockDefinition(5, 10, 20);

    @Test
    void shouldShowHighStockLevelIfOnlyOneLocalDistributorHasItemWithExcessQty() {
        mockDistributorsAvailability(1L, 50L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.High);
    }

    @Test
    void shouldShowCriticalStockLevelIfOnlyOneForeignDistributorHasItem() {
        mockDistributorsAvailability(1L, 50L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(false);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Critical);
    }

    @Test
    void shouldShowLowStockLevelIfMultipleForeignDistributorHasItemWithReasonableQty() {
        mockDistributorsAvailability(2L, 12L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(false);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Low);
    }

    @Test
    void shouldShowHighStockLevelIfThreeDistributorsHaveProduct() {
        mockDistributorsAvailability(3L, 5L);
        mockRetailersAvailability( 1L, 5L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.High);
    }

    @Test
    void shouldShowHighStockLevelIfRetailerQtyIsHigh() {
        mockDistributorsAvailability(1L, 5L);
        mockRetailersAvailability( 1L, 20L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.High);
    }

    @Test
    void shouldShowCriticalStockLevelIfNoDistributorsAndRetailerQtyIsCriticalOrLess() {
        mockDistributorsAvailability(0L, 0L);
        mockRetailersAvailability( 1L, 5L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Critical);
    }

    @Test
    void shouldShowLowStockLevelIfNoDistributorsAndRetailerQtyIsLow() {
        mockDistributorsAvailability(0L, 0L);
        mockRetailersAvailability( 1L, 10L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Low);
    }

    @Test
    void shouldShowMediumStockLevelIfNoDistributorsAndRetailerQtyIsAboveLow() {
        mockDistributorsAvailability(0L, 0L);
        mockRetailersAvailability( 1L, 15L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Medium);
    }

    @Test
    void shouldShowCriticalStockLevelIfNoRetailersAndDistributorQtyIsCritical() {
        mockDistributorsAvailability(1L, 5L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Critical);
    }

    @Test
    void shouldShowLowStockLevelIfNoRetailersAndDistributorQtyIsLow() {
        mockDistributorsAvailability(1L, 10L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Low);
    }

    @Test
    void shouldShowMediumStockLevelIfNoRetailersAndDistributorQtyIsAboveLow() {
        mockDistributorsAvailability(1L, 15L);
        mockRetailersAvailability( 0L, 0L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Medium);
    }

    @Test
    void shouldShowCriticalStockLevelIfBothDistributorAndRetailerQtyAreCriticalOrLess() {
        mockDistributorsAvailability(1L, 5L);
        mockRetailersAvailability( 1L, 5L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Critical);
    }

    @Test
    void shouldShowLowStockLevelIfBothDistributorAndRetailerQtyAreLow() {
        mockDistributorsAvailability(1L, 10L);
        mockRetailersAvailability( 1L, 10L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Low);
    }

    @Test
    void shouldShowMediumStockLevelInOtherCases() {
        mockDistributorsAvailability(2L, 15L);
        mockRetailersAvailability( 2L, 15L);
        mockHasOffersFromLocalSuppliers(true);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Medium);
    }

    private void mockDistributorsAvailability(long noOfDistributorsWithProduct, long totalAvailableQty) {
        mockAvailability(SupplierType.Distributor, noOfDistributorsWithProduct, totalAvailableQty);
    }

    private void mockRetailersAvailability(long noOfRetailersWithProduct, long totalAvailableQty) {
        mockAvailability(SupplierType.Retailer, noOfRetailersWithProduct, totalAvailableQty);
    }

    private void mockAvailability(SupplierType distributor, long noOfSuppliersWithProduct, long totalAvailableQty) {
        when(matchedInventory.getNoOfSuppliersWithProduct(distributor)).thenReturn(noOfSuppliersWithProduct);
        when(matchedInventory.getTotalAvailableQty(distributor)).thenReturn(totalAvailableQty);
    }

    private void mockHasOffersFromLocalSuppliers(boolean value) {
        when(matchedInventory.hasOffersFromLocalSuppliers()).thenReturn(value);
    }

    private void mockHasOffersFromWarehouse(boolean value) {
        when(matchedInventory.hasOffersFrom(SupplierRegistry.WAREHOUSE)).thenReturn(value);
    }

    @Test
    void shouldShowHighStockLevelIfWarehouseHasProductAndOtherDistributorExists() {
        mockHasOffersFromWarehouse(true);
        mockDistributorsAvailability(2L, 5L);
        mockRetailersAvailability(0L, 0L);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.High);
    }

    @Test
    void shouldShowMediumStockLevelIfOnlyWarehouseHasProduct() {
        mockHasOffersFromWarehouse(true);
        mockDistributorsAvailability(0L, 0L);
        mockRetailersAvailability(0L, 0L);

        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);

        assertThat(stockLevel).isEqualTo(StockLevel.Medium);
    }

}