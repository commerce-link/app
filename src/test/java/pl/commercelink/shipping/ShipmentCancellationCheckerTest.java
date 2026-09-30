package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCancellationCheckerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ID = "21353832";
    private static final String COMMAND_ID = "cmd-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private ShippingProviderFactory shippingProviderFactory;
    @Mock
    private ShipmentCancellationEventPublisher publisher;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private Store store;
    @Mock
    private ShippingProvider provider;

    private ShipmentCancellationChecker checker;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        // the real settler: its own write rules are pinned in ShipmentCancellationSettlerTest
        ShipmentCancellationSettler settler =
                new ShipmentCancellationSettler(ordersRepository, orderEventsRepository, optimisticLockingExecutor,
                        new OrderRealizationStepBack(orderEventsRepository));
        checker = new ShipmentCancellationChecker(storesRepository, ordersRepository, shippingProviderFactory, publisher, settler);
    }

    private static Shipment pendingShipment(String commandId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo("TRK-1");
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 30, 9, 0));
        shipment.setExternalId(EXTERNAL_ID);
        shipment.markCancellationPending(commandId, LocalDateTime.now());
        return shipment;
    }

    private Order orderWith(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setShipments(new ArrayList<>(List.of(shipments)));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        return order;
    }

    private static ShipmentCancellationCheckRequest attempt(int attempt) {
        return new ShipmentCancellationCheckRequest(STORE_ID, ORDER_ID, EXTERNAL_ID, COMMAND_ID, attempt);
    }

    @Test
    void pendingResultSchedulesTheNextAttempt() {
        // given
        orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID)).thenReturn(ShipmentCancellation.pending(COMMAND_ID));

        // when
        checker.check(attempt(2));

        // then
        verify(publisher).publish(attempt(3));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void pendingOnTheLastAttemptIsUnconfirmed() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID)).thenReturn(ShipmentCancellation.pending(COMMAND_ID));

        // when
        checker.check(attempt(ShipmentCancellationChecker.MAX_ATTEMPTS));

        // then
        verify(publisher, never()).publish(any());
        verify(ordersRepository).save(order);
        assertThat(order.getShipments().get(0).getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
    }

    @Test
    void providerErrorOnTheLastAttemptIsUnconfirmedNotPending() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID)).thenThrow(new ShippingException("502 Bad Gateway"));

        // when
        checker.check(attempt(ShipmentCancellationChecker.MAX_ATTEMPTS));

        // then
        assertThat(order.getShipments().get(0).getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
    }

    @Test
    void providerErrorBeforeTheLastAttemptRetries() {
        // given
        orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID)).thenThrow(new ShippingException("timeout"));

        // when
        checker.check(attempt(1));

        // then
        verify(publisher).publish(attempt(2));
    }

    @Test
    void succeededClearsTheShipmentsAndTheShippingNotification() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID))
                .thenReturn(ShipmentCancellation.succeeded(COMMAND_ID, List.of()));

        // when
        checker.check(attempt(1));

        // then
        verify(ordersRepository).save(order);
        assertThat(order.getShipments()).hasSize(1);
        assertThat(order.getShipments().get(0).getExternalId()).isNull();
        assertThat(order.getShipments().get(0).getTrackingNo()).isNull();
        assertThat(order.getShipments().get(0).getCancellationStatus()).isNull();
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
        verify(publisher, never()).publish(any());
    }

    @Test
    void failedKeepsTheShipmentWithTheReason() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID))
                .thenReturn(ShipmentCancellation.failed(COMMAND_ID, "Przesyłka została już odebrana", List.of()));

        // when
        checker.check(attempt(1));

        // then
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(shipment.getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(shipment.getCancellationError()).isEqualTo("Przesyłka została już odebrana");
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void staleMessageForANewerCommandChangesNothing() {
        // given
        orderWith(pendingShipment("cmd-2"));

        // when
        checker.check(attempt(1));

        // then
        verifyNoInteractions(provider, publisher);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void messageForADeletedOrderChangesNothing() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        checker.check(attempt(1));

        // then
        verifyNoInteractions(provider, publisher);
    }

    @Test
    void shipmentReplacedWhileCheckingIsLeftAlone() {
        // given: the first read sees the pending shipment, the fresh read inside the executor does not
        Order first = orderWith(pendingShipment(COMMAND_ID));
        Order fresh = new Order(STORE_ID);
        fresh.setOrderId(ORDER_ID);
        fresh.setShipments(new ArrayList<>(List.of(new Shipment(ShipmentType.Courier))));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(first, fresh);
        when(provider.checkShipmentCancellation(COMMAND_ID, EXTERNAL_ID))
                .thenReturn(ShipmentCancellation.succeeded(COMMAND_ID, List.of()));

        // when
        checker.check(attempt(1));

        // then
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void missingProviderIsUnconfirmed() {
        // given
        Order order = orderWith(pendingShipment(COMMAND_ID));
        when(shippingProviderFactory.get(store)).thenReturn(null);

        // when
        checker.check(attempt(1));

        // then
        assertThat(order.getShipments().get(0).getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
        verify(publisher, never()).publish(any());
    }
}
