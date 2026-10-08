package pl.commercelink.shipping.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.shipping.api.TrackingEvent;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentTrackingPollerTest {

    private static final ShipmentTrackingPollRequest REQUEST = new ShipmentTrackingPollRequest("store-1", "AD1", "allegro");
    private static final OffsetDateTime COLLECTED_AT = OffsetDateTime.of(2026, 10, 8, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DELIVERED_AT = OffsetDateTime.of(2026, 10, 9, 9, 30, 0, 0, ZoneOffset.UTC);

    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviders shippingProviders;
    @Mock private ShipmentTrackingsRepository shipmentTrackingsRepository;
    @Mock private ShipmentTrackingUpdates shipmentTrackingUpdates;
    @Mock private Store store;
    @Mock private ShippingProvider allegro;

    @InjectMocks
    private ShipmentTrackingPoller poller;

    private ShipmentTracking row;

    @BeforeEach
    void setUp() {
        row = new ShipmentTracking("store-1", "AD1", "order-1", null, LocalDateTime.now().minusDays(1), "allegro", "shp-1");
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shipmentTrackingsRepository.find("store-1", "AD1")).thenReturn(Optional.of(row));
        when(shippingProviders.forName(store, "allegro")).thenReturn(Optional.of(allegro));
    }

    private static LocalDateTime local(OffsetDateTime at) {
        return at.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Test
    void deliveredWinsOverCollectedWhateverTheOrderOfEvents() {
        // given
        when(allegro.getTrackingEvents("shp-1")).thenReturn(List.of(
                new TrackingEvent("DELIVERED", "DELIVERED", DELIVERED_AT),
                new TrackingEvent("COLLECTED", "IN_TRANSIT", COLLECTED_AT)));

        // when
        poller.poll(REQUEST);

        // then
        verify(shipmentTrackingUpdates).apply("store-1", "AD1", ShipmentTrackingState.DELIVERED, local(DELIVERED_AT));
        verify(shipmentTrackingsRepository).markPolled(eq(row), any());
    }

    @Test
    void collectedIsAppliedWithTheCarrierTime() {
        // given
        when(allegro.getTrackingEvents("shp-1")).thenReturn(List.of(new TrackingEvent("COLLECTED", "IN_TRANSIT", COLLECTED_AT)));

        // when
        poller.poll(REQUEST);

        // then
        verify(shipmentTrackingUpdates).apply("store-1", "AD1", ShipmentTrackingState.COLLECTED, local(COLLECTED_AT));
    }

    @Test
    void noEventsOnlyRemembersThePoll() {
        // given: the sandbox and fresh parcels answer with trackingDetails: null
        when(allegro.getTrackingEvents("shp-1")).thenReturn(List.of());

        // when
        poller.poll(REQUEST);

        // then
        verifyNoInteractions(shipmentTrackingUpdates);
        verify(shipmentTrackingsRepository).markPolled(eq(row), any());
    }

    @Test
    void integrationErrorIsLoggedAndTheMessageIsNotRetried() {
        // given
        when(allegro.getTrackingEvents("shp-1")).thenThrow(new IllegalStateException("503 from Allegro"));

        // when / then: no exception reaches the listener, so SQS deletes the message
        assertThatCode(() -> poller.poll(REQUEST)).doesNotThrowAnyException();
        verifyNoInteractions(shipmentTrackingUpdates);
        verify(shipmentTrackingsRepository, never()).markPolled(any(), any());
    }

    @Test
    void disconnectedIntegrationIsSkipped() {
        // given
        when(shippingProviders.forName(store, "allegro")).thenReturn(Optional.empty());

        // when
        poller.poll(REQUEST);

        // then
        verifyNoInteractions(allegro, shipmentTrackingUpdates);
    }

    @Test
    void parcelThatReachedAFinalStateMeanwhileIsNotAskedAgain() {
        // given
        row.setState("DELIVERED");

        // when
        poller.poll(REQUEST);

        // then
        verifyNoInteractions(allegro, shipmentTrackingUpdates);
    }

    @Test
    void unknownEventStatesAreIgnored() {
        // given
        when(allegro.getTrackingEvents("shp-1")).thenReturn(List.of(new TrackingEvent("OTHER", "ISSUE", DELIVERED_AT)));

        // when
        poller.poll(REQUEST);

        // then
        verifyNoInteractions(shipmentTrackingUpdates);
    }

    @Test
    void failedEffectsAreLoggedWithoutMarkingThePollOrRetryingThroughTheQueue() {
        // given
        when(allegro.getTrackingEvents("shp-1")).thenReturn(List.of(new TrackingEvent("DELIVERED", "DELIVERED", DELIVERED_AT)));
        doThrow(new IllegalStateException("effects failed")).when(shipmentTrackingUpdates)
                .apply("store-1", "AD1", ShipmentTrackingState.DELIVERED, local(DELIVERED_AT));

        // when / then
        assertThatCode(() -> poller.poll(REQUEST)).doesNotThrowAnyException();
        verify(shipmentTrackingsRepository, never()).markPolled(any(), any());
    }
}
