package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RmaShipmentOwnerTest {

    @Mock private RMARepository rmaRepository;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;

    private RMA rma;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        when(rmaRepository.findById("store-1", "rma-1")).thenAnswer(i -> rma);
    }

    private static ShipmentCreationCheckRequest request(String commandId) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.RMA)
                .ownerId("rma-1").commandId(commandId).externalId("21480003").build();
    }

    private static Shipment placeholder(String commandId) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setCreation(ShipmentCreationState.pending(commandId, LocalDateTime.now()));
        return s;
    }

    @Test
    void theCreatingShipmentReplacesTheEarlierOnes() {
        // given
        Shipment earlier = new Shipment(ShipmentType.Courier);
        earlier.setTrackingNo("T-0");
        rma.setShipments(new ArrayList<>(List.of(earlier)));
        RmaShipmentOwner owner = new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor);

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isTrue();
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).isCreationPendingFor("cmd-1")).isTrue();
        verify(rmaRepository).save(rma);
    }

    @Test
    void anRmaWithAShipmentBeingCreatedIsNotSentASecondCommand() {
        // given: replacing the list would lose the first command's placeholder and with it a paid label
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-0"))));
        RmaShipmentOwner owner = new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor);

        // when
        boolean marked = owner.markCreating(request("cmd-1"), placeholder("cmd-1"));

        // then
        assertThat(marked).isFalse();
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).isCreationPendingFor("cmd-0")).isTrue();
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void anOperatorsRefusedShipmentStaysAsFailed() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RmaShipmentOwner owner = new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor);

        // when
        owner.refused(request("cmd-1"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).hasSize(1);
        assertThat(rma.getShipments().get(0).getCreation().getError()).isEqualTo("Nieprawidłowy kod pocztowy");
    }

    @Test
    void aCustomersRefusedReturnLeavesNothingWaitingOnTheRma() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-1"))));
        RmaShipmentOwner owner = new RmaReturnShipmentOwner(rmaRepository, optimisticLockingExecutor);

        // when
        owner.refused(request("cmd-1"), "Nieprawidłowy kod pocztowy");

        // then
        assertThat(rma.getShipments()).isEmpty();
        verify(rmaRepository).save(rma);
    }

    @Test
    void aWriteForAnotherCommandChangesNothing() {
        // given
        rma.setShipments(new ArrayList<>(List.of(placeholder("cmd-2"))));
        RmaShipmentOwner owner = new RmaShipmentOwner(rmaRepository, optimisticLockingExecutor);

        // when
        owner.recordExternalId(request("cmd-1"));

        // then
        assertThat(rma.getShipments().get(0).getExternalId()).isNull();
        verify(rmaRepository, never()).save(any());
    }
}
