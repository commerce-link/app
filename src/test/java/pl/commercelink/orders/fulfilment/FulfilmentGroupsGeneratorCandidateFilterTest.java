package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.orders.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfilmentGroupsGeneratorCandidateFilterTest {

    @Mock
    private InventoryView inventory;
    @Mock
    private MatchedInventory matchedInventory;

    private static OrderItem serviceItem() {
        OrderItem item = new OrderItem("order-1", OrderItem.DELIVERY_CATEGORY, "Dostawa", 1, 9.99, "SHIPPING", false);
        item.setService(true);
        return item;
    }

    private static OrderItem productItem() {
        return new OrderItem("order-1", "Category", "Product", 1, 199.99, "MFN-1", false);
    }

    private static InventoryItem warehouseItem() {
        return new InventoryItem("5900000000014", "MFN-1", 99.0, "PLN", 3, 1, SupplierRegistry.WAREHOUSE, true, true, false);
    }

    private static InventoryItem acmeItem() {
        return new InventoryItem("5900000000014", "MFN-1", 109.0, "PLN", 5, 2, "Acme", true, true, false);
    }

    @Test
    @DisplayName("without a candidate filter every candidate with a provider is kept")
    void keepsCandidatesByDefault() {
        List<FulfilmentItem> items = FulfilmentGroupsGenerator.builder().build().run(List.of(serviceItem()));

        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("the candidate filter drops candidates it rejects")
    void dropsRejectedCandidates() {
        List<FulfilmentItem> items = FulfilmentGroupsGenerator.builder()
                .withCandidateFilter(candidate -> false)
                .build()
                .run(List.of(serviceItem()));

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("a routed order falls back to its supplier when warehouse stock is not permitted")
    void routedOrderFallsBackToSupplierCandidatesWhenWarehouseStockIsNotPermitted() {
        // given
        when(inventory.findByProductCode("MFN-1")).thenReturn(matchedInventory);
        when(matchedInventory.getInventoryItems()).thenReturn(List.of(warehouseItem(), acmeItem()));

        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .withCandidateFilter(c -> !SupplierRegistry.WAREHOUSE.equals(c.getSource().getProvider()))
                .build();

        // when
        List<FulfilmentItem> items = generator.run(List.of(productItem()));

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSource().getProvider()).isEqualTo("Acme");
    }

    @Test
    @DisplayName("an unrouted order still prefers warehouse stock")
    void unroutedOrderStillPrefersWarehouseStock() {
        // given
        when(inventory.findByProductCode("MFN-1")).thenReturn(matchedInventory);
        when(matchedInventory.getInventoryItems()).thenReturn(List.of(warehouseItem(), acmeItem()));

        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .build();

        // when
        List<FulfilmentItem> items = generator.run(List.of(productItem()));

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSource().getProvider()).isEqualTo(SupplierRegistry.WAREHOUSE);
    }

    @Test
    @DisplayName("an item without offers keeps the manual placeholder")
    void itemWithoutOffersKeepsTheManualPlaceholder() {
        // given
        when(inventory.findByProductCode("MFN-1")).thenReturn(matchedInventory);
        when(matchedInventory.getInventoryItems()).thenReturn(List.of());

        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory)
                .withCandidateFilter(c -> !SupplierRegistry.WAREHOUSE.equals(c.getSource().getProvider()))
                .build();

        // when
        List<FulfilmentItem> items = generator.run(List.of(productItem()));

        // then
        assertThat(items).isEmpty();
    }
}
