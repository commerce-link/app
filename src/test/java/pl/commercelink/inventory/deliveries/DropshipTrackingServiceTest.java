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
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.OrdersRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        givenUp.setTrackingState(DeliveryTrackingState.GIVEN_UP);
        Delivery notDue = trackableDelivery();
        notDue.setTrackingNextCheckAt(NOW.plusMinutes(5));
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
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.UNSUPPORTED);
        assertThat(delivery.getTrackingNextCheckAt()).isNull();
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
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(delivery.getTrackingLastCheckedAt()).isEqualTo(NOW);
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(delivery.getTrackingAttempts()).isEqualTo(1);
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
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusHours(2));
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
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
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
        assertThat(delivery.getTrackingState()).isNull();
        assertThat(delivery.getTrackingConsecutiveErrors()).isEqualTo(1);
        assertThat(delivery.getTrackingLastError()).isEqualTo("timeout");
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        verify(deliveriesRepository).save(delivery);
    }

    @Test
    void consecutiveErrorsReachingLimitGiveUp() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.setTrackingConsecutiveErrors(4);
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenThrow(new SupplierOrderException("timeout"));

        // when
        TrackingOutcome outcome = service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(outcome).isEqualTo(TrackingOutcome.GIVEN_UP);
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
        assertThat(delivery.getTrackingConsecutiveErrors()).isEqualTo(5);
    }

    @Test
    void successfulCheckResetsErrorCounter() {
        // given
        Delivery delivery = trackableDelivery();
        delivery.setTrackingConsecutiveErrors(3);
        delivery.setTrackingLastError("old");
        givenDelivery(delivery);
        givenSupportingProvider();
        when(provider.trackOrder(any())).thenReturn(Optional.empty());

        // when
        service.check(STORE_ID, delivery.getDeliveryId(), false);

        // then
        assertThat(delivery.getTrackingConsecutiveErrors()).isZero();
        assertThat(delivery.getTrackingLastError()).isNull();
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
        assertThat(delivery.getTrackingLastError()).isEqualTo("no secret");
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
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.CANCELLED_BY_SUPPLIER);
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
        assertThat(delivery.getTrackingState()).isEqualTo(DeliveryTrackingState.SHIPPED_WITHOUT_DATA);
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
        assertThat(delivery.getTrackingNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
        verify(completion, never()).confirmShipped(any(), any(), anyList(), anyList(), any());
    }
}
