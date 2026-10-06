package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImmediatePickupTest {

    private static final PickupWindow FIRST =
            new PickupWindow(LocalDate.of(2026, 10, 6), LocalTime.of(14, 0), LocalTime.of(17, 0), "a");

    @Mock private StoresRepository storesRepository;
    @Mock private ShipmentPickupService pickupService;
    @Mock private ShipmentOwners owners;
    @Mock private ShipmentOwner owner;
    @Mock private Store store;

    private ImmediatePickup immediate;

    @BeforeEach
    void setUp() {
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(owners.get(ShipmentOwnerType.RMA_RETURN)).thenReturn(owner);
        when(owner.applyPickup(anyString(), any(), anyCollection(), any())).thenReturn(1);
        immediate = new ImmediatePickup(storesRepository, pickupService, owners);
    }

    private static ShipmentCreationCheckRequest creation() {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.RMA_RETURN)
                .ownerId("rma-1").commandId("cmd-1").externalId("21480003").provider("furgonetka").build();
    }

    private static Shipment created(ShipmentPickup pickup) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId("21480003");
        s.setTrackingNo("A");
        s.setPickup(pickup);
        return s;
    }

    @Test
    void theFirstWindowOfTheNextThreeDaysIsOrdered() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of(FIRST,
                new PickupWindow(LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), LocalTime.of(17, 0), "b")));
        when(pickupService.order(any(), any(), any(), any())).thenReturn(PickupStart.started());

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(pickupService).order(eq(store), eq("furgonetka"),
                argThat(t -> t.size() == 1 && "21480003".equals(t.get(0).externalId())
                        && "rma-1".equals(t.get(0).ownerId()) && "A".equals(t.get(0).trackingNo())), eq(FIRST));
        verify(owner, never()).onPickupSettled(any(), any(), any(), any());
    }

    @Test
    void noWindowsMeansHandingInAtAPointAndTheOwnerIsTold() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of());

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner).applyPickup(eq("store-1"), eq("rma-1"), eq(List.of("21480003")), any());
        verify(owner).onPickupSettled(eq("store-1"), eq("furgonetka"), argThat(t -> "21480003".equals(t.externalId())),
                argThat(p -> !p.isAwaiting() && !p.isPending()));
        verify(pickupService, never()).order(any(), any(), any(), any());
    }

    @Test
    void handingInAtAPointChangesOnlyAPickupThatStillWaits() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of());

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner).applyPickup(eq("store-1"), eq("rma-1"), eq(List.of("21480003")), argThat(change -> {
            @SuppressWarnings("unchecked")
            UnaryOperator<ShipmentPickup> c = (UnaryOperator<ShipmentPickup>) change;
            ShipmentPickup ordered = ShipmentPickup.awaiting().ordered("p-1");
            return !c.apply(ShipmentPickup.awaiting()).isAwaiting() && c.apply(ordered) == ordered;
        }));
    }

    @Test
    void aPackageThatNeedsNoPickupIsSettledAtOnce() {
        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.notRequired())));

        // then
        verify(owner).onPickupSettled(eq("store-1"), eq("furgonetka"), any(), argThat(p -> !p.isAwaiting()));
        verifyNoInteractions(pickupService);
    }

    @Test
    void aRefusedPickupIsReportedWithItsCommand() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of(FIRST));
        when(pickupService.order(any(), any(), any(), any())).thenReturn(PickupStart.refused("pick-1", "Brak kuriera"));

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner).onPickupSettled(eq("store-1"), eq("furgonetka"), argThat(t -> "21480003".equals(t.externalId())),
                argThat(p -> p.isFailed() && "pick-1".equals(p.getCommandId()) && "Brak kuriera".equals(p.getError())
                        && "2026-10-06".equals(p.getDate())));
    }

    @Test
    void windowsThatCannotBeReadFailThePickup() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3))
                .thenThrow(new RuntimeException("Furgonetka down"));

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner).applyPickup(eq("store-1"), eq("rma-1"), eq(List.of("21480003")), any());
        verify(owner).onPickupSettled(eq("store-1"), eq("furgonetka"), any(), argThat(ShipmentPickup::isFailed));
        verify(pickupService, never()).order(any(), any(), any(), any());
    }

    @Test
    void anOrderThatBreaksOffIsReportedAsNotSent() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of(FIRST));
        when(pickupService.order(any(), any(), any(), any())).thenThrow(new RuntimeException("dynamo down"));

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner).onPickupSettled(eq("store-1"), eq("furgonetka"), any(), argThat(p -> p.isFailed()
                && ShipmentPickupService.NOT_SENT_KEY.equals(p.getErrorKey())));
    }

    @Test
    void aStartedPickupIsLeftToItsCheck() {
        // given
        when(pickupService.windows(store, "furgonetka", List.of("21480003"), 3)).thenReturn(List.of(FIRST));
        when(pickupService.order(any(), any(), any(), any())).thenReturn(PickupStart.gone());

        // when
        immediate.orderFor(creation(), List.of(created(ShipmentPickup.awaiting())));

        // then
        verify(owner, never()).onPickupSettled(any(), any(), any(), any());
        assertThat(ImmediatePickup.DAYS_AHEAD).isEqualTo(3);
    }
}
