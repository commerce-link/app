package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderState;
import pl.commercelink.inventory.supplier.api.SupplierOrderTracking;
import pl.commercelink.inventory.supplier.api.SupplierParcel;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.util.OperationResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DropshipTrackingServiceManualTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private SupplierProviderResolver providerResolver;
    @Mock
    private DropshipDeliveryCompletion completion;
    @Mock
    private SupplierProvider provider;

    private DropshipTrackingService service;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        service = new DropshipTrackingService(deliveriesQueryService, deliveriesRepository, ordersRepository,
                providerResolver, completion, DropshipTrackingProperties.defaults(),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        delivery = new Delivery(STORE_ID, "ACME-DS-ref-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setPurchaseRef("ref-1");
        delivery.setOrderedAt(NOW.minusDays(20));
        delivery.tracking().setNextCheckAt(NOW.plusHours(1));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(providerResolver.resolve(STORE_ID, "Acme")).thenReturn(provider);
        when(provider.supportsOrderTracking()).thenReturn(true);
    }

    @Test
    void manualCheckIgnoresScheduleAndAge() {
        // given
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.PROCESSING, List.of())));

        // when
        ManualTrackingOutcome outcome = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(outcome).isEqualTo(ManualTrackingOutcome.STILL_PROCESSING);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(delivery.getTrackingView().getLastCheckedAt()).isEqualTo(NOW);
    }

    @Test
    void manualCheckOnGivenUpDoesNotRearmWhenStillProcessing() {
        // given
        delivery.tracking().setState(DeliveryTrackingState.GIVEN_UP);
        when(provider.trackOrder(any())).thenReturn(Optional.empty());

        // when
        ManualTrackingOutcome outcome = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(outcome).isEqualTo(ManualTrackingOutcome.STILL_PROCESSING);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
    }

    @Test
    void manualCheckWithoutOpenAllocationsStaysGivenUp() {
        // given
        delivery.tracking().setState(DeliveryTrackingState.GIVEN_UP);
        delivery.setAllocations(List.of());
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.SHIPPED,
                List.of(new SupplierParcel("DPD", "PKG-1", null, null, null)))));

        // when
        ManualTrackingOutcome outcome = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(outcome).isEqualTo(ManualTrackingOutcome.STILL_PROCESSING);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
        verify(completion, never()).confirmShipped(any(), any(), anyList(), anyList(), any(), any());
    }

    @Test
    void manualCheckOnGivenUpAppliesParcelsAndConfirms() {
        // given
        delivery.tracking().setState(DeliveryTrackingState.GIVEN_UP);
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        OrderItem item = new OrderItem(ORDER_ID, "Category", "Product 1", 1, 100.0, null, false);
        item.setItemId("1");
        item.setDeliveryId(delivery.getDeliveryId());
        item.setStatus(FulfilmentStatus.Ordered);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-1");
        Allocation allocation = Allocation.fromOrderItem(order, item);
        allocation.setInAllocation(true);
        delivery.setAllocations(List.of(allocation));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.SHIPPED,
                List.of(new SupplierParcel("DPD", "PKG-1", null, null, null)))));
        when(completion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    BiConsumer<Delivery, DropshipShipmentResult> beforeSave = invocation.getArgument(5);
                    beforeSave.accept(invocation.getArgument(1), DropshipShipmentResult.COMPLETED);
                    return OperationResult.success(DropshipShipmentResult.COMPLETED);
                });

        // when
        ManualTrackingOutcome outcome = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(outcome).isEqualTo(ManualTrackingOutcome.CONFIRMED);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(delivery.getTrackingView().getNextCheckAt()).isNull();
        assertThat(delivery.getEvents()).extracting(e -> e.getName()).contains("DROPSHIP_TRACKING_APPLIED");
        verify(deliveriesRepository, never()).save(any());
    }

    @Test
    void manualCheckReportsCancellationAndNoData() {
        // given
        when(provider.trackOrder(any()))
                .thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.CANCELLED, List.of())))
                .thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of())));

        // when
        ManualTrackingOutcome cancelled = service.checkManually(STORE_ID, delivery.getDeliveryId());
        ManualTrackingOutcome noData = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(cancelled).isEqualTo(ManualTrackingOutcome.CANCELLED);
        assertThat(noData).isEqualTo(ManualTrackingOutcome.NO_DATA);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.SHIPPED_WITHOUT_DATA);
    }

    @Test
    void manualCheckIsUnavailableForUnsupportedReceivedOrPendingPurchase() {
        // given
        Delivery unsupported = new Delivery(STORE_ID, "X", "Acme");
        unsupported.setType(DeliveryType.DROPSHIP);
        unsupported.tracking().setState(DeliveryTrackingState.UNSUPPORTED);
        Delivery pending = new Delivery(STORE_ID, "X", "Acme");
        pending.setType(DeliveryType.DROPSHIP);
        pending.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        Delivery received = new Delivery(STORE_ID, "X", "Acme");
        received.setType(DeliveryType.DROPSHIP);
        received.markAsReceived();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, unsupported.getDeliveryId())).thenReturn(unsupported);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, pending.getDeliveryId())).thenReturn(pending);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, received.getDeliveryId())).thenReturn(received);

        // when / then
        assertThat(service.checkManually(STORE_ID, unsupported.getDeliveryId())).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
        assertThat(service.checkManually(STORE_ID, pending.getDeliveryId())).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
        assertThat(service.checkManually(STORE_ID, received.getDeliveryId())).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
        assertThat(service.checkManually(STORE_ID, "missing")).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
        verify(provider, never()).trackOrder(any());
    }

    @Test
    void manualErrorDoesNotCountTowardsGivingUp() {
        // given
        delivery.tracking().setConsecutiveErrors(4);
        when(provider.trackOrder(any())).thenThrow(new SupplierOrderException("timeout"));

        // when
        ManualTrackingOutcome outcome = service.checkManually(STORE_ID, delivery.getDeliveryId());

        // then
        assertThat(outcome).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
        assertThat(delivery.isTrackingPending()).isTrue();
        assertThat(delivery.getTrackingView().getConsecutiveErrors()).isEqualTo(4);
    }

    @Test
    void unexpectedExceptionBecomesUnavailable() {
        // given
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId()))
                .thenThrow(new RuntimeException("dynamo"));

        // when / then
        assertThat(service.checkManually(STORE_ID, delivery.getDeliveryId())).isEqualTo(ManualTrackingOutcome.UNAVAILABLE);
    }
}
