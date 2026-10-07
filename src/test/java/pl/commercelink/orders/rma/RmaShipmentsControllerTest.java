package pl.commercelink.orders.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.shipping.ShipmentsState;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import org.mockito.ArgumentCaptor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaShipmentsControllerTest {

    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock private RMARepository rmaRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private RMAShippingService rmaShippingService;
    @Mock private StoresRepository storesRepository;
    @Mock private Store store;

    private RmaShipmentsController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        doAnswer(invocation -> {
            Object fresh = ((Supplier<Object>) invocation.getArgument(0)).get();
            ((Consumer<Object>) invocation.getArgument(1)).accept(fresh);
            ((Consumer<Object>) invocation.getArgument(2)).accept(fresh);
            return fresh;
        }).when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        when(storesRepository.findById("store-1")).thenReturn(store);
        controller = new RmaShipmentsController(rmaRepository, optimisticLockingExecutor, messages, rmaShippingService,
                storesRepository) {
            @Override
            String storeId() {
                return "store-1";
            }
        };
    }

    private RMA rmaWith(Shipment... shipments) {
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(RMAStatus.Approved);
        rma.setShipments(new ArrayList<>(List.of(shipments)));
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        return rma;
    }

    private static Shipment creation(String commandId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setCreation(ShipmentCreationState.pending(commandId, LocalDateTime.now()));
        return shipment;
    }

    @Test
    void theStateSaysWhetherAShipmentStillWaitsForTheProvider() {
        // given
        rmaWith(creation("cmd-1"));

        // when
        ResponseEntity<ShipmentsState> response = controller.shipmentsState("rma-1");

        // then
        assertThat(response.getBody().inProgress()).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void theStateOfAnUnknownRmaIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.shipmentsState("rma-1")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aFailedCreationIsRemoved() {
        // given
        Shipment failed = creation("cmd-1");
        failed.setCreation(failed.getCreation().failed("Brak środków"));
        Shipment other = new Shipment(ShipmentType.Courier);
        other.setTrackingNo("T-1");
        RMA rma = rmaWith(failed, other);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.removeFailedCreation("rma-1", "cmd-1", redirect, PL);

        // then
        assertThat(rma.getShipments()).containsExactly(other);
        verify(rmaRepository).save(rma);
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("rma.shipments.removed");
    }

    @Test
    void aShipmentStillBeingCreatedIsNotRemoved() {
        // given: its result still comes, and a paid label would be lost without the row waiting for it
        RMA rma = rmaWith(creation("cmd-1"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.removeFailedCreation("rma-1", "cmd-1", redirect, PL);

        // then
        assertThat(rma.getShipments()).hasSize(1);
        verify(rmaRepository, never()).save(any());
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("rma.shipments.remove.gone");
    }

    private RMA failedReturn() {
        Shipment failed = creation("cmd-1");
        failed.setCreation(failed.getCreation().failed("Nieprawidłowy kod pocztowy"));
        RMA rma = rmaWith(failed);
        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setShippingDetails(ShippingDetails._default());
        rma.setReturnPackageTemplateId("7");
        rma.setShippingInsurance(1797);
        return rma;
    }

    @Test
    void aFailedReturnIsBookedAgainWithWhatTheCustomerChose() {
        // given
        RMA rma = failedReturn();
        when(rmaShippingService.retryReturnShipment(any(), eq(store)))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.retryReturnShipment("rma-1", redirect, PL);

        // then
        ArgumentCaptor<RMAShipmentRequest> request = ArgumentCaptor.forClass(RMAShipmentRequest.class);
        verify(rmaShippingService).retryReturnShipment(request.capture(), eq(store));
        assertThat(request.getValue().getRmaId()).isEqualTo("rma-1");
        assertThat(request.getValue().getPackageTemplateId()).isEqualTo("7");
        assertThat(request.getValue().getCustomerAddress()).isSameAs(rma.getShippingDetails());
        assertThat(request.getValue().getInsuranceValue()).isEqualTo(1797);
        assertThat(view).isEqualTo("redirect:/dashboard/rma/rma-1");
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("shipping.create.started");
    }

    @Test
    void aRefusedRetryShowsTheProvidersReason() {
        // given
        failedReturn();
        when(rmaShippingService.retryReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED, "Zły kod pocztowy"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.retryReturnShipment("rma-1", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Zły kod pocztowy");
    }

    @Test
    void aRetryThatFindsTheRmaChangedSaysSoNeutrally() {
        // given
        failedReturn();
        when(rmaShippingService.retryReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.GONE, null));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.retryReturnShipment("rma-1", redirect, PL);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("rma.shipments.return.retry.gone");
    }

    @Test
    void noRetryWithoutAFailedReturn() {
        // given: the return is still being created
        RMA rma = failedReturn();
        rma.getShipments().set(0, creation("cmd-2"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.retryReturnShipment("rma-1", redirect, PL);

        // then
        verify(rmaShippingService, never()).retryReturnShipment(any(), any());
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("rma.shipments.return.retry.unavailable");
    }

    @Test
    void noRetryWhileAnotherShipmentIsBeingCreated() {
        // given
        RMA rma = failedReturn();
        rma.getShipments().add(creation("cmd-2"));

        // when
        controller.retryReturnShipment("rma-1", new RedirectAttributesModelMap(), PL);

        // then
        verify(rmaShippingService, never()).retryReturnShipment(any(), any());
    }

    @Test
    void noRetryForAnRmaWithoutTheCustomersPackageTemplate() {
        // given: submitted before the template was kept on the RMA
        failedReturn().setReturnPackageTemplateId(null);

        // when
        controller.retryReturnShipment("rma-1", new RedirectAttributesModelMap(), PL);

        // then
        verify(rmaShippingService, never()).retryReturnShipment(any(), any());
    }

    @Test
    void noRetryForAFailedOperatorShipment() {
        // given: the operator's shipment has a pickup address; it is shipped again from the item list
        failedReturn().getShipments().get(0).setPickUpAddressId("addr-1");

        // when
        controller.retryReturnShipment("rma-1", new RedirectAttributesModelMap(), PL);

        // then
        verify(rmaShippingService, never()).retryReturnShipment(any(), any());
    }

    @Test
    void anotherStoresRmaIsNotFound() {
        // given: the RMA exists only under another store; the session's store finds nothing
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.retryReturnShipment("rma-1", new RedirectAttributesModelMap(), PL))
                .isInstanceOf(ResponseStatusException.class);
        verify(rmaShippingService, never()).retryReturnShipment(any(), any());
    }
}
