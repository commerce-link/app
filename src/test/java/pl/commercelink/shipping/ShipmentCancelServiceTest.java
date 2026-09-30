package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void cancelShippingMarksPendingBeforeSendingTheCommandUnderTheSameId() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        List<Boolean> pendingWhenSent = new ArrayList<>();
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenAnswer(invocation -> {
            pendingWhenSent.add(order.getShipments().get(0).hasCancellationCommand(invocation.getArgument(1)));
            return ShipmentCancellation.pending(invocation.getArgument(1));
        });

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(ordersRepository, shippingProvider, publisher);
        inOrder.verify(ordersRepository).save(order);
        inOrder.verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        inOrder.verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
        assertThat(commandId.getValue()).isNotBlank();
        assertThat(pendingWhenSent).containsExactly(true);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.isCancellationPending()).isTrue();
        assertThat(shipment.hasCancellationCommand(commandId.getValue())).isTrue();
        verify(ordersRepository, times(1)).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void cancelShippingRefusesWhenAConcurrentRequestMarkedTheCancellationFirst() {
        // given: the first read shows no cancellation, the fresh read inside the executor a PENDING one 20 s old
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Shipment marked = courierShipment(EXTERNAL_ID);
        marked.markCancellationPending("cmd-other", LocalDateTime.now().minusSeconds(20));
        Order fresh = orderWithShipments(marked);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShipmentCancellationInProgressException.class)
                .isInstanceOf(ShippingException.class)
                .hasMessage(ShipmentCancelService.ALREADY_IN_PROGRESS);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
        assertThat(fresh.getShipments().get(0).hasCancellationCommand("cmd-other")).isTrue();
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
                .isInstanceOf(ShipmentCancellationInProgressException.class)
                .hasMessage(ShipmentCancelService.ALREADY_IN_PROGRESS);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void cancelShippingRestoresNoCancellationWhenTheProviderRefusesTheCommand() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        ShippingException refused = new ShippingException("already in transit");
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenThrow(refused);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID)).isSameAs(refused);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getCancellationStatus()).isNull();
        assertThat(shipment.getCancellationCommandId()).isNull();
        assertThat(shipment.getCancellationError()).isNull();
        assertThat(shipment.getCancellationRequestedAt()).isNull();
        verify(ordersRepository, times(2)).save(order);
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRestoresTheFailedCancellationWhenTheProviderRefusesTheRetry() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(2);
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", requestedAt);
        shipment.markCancellationFailed("temporary carrier error");
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        ShippingException refused = new ShippingException("already in transit");
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenThrow(refused);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID)).isSameAs(refused);
        Shipment restored = order.getShipments().get(0);
        assertThat(restored.getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(restored.hasCancellationCommand("cmd-1")).isTrue();
        assertThat(restored.getCancellationError()).isEqualTo("temporary carrier error");
        assertThat(restored.getCancellationRequestedAt()).isEqualTo(requestedAt);
        verify(ordersRepository, times(2)).save(order);
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRethrowsTheProviderErrorWhenTheRestoreFails() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        ShippingException refused = new ShippingException("already in transit");
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenThrow(refused);
        doAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave())
                .doThrow(new IllegalStateException("DynamoDB unavailable"))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID)).isSameAs(refused);
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRechecksAnUnconfirmedCommandInsteadOfSendingANewOne() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(2);
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", requestedAt);
        shipment.markCancellationUnconfirmed();
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider, never()).cancelShipment(any(), any());
        Shipment remarked = order.getShipments().get(0);
        assertThat(remarked.isCancellationPending()).isTrue();
        assertThat(remarked.hasCancellationCommand("cmd-1")).isTrue();
        assertThat(remarked.getCancellationRequestedAt()).isAfter(requestedAt);
        verify(ordersRepository).save(order);
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
    }

    @Test
    void cancelShippingRechecksAPendingCommandOlderThanFiveMinutes() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(6);
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.markCancellationPending("cmd-1", requestedAt);
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider, never()).cancelShipment(any(), any());
        Shipment remarked = order.getShipments().get(0);
        assertThat(remarked.isCancellationPending()).isTrue();
        assertThat(remarked.hasCancellationCommand("cmd-1")).isTrue();
        assertThat(remarked.getCancellationRequestedAt()).isAfter(requestedAt);
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
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.pending(invocation.getArgument(1)));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        assertThat(commandId.getValue()).isNotEqualTo("cmd-1");
        Shipment saved = order.getShipments().get(0);
        assertThat(saved.hasCancellationCommand(commandId.getValue())).isTrue();
        assertThat(saved.getCancellationError()).isNull();
        verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
    }

    @Test
    void cancelShippingSendsNothingWhenTheShipmentVanishedBeforeTheMark() {
        // given: the fresh read inside the executor no longer has the package
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Order fresh = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(ordersRepository, never()).save(any());
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingMarksOnceWhenTheFirstSaveConflicts() {
        // given: the real executor runs load-mutate-save again after a version conflict
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Order firstAttempt = orderWithShipments(courierShipment(EXTERNAL_ID));
        Order secondAttempt = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, firstAttempt, secondAttempt);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        doThrow(new ConditionalCheckFailedException("version conflict")).doNothing().when(ordersRepository).save(any());
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.pending(invocation.getArgument(1)));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        verify(shippingProvider, times(1)).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        assertThat(secondAttempt.getShipments().get(0).hasCancellationCommand(commandId.getValue())).isTrue();
        verify(ordersRepository).save(firstAttempt);
        verify(ordersRepository).save(secondAttempt);
        verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
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
