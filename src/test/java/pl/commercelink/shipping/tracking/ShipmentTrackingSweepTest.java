package pl.commercelink.shipping.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.shipping.ShipmentTracking;
import pl.commercelink.shipping.ShipmentTrackingsRepository;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentTrackingSweepTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 12, 5);

    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviders shippingProviders;
    @Mock private ShipmentTrackingsRepository shipmentTrackingsRepository;
    @Mock private ShipmentTrackingUpdates shipmentTrackingUpdates;
    @Mock private ShipmentTrackingPollPublisher publisher;
    @Mock private Store store;
    @Mock private ShippingProvider allegro;
    @Mock private ShippingProvider furgonetka;

    private ShipmentTrackingSweep sweep;

    @BeforeEach
    void setUp() {
        when(store.getStoreId()).thenReturn("store-1");
        when(store.shippingIntegrationNames()).thenReturn(List.of("furgonetka", "allegro"));
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(allegro.supportsTrackingPolling()).thenReturn(true);
        when(furgonetka.supportsTrackingPolling()).thenReturn(false);
        when(shippingProviders.forName(store, "allegro")).thenReturn(Optional.of(allegro));
        when(shippingProviders.forName(store, "furgonetka")).thenReturn(Optional.of(furgonetka));
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        sweep = new ShipmentTrackingSweep(storesRepository, shippingProviders, shipmentTrackingsRepository,
                shipmentTrackingUpdates, publisher, clock);
    }

    private static ShipmentTracking row(String trackingNo, String provider, String externalId, LocalDateTime createdAt) {
        return new ShipmentTracking("store-1", trackingNo, "order-1", null, createdAt, provider, externalId);
    }

    @Test
    void publishesOnePollPerParcelOfAPollingIntegration() {
        // given
        when(shipmentTrackingsRepository.findByStore("store-1"))
                .thenReturn(List.of(row("AD1", "allegro", "shp-1", NOW.minusDays(1))));

        // when
        sweep.sweep();

        // then
        verify(publisher).publish(new ShipmentTrackingPollRequest("store-1", "AD1", "allegro"));
    }

    @Test
    void skipsParcelsOfIntegrationsWithWebhooksAndForeignNumbers() {
        // given
        when(shipmentTrackingsRepository.findByStore("store-1")).thenReturn(List.of(
                row("FURG-1", "furgonetka", "pkg-1", NOW.minusDays(1)),
                row("TYPED-1", "furgonetka", null, NOW.minusDays(1)),
                row("OLD-1", null, null, NOW.minusDays(1)),
                row("AD-NOID", "allegro", null, NOW.minusDays(1))));

        // when
        sweep.sweep();

        // then
        verifyNoInteractions(publisher, shipmentTrackingUpdates);
    }

    @Test
    void skipsParcelsPolledLessThanFiftyMinutesAgo() {
        // given
        ShipmentTracking recent = row("AD1", "allegro", "shp-1", NOW.minusDays(1));
        recent.setLastPolledAt(NOW.minusMinutes(49));
        ShipmentTracking due = row("AD2", "allegro", "shp-2", NOW.minusDays(1));
        due.setLastPolledAt(NOW.minusMinutes(51));
        when(shipmentTrackingsRepository.findByStore("store-1")).thenReturn(List.of(recent, due));

        // when
        sweep.sweep();

        // then
        verify(publisher, never()).publish(new ShipmentTrackingPollRequest("store-1", "AD1", "allegro"));
        verify(publisher).publish(new ShipmentTrackingPollRequest("store-1", "AD2", "allegro"));
    }

    @Test
    void skipsDeliveredAndExpiredParcelsButKeepsCollectedOnes() {
        // given
        ShipmentTracking delivered = row("AD1", "allegro", "shp-1", NOW.minusDays(1));
        delivered.setState("DELIVERED");
        ShipmentTracking expired = row("AD2", "allegro", "shp-2", NOW.minusDays(1));
        expired.setState("EXPIRED");
        ShipmentTracking collected = row("AD3", "allegro", "shp-3", NOW.minusDays(1));
        collected.setState("COLLECTED");
        when(shipmentTrackingsRepository.findByStore("store-1")).thenReturn(List.of(delivered, expired, collected));

        // when
        sweep.sweep();

        // then
        verify(publisher).publish(new ShipmentTrackingPollRequest("store-1", "AD3", "allegro"));
        verify(publisher, never()).publish(new ShipmentTrackingPollRequest("store-1", "AD1", "allegro"));
        verify(publisher, never()).publish(new ShipmentTrackingPollRequest("store-1", "AD2", "allegro"));
    }

    @Test
    void parcelWithoutDeliveryAfterThirtyDaysExpiresInsteadOfBeingPolled() {
        // given
        when(shipmentTrackingsRepository.findByStore("store-1"))
                .thenReturn(List.of(row("AD1", "allegro", "shp-1", NOW.minusDays(30).minusMinutes(1))));

        // when
        sweep.sweep();

        // then
        verify(shipmentTrackingUpdates).apply("store-1", "AD1", ShipmentTrackingState.EXPIRED, NOW);
        verifyNoInteractions(publisher);
    }

    @Test
    void storeWithoutPollingIntegrationIsNotRead() {
        // given
        when(store.shippingIntegrationNames()).thenReturn(List.of("furgonetka"));

        // when
        sweep.sweep();

        // then
        verify(shipmentTrackingsRepository, never()).findByStore(anyString());
    }

    @Test
    void aFailingStoreDoesNotStopTheOthers() {
        // given
        Store broken = mock(Store.class);
        when(broken.getStoreId()).thenReturn("store-0");
        when(broken.shippingIntegrationNames()).thenThrow(new IllegalStateException("broken"));
        when(storesRepository.findAll()).thenReturn(List.of(broken, store));
        when(shipmentTrackingsRepository.findByStore("store-1"))
                .thenReturn(List.of(row("AD1", "allegro", "shp-1", NOW.minusDays(1))));

        // when
        sweep.sweep();

        // then
        verify(publisher).publish(any());
    }
}
