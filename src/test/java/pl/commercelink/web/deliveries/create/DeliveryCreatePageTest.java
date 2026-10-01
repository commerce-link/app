package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.orders.*;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


class DeliveryCreatePageTest {

    @Test
    void warehousePageCountsTheOrdersBehindTheItemsAndSpotsTheRestock() {
        // given
        DeliveryScope scope = mock(DeliveryScope.class);
        when(scope.provider()).thenReturn("Acme");
        when(scope.purchaseAvailable()).thenReturn(true);
        when(scope.requiresOrderIdentity()).thenReturn(true);
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setItems(List.of(item(orderAllocation("o-1"), orderAllocation("o-2")), item(orderAllocation("o-1"), restock())));
        DeliveryCreateLinks links = DeliveryCreateLinks.of(false, "s", "Acme", null, null);

        // when
        DeliveryCreatePage page = DeliveryCreatePage.of(scope, links, "Acme", form);

        // then
        assertThat(page.dropship()).isFalse();
        assertThat(page.orderCount()).isEqualTo(2);
        assertThat(page.restock()).isTrue();
        assertThat(page.purchaseAvailable()).isTrue();
        assertThat(page.consignee()).isNull();
    }

    @Test
    void dropshipPageCarriesTheConsigneeAndThePickupPoint() {
        // given
        Order order = new Order("s");
        order.setOrderId("o-1");
        ShippingDetails consignee = new ShippingDetails();
        order.setShippingDetails(consignee);
        Shipment pickup = new Shipment(ShipmentType.PickupPoint);
        order.addShipment(pickup);
        DeliveryScope scope = mock(DeliveryScope.class);
        when(scope.order()).thenReturn(order);
        when(scope.purchaseBlockedReason()).thenReturn("orders.dropship.error.pickupPointUnsupported");

        // when
        DeliveryCreatePage page = DeliveryCreatePage.of(scope, DeliveryCreateLinks.of(false, "s", "Acme", "o-1", null),
                "Acme", new DeliveryCreationForm());

        // then
        assertThat(page.dropship()).isTrue();
        assertThat(page.consignee()).isSameAs(consignee);
        assertThat(page.pickupShipment()).isSameAs(pickup);
        assertThat(page.purchaseBlockedReason()).isEqualTo("orders.dropship.error.pickupPointUnsupported");
    }

    private static DeliveryItem item(Allocation... allocations) {
        DeliveryItem item = new DeliveryItem();
        item.setAllocations(new ArrayList<>(List.of(allocations)));
        return item;
    }

    private static Allocation orderAllocation(String orderId) {
        Allocation allocation = new Allocation();
        allocation.setType(AllocationType.Order);
        allocation.setKey(new AllocationKey(orderId, "i-" + orderId, "client@" + orderId));
        return allocation;
    }

    private static Allocation restock() {
        Allocation allocation = new Allocation();
        allocation.setType(AllocationType.Warehouse);
        allocation.setKey(new AllocationKey(null, "w-1", null));
        return allocation;
    }
}
