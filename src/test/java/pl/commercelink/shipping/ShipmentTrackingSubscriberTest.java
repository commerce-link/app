package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentTrackingStatus;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.ParcelTrackingRequest;
import pl.commercelink.shipping.api.ParcelTrackingSubscription;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentTrackingSubscriberTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private ShippingProviderFactory shippingProviderFactory;
    @Mock
    private ShipmentTrackingsRepository shipmentTrackingsRepository;
    @Mock
    private ShipmentTrackingEventPublisher publisher;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private Store store;
    @Mock
    private ShippingProvider provider;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;

    @InjectMocks
    private ShipmentTrackingSubscriber subscriber;

    @BeforeEach
    void passThroughOptimisticLocking() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(optimisticLockingExecutor.modifyAndSaveReturning(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSaveReturning());
    }

    private static Shipment courier(String trackingNo) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(LocalDateTime.of(2026, 9, 2, 10, 0));
        return shipment;
    }

    private static Order orderWith(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setShipments(new java.util.ArrayList<>(List.of(shipments)));
        return order;
    }

    private void providerAvailable() {
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        when(provider.supportsParcelTracking()).thenReturn(true);
        when(shipmentTrackingsRepository.saveIfAbsent(any())).thenReturn(true);
    }

    @Test
    void doesNothingWhenNoShipmentHasShippingData() {
        // given
        Order order = orderWith(new Shipment(ShipmentType.PersonalCollection));

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        verifyNoInteractions(storesRepository, shippingProviderFactory, shipmentTrackingsRepository, publisher);
    }

    @Test
    void blankTrackingNumberIsSkipped() {
        // given
        providerAvailable();
        Shipment shipment = courier("   ");
        Order order = orderWith(shipment);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        verifyNoInteractions(shipmentTrackingsRepository);
        verify(provider, never()).trackParcel(any());
        assertThat(shipment.getTrackingSubscriptionStatus()).isNull();
    }

    @Test
    void doesNothingWhenStoreHasNoTrackingCapableProvider() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(shippingProviderFactory.get(store)).thenReturn(null);
        Order order = orderWith(courier("PKG-1"));

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(order.getShipments().get(0).hasTrackingSubscription()).isFalse();
        verifyNoInteractions(shipmentTrackingsRepository, publisher);
    }

    @Test
    void activeResultMarksShipmentAndIndexesTrackingNo() {
        // given
        providerAvailable();
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.active("cmd-1", "21037943", "dpd"));
        Order order = orderWith(courier("PKG-1"));
        ArgumentCaptor<ShipmentTracking> indexed = ArgumentCaptor.forClass(ShipmentTracking.class);
        ArgumentCaptor<ParcelTrackingRequest> request = ArgumentCaptor.forClass(ParcelTrackingRequest.class);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        verify(shipmentTrackingsRepository).saveIfAbsent(indexed.capture());
        assertThat(indexed.getValue().getStoreId()).isEqualTo(STORE_ID);
        assertThat(indexed.getValue().getTrackingNo()).isEqualTo("PKG-1");
        assertThat(indexed.getValue().getOrderId()).isEqualTo(ORDER_ID);
        verify(provider).trackParcel(request.capture());
        assertThat(request.getValue().trackingNo()).isEqualTo("PKG-1");
        assertThat(request.getValue().carrier()).isEqualTo("DPD");
        assertThat(request.getValue().label()).contains(ORDER_ID);
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(shipment.getTrackingExternalId()).isEqualTo("21037943");
        verify(publisher, never()).publish(any());
    }

    @Test
    void pendingResultSchedulesDelayedCheck() {
        // given
        providerAvailable();
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.pending("cmd-1"));
        Order order = orderWith(courier("PKG-1"));
        ArgumentCaptor<ShipmentTrackingCheckRequest> check = ArgumentCaptor.forClass(ShipmentTrackingCheckRequest.class);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(order.getShipments().get(0).isTrackingPending()).isTrue();
        assertThat(order.getShipments().get(0).getTrackingSubscriptionId()).isEqualTo("cmd-1");
        verify(publisher).publish(check.capture());
        assertThat(check.getValue().getStoreId()).isEqualTo(STORE_ID);
        assertThat(check.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(check.getValue().getTrackingNo()).isEqualTo("PKG-1");
    }

    @Test
    void providerFailureMarksShipmentFailedAndRecordsOrderEventWithoutThrowing() {
        // given
        providerAvailable();
        when(provider.trackParcel(any())).thenThrow(new ShippingException("429 Too Many Requests"));
        Order order = orderWith(courier("PKG-1"));
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        Shipment shipment = order.getShipments().get(0);
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(shipment.getTrackingSubscriptionError()).contains("429");
        verify(orderEventsRepository).save(event.capture());
        assertThat(event.getValue().getName()).isEqualTo(ShipmentTrackingSubscriber.TRACKING_FAILED_EVENT);
    }

    @Test
    void duplicateTrackingNoFailsWithoutCallingProvider() {
        // given
        providerAvailable();
        when(shipmentTrackingsRepository.saveIfAbsent(any())).thenReturn(false);
        Order order = orderWith(courier("PKG-1"));

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(order.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(order.getShipments().get(0).getTrackingSubscriptionError()).isEqualTo(ShipmentTrackingSubscriber.DUPLICATE_TRACKING_NO);
        verify(provider, never()).trackParcel(any());
    }

    @Test
    void reindexingTheSameOrderForAnAlreadyTrackedTrackingNoStillCallsProvider() {
        // given
        providerAvailable();
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "PKG-1", ORDER_ID, null, LocalDateTime.now())));
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.active("cmd-1", "21037943", "dpd"));
        Order order = orderWith(courier("PKG-1"));

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(order.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        verify(provider).trackParcel(any());
        verify(shipmentTrackingsRepository, never()).saveIfAbsent(any());
    }

    @Test
    void anotherOrderAlreadyOwningTheTrackingNoFailsWithoutCallingProvider() {
        // given
        providerAvailable();
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "PKG-1", "order-other", null, LocalDateTime.now())));
        Order order = orderWith(courier("PKG-1"));

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(order.getShipments().get(0).getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(order.getShipments().get(0).getTrackingSubscriptionError()).isEqualTo(ShipmentTrackingSubscriber.DUPLICATE_TRACKING_NO);
        verify(provider, never()).trackParcel(any());
        verify(shipmentTrackingsRepository, never()).saveIfAbsent(any());
    }

    @Test
    void providerCreatedShipmentIsIndexedAndActiveWithoutTrackingCall() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.setExternalId("furg-77");
        Order order = orderWith(shipment);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(shipment.getTrackingExternalId()).isEqualTo("furg-77");
        verify(shipmentTrackingsRepository).saveIfAbsent(any());
        verify(provider, never()).trackParcel(any());
    }

    @Test
    void alreadySubscribedShipmentsAreSkipped() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingActive("1", LocalDateTime.now());
        Order order = orderWith(shipment);

        // when
        subscriber.subscribe(STORE_ID, order);

        // then
        verifyNoInteractions(shipmentTrackingsRepository);
        verify(provider, never()).trackParcel(any());
    }

    @Test
    void checkAppliesActiveResultAndSavesOrder() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending("cmd-1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.checkParcelTracking("cmd-1")).thenReturn(ParcelTrackingSubscription.active("cmd-1", "21037943", "dpd"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 1);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        verify(ordersRepository).save(order);
    }

    @Test
    void checkThrowsPendingExceptionToTriggerRedeliveryWhileAttemptsRemain() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending("cmd-1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.checkParcelTracking("cmd-1")).thenReturn(ParcelTrackingSubscription.pending("cmd-1"));

        // then
        assertThatThrownBy(() -> subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 2))
                .isInstanceOf(ShipmentTrackingPendingException.class);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void checkGivesUpAfterMaxAttempts() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending("cmd-1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.checkParcelTracking("cmd-1")).thenReturn(ParcelTrackingSubscription.pending("cmd-1"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), ShipmentTrackingSubscriber.MAX_CHECK_ATTEMPTS);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(shipment.getTrackingSubscriptionError()).isEqualTo(ShipmentTrackingSubscriber.CHECK_TIMED_OUT);
        verify(ordersRepository).save(order);
    }

    @Test
    void checkIgnoresShipmentsNoLongerPending() {
        // given
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingActive("1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 1);

        // then
        verifyNoInteractions(shippingProviderFactory);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void rateLimitedTrackParcelLeavesShipmentPendingWithoutCommandIdAndSchedulesRetry() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        when(provider.trackParcel(any())).thenThrow(
                new ShippingException("HTTP 429: too many requests", new HttpClientException(429, "too many requests")));

        // when
        subscriber.subscribe(STORE_ID, orderWith(shipment));

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.PENDING);
        assertThat(shipment.getTrackingSubscriptionId()).isNull();
        verify(publisher).publish(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"));
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void rmaShipmentLeftPendingByProviderFailsInsteadOfWaitingForever() {
        // given
        providerAvailable();
        Shipment shipment = courier("RMA-PKG");
        RMA rma = new RMA(STORE_ID);
        rma.setRmaId("rma-1");
        rma.setShipments(new java.util.ArrayList<>(List.of(shipment)));
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.pending("cmd-1"));

        // when
        subscriber.subscribe(STORE_ID, rma);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(shipment.getTrackingSubscriptionError()).isEqualTo(ShipmentTrackingSubscriber.RMA_RETRY_UNSUPPORTED);
        verify(publisher, never()).publish(any());
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    void checkRepeatsTrackParcelWhenNoCommandIdWasRecorded() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending(null, LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.active("cmd-2", "21037943", "dpd"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 2);

        // then
        verify(provider).trackParcel(new ParcelTrackingRequest("PKG-1", "DPD", "CommerceLink order " + ORDER_ID));
        verify(provider, never()).checkParcelTracking(any());
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(shipment.getTrackingExternalId()).isEqualTo("21037943");
        verify(ordersRepository).save(order);
    }

    @Test
    void checkPersistsNewlyAcceptedCommandIdBeforeRequestingRedelivery() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending(null, LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.trackParcel(any())).thenReturn(ParcelTrackingSubscription.pending("cmd-9"));

        // when / then
        assertThatThrownBy(() -> subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 2))
                .isInstanceOf(ShipmentTrackingPendingException.class);
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.PENDING);
        assertThat(shipment.getTrackingSubscriptionId()).isEqualTo("cmd-9");
        verify(ordersRepository).save(order);
    }

    @Test
    void checkRequestsRedeliveryWhenProviderCallFailsWhileAttemptsRemain() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending("cmd-1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.checkParcelTracking("cmd-1")).thenThrow(new ShippingException("HTTP 401: token revoked"));

        // when / then
        assertThatThrownBy(() -> subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 1))
                .isInstanceOf(ShipmentTrackingPendingException.class);
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.PENDING);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void checkFailsShipmentWhenProviderCallFailsOnLastAttempt() {
        // given
        providerAvailable();
        Shipment shipment = courier("PKG-1");
        shipment.markTrackingPending("cmd-1", LocalDateTime.now());
        Order order = orderWith(shipment);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(provider.checkParcelTracking("cmd-1")).thenThrow(new ShippingException("HTTP 502: bad gateway"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), ShipmentTrackingSubscriber.MAX_CHECK_ATTEMPTS);

        // then
        assertThat(shipment.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.FAILED);
        assertThat(shipment.getTrackingSubscriptionError()).isEqualTo("HTTP 502: bad gateway");
        verify(ordersRepository).save(order);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventsRepository).save(event.capture());
        assertThat(event.getValue().getName()).isEqualTo(ShipmentTrackingSubscriber.TRACKING_FAILED_EVENT);
    }

    @Test
    void checkAppliesTheOutcomeToTheFreshlyLoadedOrder() {
        // given
        providerAvailable();
        Shipment stale = courier("PKG-1");
        stale.markTrackingPending("cmd-1", LocalDateTime.now());
        Shipment fresh = courier("PKG-1");
        fresh.markTrackingPending("cmd-1", LocalDateTime.now());
        Order staleOrder = orderWith(stale);
        Order freshOrder = orderWith(fresh);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(staleOrder, freshOrder);
        when(provider.checkParcelTracking("cmd-1")).thenReturn(ParcelTrackingSubscription.active("cmd-1", "77", "dpd"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), 1);

        // then
        assertThat(fresh.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.ACTIVE);
        assertThat(stale.getTrackingSubscriptionStatus()).isEqualTo(ShipmentTrackingStatus.PENDING);
        verify(ordersRepository).save(freshOrder);
        verify(ordersRepository, never()).save(staleOrder);
        verify(ordersRepository, times(2)).findById(STORE_ID, ORDER_ID);
    }

    @Test
    void checkLeavesTheOrderAloneWhenTheFreshOrderNoLongerHasThePendingShipment() {
        // given: the tracking number was edited while the re-check was queued
        providerAvailable();
        Shipment stale = courier("PKG-1");
        stale.markTrackingPending("cmd-1", LocalDateTime.now());
        Order staleOrder = orderWith(stale);
        Order freshOrder = orderWith(courier("PKG-2"));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(staleOrder, freshOrder);
        when(provider.checkParcelTracking("cmd-1")).thenThrow(new ShippingException("HTTP 502: bad gateway"));

        // when
        subscriber.check(new ShipmentTrackingCheckRequest(STORE_ID, ORDER_ID, "PKG-1"), ShipmentTrackingSubscriber.MAX_CHECK_ATTEMPTS);

        // then
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).save(any());
    }
}
