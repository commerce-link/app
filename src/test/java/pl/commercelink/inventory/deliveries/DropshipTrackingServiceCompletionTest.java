package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DropshipTrackingService.TrackingOutcome;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires the real {@link DropshipDeliveryCompletion} into {@link DropshipTrackingService} so that the tests pin
 * what actually lands on the order and the delivery, not how the two classes talk to each other.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DropshipTrackingServiceCompletionTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String DELIVERY_ID = "delivery-1";
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private SupplierProviderResolver providerResolver;
    @Mock
    private SupplierProvider provider;

    private DropshipTrackingService service;
    private Order order;
    private OrderItem first;
    private OrderItem second;
    private Delivery lastFetched;

    @BeforeEach
    void setUp() {
        DropshipDeliveryCompletion completion = new DropshipDeliveryCompletion(deliveriesRepository, ordersRepository,
                orderItemsRepository, orderLifecycle, orderLifecycleEventPublisher);
        service = new DropshipTrackingService(deliveriesQueryService, deliveriesRepository, ordersRepository,
                providerResolver, completion, DropshipTrackingProperties.defaults(),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        first = item("1", "5900000000001");
        second = item("2", "5900000000002");
        // every check reads the delivery afresh, with allocations rebuilt from the current item statuses
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, DELIVERY_ID)).thenAnswer(invocation -> {
            lastFetched = freshDelivery();
            return lastFetched;
        });
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(first, second));
        when(providerResolver.resolve(STORE_ID, "Acme")).thenReturn(provider);
        when(provider.supportsOrderTracking()).thenReturn(true);
    }

    private static OrderItem item(String itemId, String ean) {
        OrderItem item = new OrderItem(ORDER_ID, "Category", "Product " + itemId, 1, 100.0, null, false);
        item.setItemId(itemId);
        item.setDeliveryId(DELIVERY_ID);
        item.setStatus(FulfilmentStatus.Ordered);
        item.setEan(ean);
        item.setManufacturerCode("MFN-" + itemId);
        return item;
    }

    private Delivery freshDelivery() {
        Delivery delivery = new Delivery(STORE_ID, "ACME-DS-ref-1", "Acme");
        delivery.setDeliveryId(DELIVERY_ID);
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setPurchaseRef("ref-1");
        delivery.setOrderedAt(NOW.minusHours(2));
        delivery.setAllocations(List.of(Allocation.fromOrderItem(order, first), Allocation.fromOrderItem(order, second)));
        return delivery;
    }

    private static SupplierParcel parcel(String trackingNo, SupplierOrderLine... lines) {
        return new SupplierParcel("DPD", trackingNo, null, NOW.minusHours(1), List.of(lines));
    }

    private void supplierReports(SupplierOrderState state, SupplierParcel... parcels) {
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(state, List.of(parcels))));
    }

    @Test
    void partiallyShippedParcelsWithLinesCoveringEveryItemCompleteTheDelivery() {
        // given
        supplierReports(SupplierOrderState.PARTIALLY_SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)),
                parcel("PKG-2", new SupplierOrderLine(null, "5900000000002", null, 1)));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, DELIVERY_ID, false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        assertThat(first.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(second.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("PKG-1", "PKG-2");
        assertThat(lastFetched.hasBeenReceived()).isTrue();
        assertThat(lastFetched.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(lastFetched.getTrackingNextCheckAt()).isNull();
        verify(orderLifecycleEventPublisher, times(1)).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
    }

    @Test
    void parcelsSharingOneTrackingNumberProduceASingleShipment() {
        // given: the supplier reports one row per physical package under the same label
        supplierReports(SupplierOrderState.SHIPPED,
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000001", null, 1)),
                parcel("PKG-1", new SupplierOrderLine(null, "5900000000002", null, 1)));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, DELIVERY_ID, false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        assertThat(first.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(second.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("PKG-1");
        assertThat(lastFetched.hasBeenReceived()).isTrue();
        assertThat(lastFetched.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        verify(orderLifecycleEventPublisher, times(1)).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
    }

    @Test
    void lostDeliveryWriteAfterTheOrderWasShippedIsReconciledByTheNextCheck() {
        // given: the delivery save conflicts with a concurrent write once, after the order side is already committed
        supplierReports(SupplierOrderState.SHIPPED, parcel("PKG-1"));
        doThrow(new ConditionalCheckFailedException("stale delivery version"))
                .doNothing()
                .when(deliveriesRepository).save(any());

        // when: the first check propagates the conflict (spec: redelivery, fresh read)
        assertThatThrownBy(() -> service.check(STORE_ID, DELIVERY_ID, false))
                .isInstanceOf(ConditionalCheckFailedException.class);

        // then: the order already carries the shipment and the items are delivered
        assertThat(first.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(second.getStatus()).isEqualTo(FulfilmentStatus.Delivered);
        assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("PKG-1");

        // when: the next check reads the delivery afresh
        TrackingOutcome outcome = service.check(STORE_ID, DELIVERY_ID, false);

        // then: the delivery catches up with the order instead of polling until GIVEN_UP
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        assertThat(lastFetched.hasBeenReceived()).isTrue();
        assertThat(lastFetched.getTrackingState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(lastFetched.getTrackingNextCheckAt()).isNull();
        assertThat(order.getShipments()).extracting(Shipment::getTrackingNo).containsExactly("PKG-1");
        verify(orderLifecycleEventPublisher, times(1)).publish(same(order), same(OrderLifecycleEventType.ShipmentCreated));
        verify(deliveriesRepository, times(2)).save(any());
    }
}
