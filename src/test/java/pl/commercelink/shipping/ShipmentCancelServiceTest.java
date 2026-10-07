package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.CourierCancellation;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderRealizationStepBack;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCancellationStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.rest.client.HttpClientException;
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
import static org.mockito.Mockito.verifyNoInteractions;
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

    private ShipmentCancelService shipmentCancelService;

    @BeforeEach
    void setUp() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        // the real settler: an immediate provider result is written by the same rules as the checker's
        ShipmentCancellationSettler settler =
                new ShipmentCancellationSettler(ordersRepository, orderEventsRepository, optimisticLockingExecutor,
                        new OrderRealizationStepBack(orderEventsRepository));
        shipmentCancelService = new ShipmentCancelService(storesRepository, ordersRepository, shippingProviderFactory,
                publisher, optimisticLockingExecutor, settler);
    }

    @Test
    @DisplayName("cancelShipping throws ShippingException when no shipment has a courier order")
    void cancelShippingThrowsShippingExceptionWhenNoShipmentHasShippingData() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.PersonalCollection));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShippingException.class)
                .hasMessageContaining("No courier order to cancel");

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
                .hasMessageContaining("No courier order to cancel");

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
            pendingWhenSent.add(order.getShipments().get(0).getCancellation().hasCommand(invocation.getArgument(1)));
            return ShipmentCancellation.pending(invocation.getArgument(1));
        });

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.REQUESTED);
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        InOrder inOrder = inOrder(ordersRepository, shippingProvider, publisher);
        inOrder.verify(ordersRepository).save(order);
        inOrder.verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        inOrder.verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
        assertThat(commandId.getValue()).isNotBlank();
        assertThat(pendingWhenSent).containsExactly(true);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getCancellation().isPending()).isTrue();
        assertThat(shipment.getCancellation().hasCommand(commandId.getValue())).isTrue();
        verify(ordersRepository, times(1)).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void cancelShippingRefusesWhenAConcurrentRequestMarkedTheCancellationFirst() {
        // given: the first read shows no cancellation, the fresh read inside the executor a PENDING one 20 s old
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Shipment marked = courierShipment(EXTERNAL_ID);
        marked.setCancellation(CourierCancellation.pending("cmd-other", LocalDateTime.now().minusSeconds(20)));
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
        assertThat(fresh.getShipments().get(0).getCancellation().hasCommand("cmd-other")).isTrue();
    }

    @Test
    void cancelShippingRefusesWhileACancellationIsInProgress() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusSeconds(20)));
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
    void cancelShippingRefusesWhenTheStoreHasNoShippingProvider() {
        // given: no provider configured, or its authorization was lost
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isExactlyInstanceOf(ShippingUnavailableException.class);
        verify(ordersRepository, never()).save(any());
        verify(publisher, never()).publish(any());
        assertThat(order.getShipments().get(0).getCancellation()).isNull();
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
        assertThat(shipment.getCancellation()).isNull();
        verify(ordersRepository, times(2)).save(order);
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRestoresTheFailedCancellationWhenTheProviderRefusesTheRetry() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(2);
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.setCancellation(CourierCancellation.pending("cmd-1", requestedAt));
        shipment.setCancellation(shipment.getCancellation().failed());
        CourierCancellation previous = shipment.getCancellation();
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        ShippingException refused = new ShippingException("already in transit");
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenThrow(refused);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID)).isSameAs(refused);
        Shipment restored = order.getShipments().get(0);
        assertThat(restored.getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(restored.getCancellation().hasCommand("cmd-1")).isTrue();
        assertThat(restored.getCancellation()).isEqualTo(previous);
        assertThat(restored.getCancellation().getRequestedAt()).isEqualTo(requestedAt);
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
        shipment.setCancellation(CourierCancellation.pending("cmd-1", requestedAt));
        shipment.setCancellation(shipment.getCancellation().unconfirmed());
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.RECHECKING);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        Shipment remarked = order.getShipments().get(0);
        assertThat(remarked.getCancellation().isPending()).isTrue();
        assertThat(remarked.getCancellation().hasCommand("cmd-1")).isTrue();
        assertThat(remarked.getCancellation().getRequestedAt()).isAfter(requestedAt);
        verify(ordersRepository).save(order);
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
    }

    @Test
    void cancelShippingRechecksAPendingCommandOlderThanFiveMinutes() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(6);
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.setCancellation(CourierCancellation.pending("cmd-1", requestedAt));
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.RECHECKING);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        Shipment remarked = order.getShipments().get(0);
        assertThat(remarked.getCancellation().isPending()).isTrue();
        assertThat(remarked.getCancellation().hasCommand("cmd-1")).isTrue();
        assertThat(remarked.getCancellation().getRequestedAt()).isAfter(requestedAt);
        verify(publisher).publish(ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, "cmd-1"));
    }

    @Test
    void cancelShippingSendsANewCommandAfterAFailure() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        shipment.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        shipment.setCancellation(shipment.getCancellation().failed());
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
        assertThat(saved.getCancellation().hasCommand(commandId.getValue())).isTrue();
        assertThat(saved.getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.PENDING);
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
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.GONE);
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
        assertThat(secondAttempt.getShipments().get(0).getCancellation().hasCommand(commandId.getValue())).isTrue();
        verify(ordersRepository).save(firstAttempt);
        verify(ordersRepository).save(secondAttempt);
        verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
    }

    @Test
    void cancelShippingSettlesAnImmediateSuccessWithoutACheck() {
        // given: the package was already cancelled at the provider
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.succeeded(invocation.getArgument(1), List.of()));

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.CANCELLED);
        assertThat(order.getShipments()).hasSize(1);
        Shipment placeholder = order.getShipments().get(0);
        assertThat(placeholder.getType()).isEqualTo(ShipmentType.Courier);
        assertThat(placeholder.getExternalId()).isNull();
        assertThat(placeholder.getTrackingNo()).isNull();
        assertThat(placeholder.getCancellation()).isNull();
        verify(ordersRepository, times(2)).save(order);
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRecordsAnImmediateFailureWithoutACheck() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenAnswer(invocation ->
                ShipmentCancellation.failed(invocation.getArgument(1), "Przesyłka została już odebrana", List.of()));

        // when
        ShipmentCancelResult result;
        List<String> warnings;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentCancellationSettler.class)) {
            result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);
            warnings = logs.warnings();
        }

        // then: the flash gets the reason from the result, the shipment keeps only the state, the log the reason
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.FAILED);
        assertThat(result.error()).isEqualTo("Przesyłka została już odebrana");
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(shipment.getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(warnings).singleElement().asString().contains("Przesyłka została już odebrana");
        verify(ordersRepository, times(2)).save(order);
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void cancelShippingRefusesWhenTheFreshReadTurnedUnconfirmedAfterAPlainFirstRead() {
        // given: the first read chooses a new command, the fresh read already asks for a re-check
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        Shipment unconfirmed = courierShipment(EXTERNAL_ID);
        unconfirmed.setCancellation(CourierCancellation.pending("cmd-other", LocalDateTime.now().minusMinutes(2)));
        unconfirmed.setCancellation(unconfirmed.getCancellation().unconfirmed());
        Order fresh = orderWithShipments(unconfirmed);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShipmentCancellationInProgressException.class);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
        assertThat(fresh.getShipments().get(0).getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
    }

    @Test
    void cancelShippingRefusesWhenTheFreshReadNoLongerNeedsTheRecheckChosenOnTheFirstRead() {
        // given: the first read sees an unconfirmed command, the fresh read a failed one
        Shipment unconfirmed = courierShipment(EXTERNAL_ID);
        unconfirmed.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        unconfirmed.setCancellation(unconfirmed.getCancellation().unconfirmed());
        Order order = orderWithShipments(unconfirmed);
        Shipment failed = courierShipment(EXTERNAL_ID);
        failed.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        failed.setCancellation(failed.getCancellation().failed());
        Order fresh = orderWithShipments(failed);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShipmentCancellationInProgressException.class);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
        assertThat(fresh.getShipments().get(0).getCancellation().getStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
    }

    @Test
    void cancelShippingRefusesARecheckWhenTheFreshReadCarriesAnotherCommand() {
        // given: both reads need a re-check, but of different commands
        Shipment first = courierShipment(EXTERNAL_ID);
        first.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now().minusMinutes(2)));
        first.setCancellation(first.getCancellation().unconfirmed());
        Shipment other = courierShipment(EXTERNAL_ID);
        other.setCancellation(CourierCancellation.pending("cmd-2", LocalDateTime.now().minusMinutes(2)));
        other.setCancellation(other.getCancellation().unconfirmed());
        Order order = orderWithShipments(first);
        Order fresh = orderWithShipments(other);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, fresh);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShipmentCancellationInProgressException.class);
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
        assertThat(fresh.getShipments().get(0).getCancellation().hasCommand("cmd-2")).isTrue();
    }

    @Test
    void cancelShippingReportsAnOrderDeletedBetweenTheReadsAsGone() {
        // given: the real executor wraps any exception thrown inside the mutator
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order, (Order) null);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.GONE);
        verify(shippingProvider, never()).cancelShipment(any(), any());
        verify(publisher, never()).publish(any());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void cancelShippingKeepsAServerErrorPendingAndChecksIt() {
        // given: the PUT answered 502 the way the library wraps it; Furgonetka may still have run the command
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        HttpClientException http = new HttpClientException(502, "Bad Gateway");
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenThrow(new ShippingException(http.getMessage(), http));

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.REQUESTED);
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getCancellation().isPending()).isTrue();
        assertThat(shipment.getCancellation().hasCommand(commandId.getValue())).isTrue();
        verify(ordersRepository, times(1)).save(order);
        verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
    }

    @Test
    void cancelShippingKeepsATimeoutPendingAndChecksIt() {
        // given: no HTTP answer at all
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenThrow(new IllegalStateException(new java.net.SocketTimeoutException("Read timed out")));

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.REQUESTED);
        ArgumentCaptor<String> commandId = ArgumentCaptor.forClass(String.class);
        verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), commandId.capture());
        assertThat(order.getShipments().get(0).getCancellation().hasCommand(commandId.getValue())).isTrue();
        assertThat(order.getShipments().get(0).getCancellation().isPending()).isTrue();
        verify(publisher).publish(
                ShipmentCancellationCheckRequest.first(STORE_ID, ORDER_ID, EXTERNAL_ID, commandId.getValue()));
    }

    @Test
    void cancelShippingRestoresAndRethrowsAClientError() {
        // given: a 4xx answer is a clear refusal of the command
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        HttpClientException http = new HttpClientException(400, "{\"errors\":[{\"message\":\"Nieprawidłowa paczka\"}]}");
        ShippingException refused = new ShippingException(http.getMessage(), http);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString())).thenThrow(refused);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID)).isSameAs(refused);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getCancellation()).isNull();
        verify(ordersRepository, times(2)).save(order);
        verify(publisher, never()).publish(any());
    }

    private void succeedsRightAway() {
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.succeeded(invocation.getArgument(1), List.of()));
    }

    @Test
    void anImmediateCancellationOfTheOnlyShippedCourierOrderTakesTheOrderBackToRealizationWithoutAnEmail() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        order.setStatus(OrderStatus.Shipping);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        succeedsRightAway();

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then: the step back is recorded before the settling save, so the notifications skip "Zamówienie w realizacji"
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.CANCELLED);
        assertThat(result.backToRealization()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Realization);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        InOrder inOrder = inOrder(orderEventsRepository, ordersRepository);
        inOrder.verify(ordersRepository).save(order);
        inOrder.verify(orderEventsRepository).save(event.capture());
        inOrder.verify(ordersRepository).save(order);
        assertThat(event.getValue().getType()).isEqualTo(EventType.action);
        assertThat(event.getValue().getName()).isEqualTo(OrderRealizationStepBack.EVENT);
    }

    @Test
    void anImmediateCancellationBeforeShippingKeepsTheStatus() {
        // given
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        order.setStatus(OrderStatus.Assembled);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        succeedsRightAway();

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.CANCELLED);
        assertThat(result.backToRealization()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Assembled);
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void aRequestedCancellationLeavesTheStatusToTheBackgroundCheck() {
        // given: the provider takes the command and answers later
        Order order = orderWithShipments(courierShipment(EXTERNAL_ID));
        order.setStatus(OrderStatus.Shipping);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.pending(invocation.getArgument(1)));

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        assertThat(result.outcome()).isEqualTo(ShipmentCancelOutcome.REQUESTED);
        assertThat(result.backToRealization()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Shipping);
        assertThat(order.getShipments().get(0).getExternalId()).isEqualTo(EXTERNAL_ID);
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void theCourierOrderIsFoundByItsIdWhenItsShipmentLostTheShippedDate() {
        // given: a hand-typed shipment that went out first, then the booked courier without its date
        Shipment typed = courierShipment(null);
        Shipment booked = courierShipment(EXTERNAL_ID);
        booked.setShippedAt(null);
        Order order = orderWithShipments(typed, booked);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        when(shippingProvider.cancelShipment(eq(EXTERNAL_ID), anyString()))
                .thenAnswer(invocation -> ShipmentCancellation.pending(invocation.getArgument(1)));

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), anyString());
        assertThat(booked.getCancellation().isPending()).isTrue();
    }

    @Test
    void anImmediateCancellationOfOneCourierOrderKeepsTheOtherShipmentsAndTheStatusWhileOneOfThemIsShipped() {
        // given: the courier order first, a parcel typed by hand that really went out after it
        Shipment booked = courierShipment(EXTERNAL_ID);
        Shipment typed = courierShipment(null);
        typed.setTrackingNo("TRK-TYPED");
        Order order = orderWithShipments(booked, typed);
        order.setStatus(OrderStatus.Shipping);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        succeedsRightAway();

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then: the typed parcel is with the carrier, so the order stays Shipping and the customer's e-mail stands
        assertThat(result.backToRealization()).isFalse();
        assertThat(order.getShipments()).containsExactly(typed);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Shipping);
        verify(orderEventsRepository, never()).save(any());
        verify(orderEventsRepository, never()).deleteByOrderIdAndName(any(), any());
    }

    @Test
    void anImmediateCancellationOfACourierOrderOfTwoParcelsRemovesBothAndStepsBackWhenWhatIsLeftHasNotGoneOut() {
        // given: two parcels of one courier order and a shipment still waiting for its data
        Shipment parcel = courierShipment(EXTERNAL_ID);
        Shipment secondParcel = courierShipment(EXTERNAL_ID);
        secondParcel.setTrackingNo("TRK-124");
        Shipment waiting = new Shipment(ShipmentType.Courier);
        waiting.setTrackingNo("TRK-WAITING");
        Order order = orderWithShipments(parcel, secondParcel, waiting);
        order.setStatus(OrderStatus.Shipping);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);
        succeedsRightAway();

        // when
        ShipmentCancelResult result = shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider).cancelShipment(eq(EXTERNAL_ID), anyString());
        assertThat(order.getShipments()).containsExactly(waiting);
        assertThat(result.backToRealization()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Realization);
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    @Test
    void aStoreThatLostItsShippingProviderGetsARefusalAndNothingChanges() {
        // given: the carrier authorisation was lost, so the factory has no provider for the store
        Shipment booked = courierShipment(EXTERNAL_ID);
        Order order = orderWithShipments(booked);
        order.setStatus(OrderStatus.Shipping);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID))
                .isInstanceOf(ShippingUnavailableException.class);
        assertThat(order.getShipments()).containsExactly(booked);
        assertThat(booked.getCancellation()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.Shipping);
        verify(ordersRepository, never()).save(any());
        verify(publisher, never()).publish(any());
        verifyNoInteractions(orderEventsRepository);
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
