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

    @Test
    void anOrderIsEntirelyDropshipOnlyWhenEveryDeliveryBehindItsItemsIsOne() {
        // given
        OrderItem first = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem second = item("i2", FulfilmentStatus.Ordered, "d-2");
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-2")).thenReturn(delivery("d-2", DeliveryType.DROPSHIP));

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(first, second))).isTrue();
    }

    @Test
    void oneWarehouseLegIsEnoughToMakeTheOrderNotEntirelyDropship() {
        // given
        OrderItem dropshipped = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem throughTheWarehouse = item("i2", FulfilmentStatus.Ordered, "d-2");
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-2")).thenReturn(delivery("d-2", DeliveryType.WAREHOUSE));

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(dropshipped, throughTheWarehouse))).isFalse();
    }

    @Test
    void anOrderWithNoResolvableDeliveryIsNotEntirelyDropship() {
        // given
        OrderItem stillAllocating = item("i1", FulfilmentStatus.Allocation, "Acme");
        OrderItem withoutADelivery = item("i2", FulfilmentStatus.New, null);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "Acme")).thenReturn(null);

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(stillAllocating, withoutADelivery))).isFalse();
    }

    @Test
    void anItemNotOnADeliveryYetStopsTheOrderFromCountingAsEntirelyDropship() {
        // given: the dropship leg has been bought, the second item is still waiting for its purchase and
        // carries only its supplier's name - it may yet travel through our warehouse
        OrderItem dropshipped = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem notBoughtYet = item("i2", FulfilmentStatus.Allocation, "Acme");
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "Acme")).thenReturn(null);

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(dropshipped, notBoughtYet))).isFalse();
    }

    @Test
    void anItemTakenFromOurOwnShelfStopsTheOrderFromCountingAsEntirelyDropship() {
        // given: stock already on the shelf resolves to no delivery, and it is exactly the goods somebody
        // still has to pick and pack
        OrderItem dropshipped = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem fromStock = item("i2", FulfilmentStatus.Delivered, SupplierRegistry.WAREHOUSE);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));
        when(deliveriesRepository.findByIdConsistently(STORE_ID, SupplierRegistry.WAREHOUSE)).thenReturn(null);

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(dropshipped, fromStock))).isFalse();
    }

    @Test
    void aServiceCarriesNoGoodsAndDoesNotDecideTheRoute() {
        // given
        OrderItem dropshipped = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem service = item("i2", FulfilmentStatus.Delivered, OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        service.setService(true);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(delivery("d-1", DeliveryType.DROPSHIP));

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(dropshipped, service))).isTrue();
        verify(deliveriesRepository, never()).findByIdConsistently(STORE_ID, OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
    }

    @Test
    void anOrderOfNothingButServicesIsNotEntirelyDropship() {
        // given
        OrderItem service = item("i1", FulfilmentStatus.Delivered, OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        service.setService(true);

        // when / then
        assertThat(lookup.isEntirelyDropship(STORE_ID, List.of(service))).isFalse();
    }

    @Test
    void theRouteLooksUpEachDeliveryOnceAndSkipsTheOnesThatDoNotExist() {
        // given
        OrderItem first = item("i1", FulfilmentStatus.Ordered, "d-1");
        OrderItem sameDelivery = item("i2", FulfilmentStatus.Ordered, "d-1");
        OrderItem orphan = item("i3", FulfilmentStatus.Ordered, "gone");
        Delivery dropship = delivery("d-1", DeliveryType.DROPSHIP);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "d-1")).thenReturn(dropship);
        when(deliveriesRepository.findByIdConsistently(STORE_ID, "gone")).thenReturn(null);

        // when
        DropshipItemLookup.GoodsRoute route = lookup.routeOf(STORE_ID, List.of(first, sameDelivery, orphan));

        // then: the orphan still counts against the route even though it contributes no delivery
        assertThat(route.deliveries()).containsExactly(dropship);
        assertThat(route.entirelyDropship()).isFalse();
        verify(deliveriesRepository, times(1)).findByIdConsistently(STORE_ID, "d-1");
    }
}
