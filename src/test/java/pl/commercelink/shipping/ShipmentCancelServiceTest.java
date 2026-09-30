package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShipmentCancellation;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCancelServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ID = "PKG-12345";

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
    private ShippingProvider shippingProvider;

    @InjectMocks
    private ShipmentCancelService shipmentCancelService;

    @BeforeEach
    void passThroughOptimisticLocking() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
    }

    @Test
    @DisplayName("cancelShipping throws ShippingException when no shipment carries valid shipping data")
    void cancelShippingThrowsShippingExceptionWhenNoShipmentHasShippingData() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.PersonalCollection));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShippingException.class)
                .hasMessageContaining("No valid shipment data");

        verify(shippingProviderFactory, never()).get(any());
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    @DisplayName("cancelShipping throws ShippingException when the shipment lacks an external package id")
    void cancelShippingThrowsShippingExceptionWhenShipmentHasNoExternalId() {
        // given
        Shipment shippableButNoExternalId = courierShipment(null);
        Order order = orderWithShipments(shippableButNoExternalId);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShippingException.class)
                .hasMessageContaining("no external package ID");

        verify(shippingProvider, never()).cancelShipment(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void cancelShippingSendsTheCommandMarksPendingAndSchedulesTheCheck() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(EXTERNAL_ID)).thenReturn(ShipmentCancellation.pending("cmd-1"));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(shipment.isCancellationPending()).isTrue();
        assertThat(shipment.hasCancellationCommand("cmd-1")).isTrue();
        verify(ordersRepository).save(order);
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void cancelShippingRefusesWhileACancellationIsInProgress() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", LocalDateTime.now().minusSeconds(20));
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShippingException.class)
                .hasMessage(ShipmentCancelService.ALREADY_IN_PROGRESS);
        verify(shippingProvider, never()).cancelShipment(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRechecksAnUnconfirmedCommandInsteadOfSendingANewOne() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", LocalDateTime.now().minusMinutes(2));
        shipment.markCancellationUnconfirmed();
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider, never()).cancelShipment(any());
        assertThat(order.getShipments().get(0).isCancellationPending()).isTrue();
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
    }

    @Test
    void cancelShippingRechecksAPendingCommandOlderThanFiveMinutes() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", LocalDateTime.now().minusMinutes(6));
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider, never()).cancelShipment(any());
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
    }

    @Test
    void cancelShippingSendsANewCommandAfterAFailure() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", LocalDateTime.now().minusMinutes(2));
        shipment.markCancellationFailed("temporary carrier error");
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(EXTERNAL_ID)).thenReturn(ShipmentCancellation.pending("cmd-2"));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        Shipment saved = order.getShipments().get(0);
        assertThat(saved.hasCancellationCommand("cmd-2")).isTrue();
        assertThat(saved.getCancellationError()).isNull();
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-2"));
    }

    @Test
    void cancelShippingDoesNotScheduleACheckWhenTheShipmentVanishedBeforeTheSave() {
        // given: the fresh read inside the executor no longer has the package
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Order fresh = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(EXTERNAL_ID)).thenReturn(ShipmentCancellation.pending("cmd-1"));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(ordersRepository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    private Order orderWithShipments(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    private Shipment courierShipment(String externalId) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DHL");
        shipment.setTrackingNo("TRK-123");
        shipment.setShippedAt(LocalDateTime.now().minusHours(1));
        shipment.setExternalId(externalId);
        return shipment;
    }
}
