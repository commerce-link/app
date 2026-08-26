package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DropshipTrackingService.TrackingOutcome;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLookup;
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
import java.time.Duration;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipTrackingServiceTest {

    static final String STORE_ID = "store-1";
    static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    DeliveriesQueryService deliveriesQueryService;
    @Mock
    DeliveriesRepository deliveriesRepository;
    @Mock
    OrdersRepository ordersRepository;
    @Mock
    SupplierProviderResolver providerResolver;
    @Mock
    DropshipDeliveryCompletion completion;
    @Mock
    SupplierProvider provider;

    DropshipTrackingProperties properties = DropshipTrackingProperties.defaults();
    DropshipTrackingService service;

    @BeforeEach
    void setUp() {
        service = new DropshipTrackingService(deliveriesQueryService, deliveriesRepository, ordersRepository,
                providerResolver, completion, properties, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    static Delivery trackableDelivery() {
        Delivery delivery = new Delivery(STORE_ID, "ACME-DS-ref-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setPurchaseRef("ref-1");
        delivery.setOrderedAt(NOW.minusHours(2));
        return delivery;
    }

    void givenDelivery(Delivery delivery) {
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
    }

    void givenSupportingProvider() {
        when(providerResolver.resolve(STORE_ID, "Acme")).thenReturn(provider);
        when(provider.supportsOrderTracking()).thenReturn(true);
    }

    static boolean hasEvent(Delivery delivery, String name) {
        return delivery.getEvents().stream().anyMatch(event -> name.equals(event.getName()));
    }

    @Test
    void skipsUnknownDelivery() {
        // given
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, "missing")).thenReturn(null);

        // when
        TrackingOutcome outcome = service.check(STORE_ID, "missing", false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.SKIPPED);
        verifyNoInteractions(providerResolver, deliveriesRepository);
    }

    @Test
    void skipsDeliveryConfirmedManuallyInTheMeantime() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.markAsReceived();
        givenDelivery(delivery);

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.SKIPPED);
        verifyNoInteractions(providerResolver);
    }

    @Test
    void skipsTerminalStateAndNotYetDueDeliveries() {
        // given
        Delivery givenUp = trackableDelivery();
        givenUp.tracking().setState(DeliveryTrackingState.GIVEN_UP);
        Delivery notDue = trackableDelivery();
        notDue.tracking().setNextCheckAt(NOW.plusMinutes(5));
        givenDelivery(givenUp);
        givenDelivery(notDue);

        // when / then
        assertThat(service.check(STORE_ID, givenUp.getDeliveryId(), false)).isEqualTo(TrackingOutcome.SKIPPED);
        assertThat(service.check(STORE_ID, notDue.getDeliveryId(), false)).isEqualTo(TrackingOutcome.SKIPPED);
        verifyNoInteractions(providerResolver);
    }

    @Test
    void marksUnsupportedWhenProviderCannotTrack() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        when(providerResolver.resolve(STORE_ID, "Acme")).thenReturn(provider);
        when(provider.supportsOrderTracking()).thenReturn(false);

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.UNSUPPORTED);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.UNSUPPORTED);
        assertThat(delivery.getTrackingView().getNextCheckAt()).isNull();
        assertThat(hasEvent(delivery, "DROPSHIP_TRACKING_UNSUPPORTED")).isTrue();
        verify(deliveriesRepository).save(delivery);
        verify(provider, never()).trackOrder(any());
    }

    @Test
    void processingSchedulesNextCheckWithinFirstDayInterval() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(new SupplierOrderLookup("ACME-DS-ref-1", "ref-1")))
                .thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.PROCESSING, List.of())));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.PROCESSING);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(delivery.getTrackingView().getLastCheckedAt()).isEqualTo(NOW);
        assertThat(delivery.getTrackingView().getNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(delivery.getTrackingView().getAttempts()).isEqualTo(1);
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void processingAfterFirstDayUsesLaterInterval() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.setOrderedAt(NOW.minusDays(2));
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.empty());

        // when
        service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(delivery.getTrackingView().getNextCheckAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    void givesUpWhenOlderThanMaxAge() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.setOrderedAt(NOW.minusDays(15));
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.empty());

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.GIVEN_UP);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
        assertThat(hasEvent(delivery, "DROPSHIP_TRACKING_GIVEN_UP")).isTrue();
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void communicationErrorIsCountedAndRescheduled() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenThrow(new SupplierOrderException("timeout"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.ERROR);
        assertThat(delivery.isTrackingPending()).isTrue();
        assertThat(delivery.getTrackingView().getConsecutiveErrors()).isEqualTo(1);
        assertThat(delivery.getTrackingView().getLastError()).isEqualTo("timeout");
        assertThat(delivery.getTrackingView().getNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void consecutiveErrorsReachingLimitGiveUp() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.tracking().setConsecutiveErrors(4);
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenThrow(new SupplierOrderException("timeout"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.GIVEN_UP);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
        assertThat(delivery.getTrackingView().getConsecutiveErrors()).isEqualTo(5);
    }

    @Test
    void successfulCheckResetsErrorCounter() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.tracking().setConsecutiveErrors(3);
        delivery.tracking().setLastError("old");
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.empty());

        // when
        service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(delivery.getTrackingView().getConsecutiveErrors()).isZero();
        assertThat(delivery.getTrackingView().getLastError()).isNull();
    }

    @Test
    void unresolvableProviderCountsAsError() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        when(providerResolver.resolve(STORE_ID, "Acme")).thenThrow(new IllegalStateException("no secret"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.ERROR);
        assertThat(delivery.getTrackingView().getLastError()).isEqualTo("no secret");
    }

    @Test
    void supplierCancellationStopsTrackingWithoutTouchingItems() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.CANCELLED, List.of())));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.CANCELLED);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.CANCELLED_BY_SUPPLIER);
        assertThat(hasEvent(delivery, "DROPSHIP_SUPPLIER_CANCELLED")).isTrue();
        verifyNoInteractions(completion);
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void shippedWithoutParcelsAsksForManualConfirmation() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of())));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.NO_DATA);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.SHIPPED_WITHOUT_DATA);
        assertThat(hasEvent(delivery, "DROPSHIP_TRACKING_NO_DATA")).isTrue();
        verifyNoInteractions(completion);
    }

    @Test
    void partiallyShippedWithoutParcelsKeepsPolling() {
        // given
        Delivery delivery = trackableDelivery();
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.PARTIALLY_SHIPPED, List.of())));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.PROCESSING);
        assertThat(delivery.getTrackingView().getNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        verify(completion, never()).confirmShipped(any(), any(), anyList(), anyList(), any());
    }

    @Test
    void appliedParcelPersistsTrackingStateInsideCompletionSave() {
        // given
        Delivery delivery = trackableDelivery();
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        BillingDetails billingDetails = new BillingDetails();
        billingDetails.setEmail("customer@example.com");
        order.setBillingDetails(billingDetails);
        OrderItem item = new OrderItem("order-1", "Category", "Product 1", 1, 100.0, null, false);
        item.setItemId("item-1");
        item.setDeliveryId(delivery.getDeliveryId());
        item.setStatus(FulfilmentStatus.Ordered);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-1");
        delivery.setAllocations(List.of(Allocation.fromOrderItem(order, item)));
        givenDelivery(delivery);
        givenSupportingProvider();
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);
        SupplierParcel parcel = new SupplierParcel("DPD", "PKG-1", null, null, null);
        when(provider.trackOrder(any()))
                .thenReturn(Optional.of(new SupplierOrderTracking(SupplierOrderState.SHIPPED, List.of(parcel))));
        when(completion.confirmShipped(eq(STORE_ID), same(delivery), anyList(), anyList(), any(), any()))
                .thenAnswer(invocation -> {
                    BiConsumer<Delivery, DropshipShipmentResult> beforeSave = invocation.getArgument(5);
                    beforeSave.accept(delivery, DropshipShipmentResult.COMPLETED);
                    return OperationResult.success(DropshipShipmentResult.COMPLETED);
                });

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.APPLIED);
        assertThat(delivery.getTrackingView().getState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(delivery.getTrackingView().getNextCheckAt()).isNull();
        assertThat(hasEvent(delivery, "DROPSHIP_TRACKING_APPLIED")).isTrue();
        verify(deliveriesRepository, never()).save(any());
    }
}
