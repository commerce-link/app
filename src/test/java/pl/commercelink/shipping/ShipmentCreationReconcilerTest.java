package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.ShipmentCreation;
import pl.commercelink.shipping.api.ShipmentResult;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCreationReconcilerTest {

    @Mock private ShippingProviders shippingProviders;
    @Mock private ShipmentCreationSettler settler;
    @Mock private ShippingProvider allegro;

    @InjectMocks
    private ShipmentCreationReconciler reconciler;

    private final Store store = new Store();
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order("store-1");
        order.setOrderId("order-1");
        when(shippingProviders.forShipment(eq(store), any())).thenReturn(Optional.of(allegro));
    }

    private Shipment row(ShipmentCreationState creation) {
        Shipment shipment = new Shipment(ShipmentType.PickupPoint);
        shipment.setProvider("allegro");
        shipment.setPickUpAddressId("addr-1");
        shipment.setCreation(creation);
        order.setShipments(new ArrayList<>(List.of(shipment)));
        return shipment;
    }

    private static ShipmentCreationState overdue() {
        return ShipmentCreationState.pending("cmd-0", LocalDateTime.now().minusMinutes(11));
    }

    private static ShipmentCreationState unconfirmed(String key) {
        return overdue().failedWithKey(key);
    }

    private static ShipmentResult created() {
        return new ShipmentResult("shp-9", List.of(
                new ShipmentResult.ShipmentParcelResult("AD058PZBBXLXNJ5TZ", "DPD", null, true, null, false)), null);
    }

    @Test
    void aCommandFoundCreatedIsSettledAndReported() {
        // given: the app died during the POST; Allegro created the shipment and attached the waybill
        Shipment stuck = row(overdue());
        when(allegro.checkShipmentCreation("cmd-0", null)).thenReturn(ShipmentCreation.succeeded("cmd-0", created()));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).contains(stuck);
        verify(settler).succeeded(argThat(r -> "shp-9".equals(r.getExternalId()) && "cmd-0".equals(r.getCommandId())
                && r.getOwnerType() == ShipmentOwnerType.ORDER && "order-1".equals(r.getOwnerId())
                && "store-1".equals(r.getStoreId()) && "allegro".equals(r.getProvider())
                && "addr-1".equals(r.getPickUpAddressId())), eq(created()));
    }

    @Test
    void aCommandSettledAsNeverConfirmedIsAskedWithThePackageIdItKept() {
        // given
        Shipment stuck = row(unconfirmed(ShipmentCreationState.UNCONFIRMED_KEY));
        stuck.setExternalId("shp-9");
        when(allegro.checkShipmentCreation("cmd-0", "shp-9")).thenReturn(ShipmentCreation.succeeded("cmd-0", created()));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).contains(stuck);
        verify(settler).succeeded(any(), eq(created()));
    }

    @Test
    void aCommandFoundRefusedIsSettledAsFailedAndLetsTheBookingGoOn() {
        // given
        row(unconfirmed(ShipmentCreationState.UNCONFIRMED_DISCONNECTED_KEY));
        when(allegro.checkShipmentCreation("cmd-0", null))
                .thenReturn(ShipmentCreation.failed("cmd-0", null, "Nieprawidłowy kod pocztowy"));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).isEmpty();
        verify(settler).failed(argThat(r -> "cmd-0".equals(r.getCommandId())), eq("Nieprawidłowy kod pocztowy"));
        verify(settler, never()).succeeded(any(), any());
    }

    @Test
    void aCommandStillPendingChangesNothing() {
        // given
        row(overdue());
        when(allegro.checkShipmentCreation("cmd-0", null)).thenReturn(ShipmentCreation.pending("cmd-0", "shp-9"));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(settler);
    }

    @Test
    void aCheckThatFailsChangesNothing() {
        // given
        row(overdue());
        when(allegro.checkShipmentCreation("cmd-0", null)).thenThrow(new ShippingException("HTTP 502"));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(settler);
    }

    @Test
    void aShipmentFoundCreatedIsReportedEvenWhenSavingItFails() {
        // given: the provider holds a paid shipment whether or not the order got its number
        Shipment stuck = row(overdue());
        when(allegro.checkShipmentCreation("cmd-0", null)).thenReturn(ShipmentCreation.succeeded("cmd-0", created()));
        doThrow(new IllegalStateException("throttled")).when(settler).succeeded(any(), any());

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).contains(stuck);
    }

    @Test
    void aCommandWhoseIntegrationIsGoneIsNotChecked() {
        // given
        row(overdue());
        when(shippingProviders.forShipment(eq(store), any())).thenReturn(Optional.empty());

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(allegro, settler);
    }

    @Test
    void creationsWithAKnownOutcomeOrStillInProgressAreNotChecked() {
        // given: one in progress, one refused by the provider, one created
        Shipment inProgress = new Shipment(ShipmentType.Courier);
        inProgress.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        Shipment refused = new Shipment(ShipmentType.Courier);
        refused.setCreation(overdue().failed("Nieprawidłowy kod pocztowy"));
        Shipment done = new Shipment(ShipmentType.Courier);
        done.setExternalId("shp-1");
        order.setShipments(new ArrayList<>(List.of(inProgress, refused, done)));

        // when
        Optional<Shipment> result = reconciler.reconcile(store, order);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(allegro, settler);
    }
}
