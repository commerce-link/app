package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchedInventoryTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory inventoryWith(InventoryItem... items) {
        return new MatchedInventory(InventoryKey.fromMfn("M1"), List.of(items), taxonomyCache, supplierRegistry);
    }

    private InventoryItem warehouseItem(double netPrice, boolean inStock) {
        return new InventoryItem("E1", "M1", netPrice, "PLN", 2, 1, SupplierRegistry.WAREHOUSE, true, inStock, !inStock);
    }

    private InventoryItem supplierItem(String supplier, double netPrice, int leadTimeDays) {
        return new InventoryItem("E1", "M1", netPrice, "PLN", 20, leadTimeDays, supplier, true);
    }

    private InventoryItem supplierItemWithQty(String supplier, int qty) {
        return new InventoryItem("E1", "M1", 1000.0, "PLN", qty, 1, supplier, true);
    }

    private SupplierInfo supplierInfo(String name, int arrivalDays) {
        return supplierInfo(name, "PL", arrivalDays);
    }

    private SupplierInfo supplierInfo(String name, String origin, int arrivalDays) {
        return new SupplierInfo(name, SupplierType.Distributor, 1, origin,
                new ShippingPolicy(new ShippingTerms(arrivalDays, new ShippingCostPolicy.Free())));
    }

    private InventoryItem supplierItem(String supplier, double netPrice, int qty, int leadTimeDays) {
        return new InventoryItem("E1", "M1", netPrice, "PLN", qty, leadTimeDays, supplier, true);
    }

    @Test
    void narrowsInventoryToItemsWithinGrossPricePoint() {
        // given
        MatchedInventory matched = inventoryWith(
                supplierItem("Cheap", /* net */ 100.0, /* gross 123 */ 5, 1),
                supplierItem("Pricey", /* net */ 200.0, /* gross 246 */ 20, 1)
        );

        // when
        MatchedInventory atPrice = matched.atPricePoint(150);

        // then
        assertThat(atPrice.getSuppliers()).containsExactly("Cheap");
        assertThat(atPrice.getTotalAvailableQty()).isEqualTo(5L);
        assertThat(atPrice.hasOffersFromMultipleSuppliers(2, 1)).isFalse();
        assertThat(atPrice.getInventoryKey()).isSameAs(matched.getInventoryKey());
        assertThat(matched.getTotalAvailableQty()).isEqualTo(25L);
    }

    @Test
    void sumsAvailableQtyOfSingleSupplier() {
        // given
        MatchedInventory matched = inventoryWith(warehouseItem(1000.0, true), supplierItemWithQty("Action", 7));

        // when / then
        assertThat(matched.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE)).isEqualTo(2L);
        assertThat(matched.getTotalAvailableQtyFromSupplier("Action")).isEqualTo(7L);
        assertThat(matched.getTotalAvailableQtyFromSupplier("Missing")).isEqualTo(0L);
    }

    @Test
    void countsOnlyLocalSuppliersWithRequiredQty() {
        // given
        when(supplierRegistry.get("Local")).thenReturn(supplierInfo("Local", "PL", 1));
        when(supplierRegistry.get("Foreign")).thenReturn(supplierInfo("Foreign", "DE", 3));
        MatchedInventory matched = inventoryWith(supplierItemWithQty("Local", 10), supplierItemWithQty("Foreign", 10));

        // when / then
        assertThat(matched.hasOffersFromMultipleSuppliers(2, 5)).isTrue();
        assertThat(matched.hasOffersFromMultipleLocalSuppliers(1, 5)).isTrue();
        assertThat(matched.hasOffersFromMultipleLocalSuppliers(2, 5)).isFalse();
    }

    @Test
    void ignoresLocalSuppliersBelowRequiredQty() {
        // given
        when(supplierRegistry.get("Local")).thenReturn(supplierInfo("Local", "PL", 1));
        MatchedInventory matched = inventoryWith(supplierItemWithQty("Local", 3));

        // when / then
        assertThat(matched.hasOffersFromMultipleLocalSuppliers(1, 5)).isFalse();
        assertThat(matched.hasOffersFromMultipleLocalSuppliers(0, 5)).isTrue();
    }

    @Test
    void returnsOneDayWhenWarehouseHasDeliveredItemsRegardlessOfPrice() {
        // given
        MatchedInventory matched = inventoryWith(warehouseItem(1199.99, true), supplierItem("Action", 1175.49, 1));

        // when
        int days = matched.getEstimatedDeliveryDays(1449);

        // then
        assertThat(days).isEqualTo(1);
    }

    @Test
    void calculatesDaysFromOffersWhenWarehouseItemsAreOnlyOrdered() {
        // given
        when(supplierRegistry.get(SupplierRegistry.WAREHOUSE)).thenReturn(supplierInfo(SupplierRegistry.WAREHOUSE, 1));
        MatchedInventory matched = inventoryWith(warehouseItem(1000.0, false));

        // when
        int days = matched.getEstimatedDeliveryDays(1449);

        // then
        assertThat(days).isEqualTo(2);
    }

    @Test
    void skipsOrderedWarehouseItemsAbovePricePoint() {
        // given
        when(supplierRegistry.get("Action")).thenReturn(supplierInfo("Action", 2));
        MatchedInventory matched = inventoryWith(warehouseItem(1199.99, false), supplierItem("Action", 1175.49, 2));

        // when
        int days = matched.getEstimatedDeliveryDays(1449);

        // then
        assertThat(days).isEqualTo(4);
    }
}
