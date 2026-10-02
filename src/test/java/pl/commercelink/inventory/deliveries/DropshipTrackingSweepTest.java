package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipTrackingSweepTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DropshipTrackingEventPublisher publisher;

    private DropshipTrackingSweep sweep;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        sweep = new DropshipTrackingSweep(storesRepository, deliveriesRepository, publisher,
                DropshipTrackingProperties.defaults(), clock);
    }

    private static Delivery delivery(String id, LocalDateTime orderedAt, LocalDateTime nextCheckAt) {
        Delivery delivery = new Delivery("store-1", "ACME-DS-" + id, "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setOrderedAt(orderedAt);
        delivery.tracking().setNextCheckAt(nextCheckAt);
        return delivery;
    }

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void publishesOneMessagePerDueDelivery() {
        // given
        Delivery due = delivery("due", NOW.minusHours(2), NOW.minusMinutes(1));
        Delivery neverChecked = delivery("fresh", NOW.minusHours(2), null);
        Delivery notYet = delivery("later", NOW.minusHours(2), NOW.plusMinutes(5));
        Delivery tooYoung = delivery("young", NOW.minusMinutes(10), null);
        when(storesRepository.findAll()).thenReturn(List.of(store("store-1")));
        when(deliveriesRepository.findTrackableDropshipDeliveries("store-1"))
                .thenReturn(List.of(due, neverChecked, notYet, tooYoung));

        // when
        sweep.sweep();

        // then
        ArgumentCaptor<DropshipTrackingEventRequest> captor = ArgumentCaptor.forClass(DropshipTrackingEventRequest.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(DropshipTrackingEventRequest::getDeliveryId)
                .containsExactly(due.getDeliveryId(), neverChecked.getDeliveryId());
        assertThat(captor.getAllValues().get(0).getStoreId()).isEqualTo("store-1");
        assertThat(captor.getAllValues().get(0).getExternalDeliveryId()).isEqualTo("ACME-DS-due");
    }

    @Test
    void oneFailingStoreDoesNotStopTheOthers() {
        // given
        Delivery due = delivery("due", NOW.minusHours(2), null);
        when(storesRepository.findAll()).thenReturn(List.of(store("broken"), store("store-1")));
        when(deliveriesRepository.findTrackableDropshipDeliveries("broken")).thenThrow(new RuntimeException("dynamo down"));
        when(deliveriesRepository.findTrackableDropshipDeliveries("store-1")).thenReturn(List.of(due));

        // when
        sweep.sweep();

        // then
        verify(publisher).publish(any());
    }

    @Test
    void deliveryWithoutOrderedAtIsNeverDue() {
        // given
        Delivery delivery = delivery("x", null, null);

        // when / then
        assertThat(sweep.isDue(delivery, NOW)).isFalse();
        verify(publisher, never()).publish(any());
    }
}
