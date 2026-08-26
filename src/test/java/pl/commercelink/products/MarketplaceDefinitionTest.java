package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceDefinitionTest {

    private static final String MARKETPLACE = "Morele";

    @Mock
    private MatchedInventory inventory;

    private MarketplaceDefinition warehouseDefinition(int minWarehouseQty) {
        return definition(0, 0, 0, 0, minWarehouseQty);
    }

    private MarketplaceDefinition distributorsDefinition(int minQtyPerDistributor, int minNumOfDistributors) {
        return definition(0, minQtyPerDistributor, minNumOfDistributors, 0, 0);
    }

    private MarketplaceDefinition definition(int minDistributorsQty, int minQtyPerDistributor, int minNumOfDistributors, int minNumOfLocalDistributors, int minWarehouseQty) {
        return new MarketplaceDefinition(MARKETPLACE, 1.1, minDistributorsQty, minQtyPerDistributor, minNumOfDistributors, minNumOfLocalDistributors, minWarehouseQty);
    }

    private void inventoryWith(long warehouseQty, boolean distributorsCriteriaMet, long totalAvailableQty) {
        when(inventory.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE)).thenReturn(warehouseQty);
        when(inventory.hasOffersFromMultipleSuppliers(anyInt(), anyInt())).thenReturn(distributorsCriteriaMet);
        when(inventory.hasOffersFromMultipleLocalSuppliers(anyInt(), anyInt())).thenReturn(true);
        when(inventory.hasTotalMinQty(anyInt())).thenAnswer(invocation -> totalAvailableQty >= invocation.<Integer>getArgument(0));
        when(inventory.getTotalAvailableQty()).thenReturn(totalAvailableQty);
    }

    @Test
    void isCompleteWhenOnlyDistributorsCriteriaAreSet() {
        // when / then
        assertThat(distributorsDefinition(5, 2).isComplete()).isTrue();
    }

    @Test
    void isCompleteWhenOnlyWarehouseCriteriaAreSet() {
        // when / then
        assertThat(warehouseDefinition(3).isComplete()).isTrue();
    }

    @Test
    void isNotCompleteWhenDistributorsCriteriaArePartiallySet() {
        // when / then
        assertThat(distributorsDefinition(5, 0).isComplete()).isFalse();
        assertThat(distributorsDefinition(0, 2).isComplete()).isFalse();
    }

    @Test
    void isNotCompleteWithoutNameOrMarkup() {
        // when / then
        assertThat(new MarketplaceDefinition(null, 1.1, 0, 5, 2, 0, 3).isComplete()).isFalse();
        assertThat(new MarketplaceDefinition(MARKETPLACE, 0, 0, 5, 2, 0, 3).isComplete()).isFalse();
    }

    @Test
    void publishesWarehouseQtyWhenWarehouseCriteriaAreMet() {
        // given
        inventoryWith(/* warehouseQty */ 10, false, 25);

        // when
        long qty = warehouseDefinition(5).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(10L);
    }

    @Test
    void publishesZeroWhenOnlyWarehouseCriteriaAreConfiguredAndNotMet() {
        // given
        inventoryWith(/* warehouseQty */ 2, true, 25);

        // when
        long qty = warehouseDefinition(5).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(0L);
        verify(inventory, never()).hasOffersFromMultipleSuppliers(anyInt(), anyInt());
    }

    @Test
    void publishesTotalQtyWhenDistributorsCriteriaAreMet() {
        // given
        inventoryWith(0, /* distributorsCriteriaMet */ true, /* totalAvailableQty */ 25);

        // when
        long qty = distributorsDefinition(/* minQtyPerDistributor */ 5, /* minNumOfDistributors */ 2).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(25L);
        verify(inventory).hasOffersFromMultipleSuppliers(2, 5);
        verify(inventory, never()).getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE);
    }

    @Test
    void publishesZeroWhenDistributorsCriteriaAreNotMet() {
        // given
        inventoryWith(0, /* distributorsCriteriaMet */ false, 25);

        // when
        long qty = distributorsDefinition(5, 2).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(0L);
    }

    @Test
    void publishesZeroWhenDistributorsSummedQtyIsBelowMinimum() {
        // given
        inventoryWith(0, true, /* totalAvailableQty */ 25);

        // when
        long qty = definition(/* minDistributorsQty */ 30, 5, 2, 0, 0).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(0L);
        verify(inventory).hasTotalMinQty(30);
    }

    @Test
    void publishesTotalQtyWhenDistributorsSummedQtyMeetsMinimum() {
        // given
        inventoryWith(0, true, /* totalAvailableQty */ 25);

        // when
        long qty = definition(/* minDistributorsQty */ 20, 5, 2, 0, 0).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(25L);
    }

    @Test
    void publishesZeroWhenLocalDistributorsCriteriaAreNotMet() {
        // given
        inventoryWith(0, true, 25);
        when(inventory.hasOffersFromMultipleLocalSuppliers(anyInt(), anyInt())).thenReturn(false);

        // when
        long qty = definition(0, 5, 2, /* minNumOfLocalDistributors */ 1, 0).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(0L);
        verify(inventory).hasOffersFromMultipleLocalSuppliers(1, 5);
    }

    @Test
    void prefersWarehouseQtyWhenBothCriteriaAreMet() {
        // given
        inventoryWith(/* warehouseQty */ 10, true, /* totalAvailableQty */ 25);

        // when
        long qty = definition(0, 5, 2, 0, /* minWarehouseQty */ 5).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(10L);
        verify(inventory, never()).getTotalAvailableQty();
    }

    @Test
    void fallsBackToDistributorsCriteriaWhenWarehouseCriteriaAreNotMet() {
        // given
        inventoryWith(/* warehouseQty */ 2, true, /* totalAvailableQty */ 25);

        // when
        long qty = definition(0, 5, 2, 0, /* minWarehouseQty */ 5).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(25L);
    }

    @Test
    void publishesZeroWhenNeitherCriteriaAreMet() {
        // given
        inventoryWith(2, false, 25);

        // when
        long qty = definition(0, 5, 2, 0, 5).qtyToPublish(inventory);

        // then
        assertThat(qty).isEqualTo(0L);
    }
}
