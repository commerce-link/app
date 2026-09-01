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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipEligibilityTest {

    private static final String STORE_ID = "store-1";
    private static final String PROVIDER = "Acme";
    private static final String SECOND_PROVIDER = "Elko";

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
        lenient().when(dropshipPurchaseService.isDropshipAvailable(STORE_ID, SECOND_PROVIDER)).thenReturn(true);
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
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(List.of(PROVIDER), assessment.providers());
        assertNull(assessment.rejection());
    }

    @Test
    void allowsDeliveredServiceItemsNextToTheAllocation() {
        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(null, FulfilmentStatus.Delivered)));

        // then
        assertEquals(List.of(PROVIDER), assessment.providers());
        verifyNoInteractions(deliveriesRepository);
    }

    @Test
    void rejectsWarehouseFulfilmentOrders() {
        // given
        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(DropshipRejection.WAREHOUSE_FULFILMENT, assessment.rejection());
        verifyNoInteractions(dropshipPurchaseService);
    }

    @Test
    void rejectsOrdersWithoutShippingDetails() {
        // given
        order.setShippingDetails(null);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(DropshipRejection.NO_SHIPPING_DETAILS, assessment.rejection());
    }

    @Test
    void reportsEverySupplierWhenTheAllocationSpansSeveralOfThem() {
        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation),
                        item(SECOND_PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(List.of(PROVIDER, SECOND_PROVIDER), assessment.providers());
        assertNull(assessment.rejection());
    }

    @Test
    void aMixedOrderReportsOnlyTheDropshipCapableSupplier() {
        // when: one item at Acme, one sitting on the warehouse sentinel
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation),
                        item(OrderItem.GENERIC_WAREHOUSE_ORDER_NO, FulfilmentStatus.Allocation)));

        // then
        assertEquals(List.of(PROVIDER), assessment.providers());
        assertNull(assessment.rejection());
    }

    @Test
    void aSupplierWithoutDropshipIsLeftOutInsteadOfSinkingTheWholeOrder() {
        // given
        when(dropshipPurchaseService.isDropshipAvailable(STORE_ID, SECOND_PROVIDER)).thenReturn(false);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation),
                        item(SECOND_PROVIDER, FulfilmentStatus.Allocation)));

        // then
        assertEquals(List.of(PROVIDER), assessment.providers());
        assertNull(assessment.rejection());
    }

    @Test
    void rejectsWarehouseAllocations() {
        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(OrderItem.GENERIC_WAREHOUSE_ORDER_NO, FulfilmentStatus.Allocation)));

        // then
        assertEquals(DropshipRejection.NO_DROPSHIP_CAPABLE_SUPPLIER, assessment.rejection());
    }

    @Test
    void rejectsOrdersWithUnsettledItemsOutsideTheAllocation() {
        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item(null, FulfilmentStatus.New)));

        // then
        assertEquals(DropshipRejection.UNSETTLED_ITEMS, assessment.rejection());
    }

    @Test
    void rejectsOrdersWithNothingAllocated() {
        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(null, FulfilmentStatus.Delivered)));

        // then
        assertEquals(DropshipRejection.NOTHING_ALLOCATED, assessment.rejection());
    }

    @Test
    void orderedItemOnADropshipDeliveryCountsAsSettled() {
        // given
        Delivery dropshipDelivery = new Delivery();
        dropshipDelivery.setType(DeliveryType.DROPSHIP);
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(dropshipDelivery);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertEquals(List.of(PROVIDER), assessment.providers());
        assertNull(assessment.rejection());
    }

    @Test
    void orderedItemOnAWarehouseDeliveryStillBlocksEligibility() {
        // given
        Delivery warehouseDelivery = new Delivery();
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(warehouseDelivery);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertEquals(DropshipRejection.UNSETTLED_ITEMS, assessment.rejection());
    }

    @Test
    void orderedItemWhoseDeliveryIsGoneBlocksEligibility() {
        // given
        when(deliveriesRepository.findById(eq(STORE_ID), eq("dropship-1"))).thenReturn(null);

        // when
        DropshipAssessment assessment = eligibility.assess(order,
                List.of(item(PROVIDER, FulfilmentStatus.Allocation), item("dropship-1", FulfilmentStatus.Ordered)));

        // then
        assertEquals(DropshipRejection.UNSETTLED_ITEMS, assessment.rejection());
    }
}
