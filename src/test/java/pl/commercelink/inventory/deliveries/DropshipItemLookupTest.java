package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipItemLookupTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @InjectMocks
    private DropshipItemLookup lookup;

    private static OrderItem item(String itemId, FulfilmentStatus status, String deliveryId) {
        OrderItem item = new OrderItem("order-1", "Category", "Product " + itemId, 1, 100.0, null, false);
        item.setItemId(itemId);
        item.setEan("590000000000" + itemId.charAt(itemId.length() - 1));
        item.setManufacturerCode("MFN-" + itemId);
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        return item;
    }

    private static Delivery delivery(String deliveryId, DeliveryType type) {
        Delivery delivery = new Delivery(STORE_ID, null, "Acme");
        delivery.setDeliveryId(deliveryId);
        delivery.setType(type);
        return delivery;
    }

    @Test
    void returnsTheItemsAllocatedToDropshipDeliveriesLookingEachDeliveryUpOnce() {
        // given
        OrderItem inDropship = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem alsoInDropship = item("i2", FulfilmentStatus.Delivered, "d-1");
        OrderItem inWarehouseDelivery = item("i3", FulfilmentStatus.Ordered, "d-2");
        OrderItem stillAllocating = item("i4", FulfilmentStatus.Allocation, "Acme");
        OrderItem fromStock = item("i5", FulfilmentStatus.Delivered, SupplierRegistry.WAREHOUSE);
        when(deliveriesRepository.findById(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));
        when(deliveriesRepository.findById(STORE_ID, "d-2")).thenReturn(delivery("d-2", DeliveryType.WAREHOUSE));

        // when
        Set<String> ids = lookup.itemIdsInDropshipDeliveries(STORE_ID,
                List.of(inDropship, alsoInDropship, inWarehouseDelivery, stillAllocating, fromStock));

        // then
        assertThat(ids).containsExactlyInAnyOrder("i1", "i2");
        verify(deliveriesRepository, times(1)).findById(STORE_ID, "d-1");
        verify(deliveriesRepository, never()).findById(eq(STORE_ID), eq("Acme"));
        verify(deliveriesRepository, never()).findById(eq(STORE_ID), eq(SupplierRegistry.WAREHOUSE));
    }

    @Test
    void aMissingDeliveryIsNotDropship() {
        // given
        OrderItem orphan = item("i1", FulfilmentStatus.Ordered, "gone");
        when(deliveriesRepository.findById(any(), any())).thenReturn(null);

        // when
        Set<String> ids = lookup.itemIdsInDropshipDeliveries(STORE_ID, List.of(orphan));

        // then
        assertThat(ids).isEmpty();
    }
}
