package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupRetryControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private RMARepository rmaRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private ShippingService shippingService;
    @Mock private ImmediatePickup immediatePickup;
    @Mock private Store store;

    private ShipmentPickupRetryController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingService.providerName(store)).thenReturn("furgonetka");
        controller = new ShipmentPickupRetryController(rmaRepository, storesRepository, shippingService, immediatePickup,
                messages, ShippingIntegrationNamesFixture.names()) {
            @Override
            String storeId() {
                return "store-1";
            }
        };
    }

    private RMA rmaWithReturn(ShipmentPickup pickup) {
        Shipment parcel = new Shipment(ShipmentType.Courier);
        parcel.setProvider("furgonetka");
        parcel.setCarrier("DPD");
        parcel.setExternalId("21480003");
        parcel.setPickup(pickup);
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setShipments(new ArrayList<>(List.of(parcel)));
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        return rma;
    }

    @Test
    void aFailedPickupOfACustomersReturnIsOrderedAgainAtOnce() {
        // given
        RMA rma = rmaWithReturn(ShipmentPickup.awaiting().failed("Brak kuriera"));
        when(immediatePickup.orderFor(any(), anyList())).thenReturn(ImmediatePickup.Outcome.started());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        ArgumentCaptor<ShipmentCreationCheckRequest> request = ArgumentCaptor.forClass(ShipmentCreationCheckRequest.class);
        verify(immediatePickup).orderFor(request.capture(), any());
        assertThat(request.getValue().getOwnerType()).isEqualTo(ShipmentOwnerType.RMA_RETURN);
        assertThat(request.getValue().getOwnerId()).isEqualTo("rma-1");
        assertThat(request.getValue().getStoreId()).isEqualTo("store-1");
        assertThat(request.getValue().getProvider()).isEqualTo("furgonetka");
        assertThat(request.getValue().getExternalId()).isEqualTo("21480003");
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("shipping.pickup.started");
        assertThat(rma.getShipments()).hasSize(1);
    }

    @Test
    void anOrderedPickupIsNotOrderedAgain() {
        // given
        rmaWithReturn(ShipmentPickup.pending("cmd-1", LocalDateTime.now(), LocalDate.of(2026, 10, 8),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).ordered("P-1"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        verify(immediatePickup, never()).orderFor(any(), anyList());
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.pickup.gone");
    }

    @Test
    void aPackageOfTheStoresPickupListIsLeftToThePickupPage() {
        // given
        RMA rma = rmaWithReturn(ShipmentPickup.awaiting().failed("x"));
        rma.getShipments().get(0).setPickUpAddressId("addr-1");

        // when
        controller.orderAgain("rma-1", "21480003", new RedirectAttributesModelMap(), PL);

        // then
        verify(immediatePickup, never()).orderFor(any(), anyList());
    }

    @Test
    void aPackageOfAnotherIntegrationThanTheStoresIsRefused() {
        // given
        rmaWithReturn(ShipmentPickup.awaiting().failed("x"));
        when(shippingService.providerName(store)).thenReturn("allegro");

        // when
        controller.orderAgain("rma-1", "21480003", new RedirectAttributesModelMap(), PL);

        // then
        verify(immediatePickup, never()).orderFor(any(), anyList());
    }

    @Test
    void noWindowsAgainShowTheReasonInsteadOfSuccess() {
        // given
        rmaWithReturn(ShipmentPickup.awaiting().failedWithKey(ImmediatePickup.NO_WINDOWS_KEY));
        when(immediatePickup.orderFor(any(), anyList()))
                .thenReturn(ImmediatePickup.Outcome.failed(null, ImmediatePickup.NO_WINDOWS_KEY));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo(ImmediatePickup.NO_WINDOWS_KEY);
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("successMessage");
    }

    @Test
    void anUnconfirmedPickupNamesTheIntegrationOfTheReturn() {
        // given: the application's own bundles instead of the codes
        ShipmentPickupRetryController withBundles = new ShipmentPickupRetryController(rmaRepository, storesRepository,
                shippingService, immediatePickup, ShippingIntegrationNamesFixture.bundles(),
                ShippingIntegrationNamesFixture.names()) {
            @Override
            String storeId() {
                return "store-1";
            }
        };
        rmaWithReturn(ShipmentPickup.awaiting().failed("x"));
        when(immediatePickup.orderFor(any(), anyList()))
                .thenReturn(ImmediatePickup.Outcome.failed(null, ShipmentPickup.UNCONFIRMED_KEY));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        withBundles.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Integracja wysyłki (Furgonetka) nie "
                + "potwierdziła odbioru — sprawdź go w jej panelu, zanim zamówisz ponownie.");
    }

    @Test
    void windowsThatCannotBeReadShowTheProvidersWords() {
        // given
        rmaWithReturn(ShipmentPickup.awaiting().failed("x"));
        when(immediatePickup.orderFor(any(), anyList()))
                .thenReturn(ImmediatePickup.Outcome.failed("Furgonetka down", null));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Furgonetka down");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("successMessage");
    }

    @Test
    void aPackageThatStoppedWaitingMeanwhileIsGone() {
        // given
        rmaWithReturn(ShipmentPickup.awaiting().failed("x"));
        when(immediatePickup.orderFor(any(), anyList())).thenReturn(ImmediatePickup.Outcome.gone());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.orderAgain("rma-1", "21480003", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("shipping.pickup.gone");
    }
}
