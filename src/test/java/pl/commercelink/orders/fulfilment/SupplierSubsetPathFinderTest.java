package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static pl.commercelink.orders.fulfilment.FulfilmentTestFixtures.group;
import static pl.commercelink.orders.fulfilment.FulfilmentTestFixtures.supplier;

@ExtendWith(MockitoExtension.class)
class SupplierSubsetPathFinderTest {

    @Mock
    private SupplierRegistry supplierRegistry;

    @Test
    void picksCheapestSupplierForSingleItemIncludingShipping() {
        // given
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.FlatRate(1000, 50)));
        when(supplierRegistry.get("B")).thenReturn(supplier("B", new ShippingCostPolicy.Free()));
        List<FulfilmentGroup> groups = List.of(group("A", "item-1", 100), group("B", "item-1", 120));

        // when
        List<FulfilmentPath> paths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(1, paths.size());
        assertEquals(List.of("B"), paths.get(0).getProviders());
        assertEquals(120.0, paths.get(0).getEstimatedTotalValue());
    }

    @Test
    void consolidationBeatsSplitWhenShippingOutweighsItemSavings() {
        // given
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.Free()));
        when(supplierRegistry.get("B")).thenReturn(supplier("B", new ShippingCostPolicy.FlatRate(1000, 30)));
        List<FulfilmentGroup> groups = List.of(
                group("A", "item-1", 100),
                group("B", "item-1", 90),
                group("A", "item-2", 100));

        // when
        List<FulfilmentPath> paths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(List.of("A"), paths.get(0).getProviders());
        assertEquals(200.0, paths.get(0).getEstimatedTotalValue());
        assertEquals(220.0, paths.get(1).getEstimatedTotalValue());
    }

    @Test
    void aggregatesProviderValueAcrossGroupsForFreeShippingThreshold() {
        // given
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.FlatRate(150, 40)));
        List<FulfilmentGroup> groups = List.of(group("A", "item-1", 100), group("A", "item-2", 80));

        // when
        List<FulfilmentPath> paths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(1, paths.size());
        assertEquals(180.0, paths.get(0).getEstimatedTotalValue());
    }

    @Test
    void warehouseGroupIsAlwaysChosenForItsAllocation() {
        // given
        when(supplierRegistry.get("Warehouse")).thenReturn(supplier("Warehouse", new ShippingCostPolicy.Free()));
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.Free()));
        List<FulfilmentGroup> groups = List.of(
                group("Warehouse", "item-1", 120),
                group("A", "item-1", 100),
                group("A", "item-2", 50));

        // when
        List<FulfilmentPath> paths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(1, paths.size());
        assertTrue(paths.get(0).getProviders().contains("Warehouse"));
        assertEquals(170.0, paths.get(0).getEstimatedTotalValue());
    }

    @Test
    void returnsOptimalPathPerSupplierCount() {
        // given
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.Free()));
        when(supplierRegistry.get("B")).thenReturn(supplier("B", new ShippingCostPolicy.FlatRate(1000, 20)));
        when(supplierRegistry.get("C")).thenReturn(supplier("C", new ShippingCostPolicy.FlatRate(1000, 25)));
        List<FulfilmentGroup> groups = List.of(
                group("A", "item-1", 100), group("B", "item-1", 80), group("C", "item-1", 90),
                group("A", "item-2", 100), group("B", "item-2", 95), group("C", "item-2", 60));

        // when
        List<FulfilmentPath> paths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(2, paths.size());
        assertEquals(List.of("C"), paths.get(0).getProviders());
        assertEquals(175.0, paths.get(0).getEstimatedTotalValue());
        assertEquals(2, paths.get(1).size());
        assertEquals(185.0, paths.get(1).getEstimatedTotalValue());
    }

    @Test
    void agreesWithHeuristicFinderOnCheapestPath() {
        // given
        when(supplierRegistry.get("A")).thenReturn(supplier("A", new ShippingCostPolicy.Free()));
        when(supplierRegistry.get("B")).thenReturn(supplier("B", new ShippingCostPolicy.FlatRate(1000, 20)));
        when(supplierRegistry.get("C")).thenReturn(supplier("C", new ShippingCostPolicy.FlatRate(1000, 25)));
        List<FulfilmentGroup> groups = List.of(
                group("A", "item-1", 100), group("B", "item-1", 80), group("C", "item-1", 90),
                group("A", "item-2", 100), group("B", "item-2", 95), group("C", "item-2", 60));

        // when
        List<FulfilmentPath> exactPaths = new SupplierSubsetPathFinder(supplierRegistry).resolve(groups);
        List<FulfilmentPath> heuristicPaths = new FulfilmentPathFinder(supplierRegistry).resolve(groups);

        // then
        assertEquals(heuristicPaths.get(0).getEstimatedTotalValue(), exactPaths.get(0).getEstimatedTotalValue());
    }
}
