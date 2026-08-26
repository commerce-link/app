package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipEligibilityTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";

    @Mock
    private DropshipPurchaseService dropshipPurchaseService;

    @Mock
    private DeliveriesRepository deliveriesRepository;

    @InjectMocks
    private DropshipEligibility eligibility;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setStoreId(STORE_ID);
        order.setOrderId("order-1");
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("ul. Polna 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setPhone("+48601234567");
        details.setEmail("jan@example.com");
        order.setShippingDetails(details);
        lenient().when(dropshipPurchaseService.isDropshipAvailable(STORE_ID, PROVIDER)).thenReturn(true);
    }

    private static OrderItem item(String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem();
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-1");
        return item;
    }

    @Test
    void reportsTheSingleSupplierOfAFullyAllocatedOrder() {
        // when
        Optional<String> provider = eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(Optional.of(PROVIDER), provider);
    }

    @Test
    void allowsDeliveredServiceItemsNextToTheAllocation() {
        // when
        Optional<String> provider = eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(null, FulfilmentStatus.Delivered)));

        // then
        assertEquals(Optional.of(PROVIDER), provider);
        verifyNoInteractions(deliveriesRepository);
    }

    @Test
    void rejectsWarehouseFulfilmentOrders() {
        // given
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation))).isEmpty());
        verifyNoInteractions(dropshipPurchaseService);
    }

    @Test
    void rejectsOrdersWithoutShippingDetails() {
        // given
        order.setShippingDetails(null);

        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation))).isEmpty());
    }

    @Test
    void rejectsAllocationsSplitAcrossSuppliers() {
        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("Elko", FulfilmentStatus.Allocation)))
                .isEmpty());
    }

    @Test
    void rejectsWarehouseAllocations() {
        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(OrderItem.GENERIC_WAREHOUSE_ORDER_NO, FulfilmentStatus.Allocation))).isEmpty());
    }

    @Test
    void rejectsOrdersWithUnsettledItemsOutsideTheAllocation() {
        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(null, FulfilmentStatus.New))).isEmpty());
    }

    @Test
    void rejectsOrdersWithNothingAllocated() {
        // when / then
        assertTrue(eligibility.eligibleProvider(order,
                List.of(item(null, FulfilmentStatus.Delivered))).isEmpty());
    }

    @Test
    void orderedItemOnADropshipDeliveryCountsAsSettled() {
        // given
        Delivery dropshipDelivery = new Delivery();
        dropshipDelivery.setType(DeliveryType.DROPSHIP);
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(dropshipDelivery);

        // when
        Optional<String> provider = eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertEquals(Optional.of(PROVIDER), provider);
    }

    @Test
    void orderedItemOnAWarehouseDeliveryStillBlocksEligibility() {
        // given
        Delivery warehouseDelivery = new Delivery();
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(warehouseDelivery);

        // when
        Optional<String> provider = eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertTrue(provider.isEmpty());
    }

    @Test
    void orderedItemWhoseDeliveryIsGoneBlocksEligibility() {
        // given
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(null);

        // when
        Optional<String> provider = eligibility.eligibleProvider(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertTrue(provider.isEmpty());
    }
}
