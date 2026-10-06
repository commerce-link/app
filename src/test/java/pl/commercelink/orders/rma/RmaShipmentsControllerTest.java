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
import pl.commercelink.shipping.ShipmentsState;
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
        controller = new RmaShipmentsController(rmaRepository, optimisticLockingExecutor, messages) {
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
}
