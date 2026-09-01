package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.orders.MarketplaceReturnAction;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycleEvent;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderLifecycleEventListenerTest {

    private static CarrierDictionary dictionaryWithDpd() {
        CarrierDictionary dictionary = new CarrierDictionary();
        dictionary.setCarriers(java.util.Map.of("furgonetka", java.util.Map.of("Empik", "{\"DPD\":\"dpd-1\"}")));
        return dictionary;
    }

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ORDER_ID = "EXT-1";
    private static final String MARKETPLACE = "Empik";

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Spy private CarrierDictionary carrierDictionary = dictionaryWithDpd();

    @Mock private Store store;
    @Mock private Order order;
    @Mock private OrderSource source;
    @Mock private MarketplaceProvider provider;
    @Mock private MarketplaceReturns returns;

    @InjectMocks
    private MarketplaceOrderLifecycleEventListener listener;

    @BeforeEach
    void setUpDefaults() {
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(order.isMarketplaceOrder()).thenReturn(true);
        when(order.getSource()).thenReturn(source);
        when(source.getName()).thenReturn(MARKETPLACE);
        when(store.getConfigurationValue(pl.commercelink.stores.IntegrationType.SHIPPING_PROVIDER)).thenReturn("furgonetka");
        when(order.getExternalOrderId()).thenReturn(EXTERNAL_ORDER_ID);
        when(order.getShipments()).thenReturn(List.of());
        when(order.getDocuments()).thenReturn(List.of());
        when(order.getStatus()).thenReturn(OrderStatus.Assembly);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(new MarketplaceIntegration(MARKETPLACE));
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
    }

    @Test
    void orderAcceptedEventCallsAcceptOrder() {
        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void redeliveredOrderAcceptedEventSendsAcceptAgain() {
        // when
        handle(OrderLifecycleEventType.OrderAccepted);
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider, times(2)).acceptOrder(EXTERNAL_ORDER_ID);
    }

    @ParameterizedTest
    @EnumSource(OrderLifecycleEventType.class)
    void listenerNeverWritesToTheOrdersRepository(OrderLifecycleEventType type) {
        // when
        handle(type);

        // then
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void orderAcceptedEventIsSkippedWhenOrderIsCancelled() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Cancelled);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void shipmentCreatedEventCallsShipOrderWithShipmentData() {
        // given
        Shipment shipment = mock(Shipment.class);
        when(shipment.hasShippingData()).thenReturn(true);
        when(shipment.getTrackingNo()).thenReturn("TRACK-9");
        when(shipment.getCarrier()).thenReturn("DPD");
        when(shipment.getTrackingUrl()).thenReturn("https://track.example/TRACK-9");
        when(order.getShipments()).thenReturn(List.of(shipment));

        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate("TRACK-9", "dpd-1", "DPD", "https://track.example/TRACK-9"));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void shipmentCreatedEventWithoutShippingDataCallsNothing() {
        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void shipmentCreatedEventForPersonalCollectionShipmentCallsShipOrderWithNullUpdate() {
        // given
        Shipment shipment = mock(Shipment.class);
        when(shipment.hasShippingData()).thenReturn(false);
        when(shipment.hasCollectionData()).thenReturn(true);
        when(order.getShipments()).thenReturn(List.of(shipment));

        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate(null, null, null, null));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void shipmentCreatedEventIsSkippedWhenOrderIsCompleted() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Completed);
        Shipment shipment = mock(Shipment.class);
        when(shipment.hasShippingData()).thenReturn(true);
        when(order.getShipments()).thenReturn(List.of(shipment));

        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void shipmentCreatedEventIsSkippedWhenOrderIsCancelled() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Cancelled);
        Shipment shipment = mock(Shipment.class);
        when(shipment.hasShippingData()).thenReturn(true);
        when(order.getShipments()).thenReturn(List.of(shipment));

        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void orderCompletedEventIsSkippedWhenOrderIsCancelled() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Cancelled);

        // when
        handle(OrderLifecycleEventType.OrderCompleted);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void orderCancelledEventCallsCancelOrder() {
        // when
        handle(OrderLifecycleEventType.OrderCancelled);

        // then
        verify(provider).cancelOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void orderCompletedEventCallsCompleteOrder() {
        // when
        handle(OrderLifecycleEventType.OrderCompleted);

        // then
        verify(provider).completeOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void deprecatedStatusChangeEventIsIgnored() {
        // when
        handle(OrderLifecycleEventType.StatusChange);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void nonMarketplaceOrderIsIgnored() {
        // given
        when(order.isMarketplaceOrder()).thenReturn(false);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void orderAcceptedEventCallsAcceptOrderWhenOrderIsAlreadyShipping() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Shipping);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void orderAcceptedEventCallsAcceptOrderWhenOrderIsAlreadyCompleted() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Completed);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void orderCancelledEventCancelsUsingPayloadWhenOrderDeleted() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID,
                OrderLifecycleEventType.OrderCancelled, EXTERNAL_ORDER_ID, MARKETPLACE));

        // then
        verify(provider).cancelOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void shipmentCreatedEventIsSkippedWhenOrderDeleted() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID,
                OrderLifecycleEventType.ShipmentCreated, EXTERNAL_ORDER_ID, MARKETPLACE));

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void orderAcceptedEventIsSkippedWhenOrderDeleted() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        handleDeleted(OrderLifecycleEventType.OrderAccepted);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void orderCompletedEventIsSkippedWhenOrderDeleted() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        handleDeleted(OrderLifecycleEventType.OrderCompleted);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void invoiceCreatedEventIsSkippedWhenOrderDeleted() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        handleDeleted(OrderLifecycleEventType.InvoiceCreated);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void deletedOrderWithoutMarketplaceInPayloadIsIgnored() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID,
                OrderLifecycleEventType.OrderCancelled, EXTERNAL_ORDER_ID, null));

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void removedMarketplaceIntegrationIsIgnored() {
        // given
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(null);

        // when
        handle(OrderLifecycleEventType.OrderCancelled);

        // then
        verifyNoInteractions(provider);
    }

    @Test
    void loggedOutMarketplaceIntegrationThrowsSoSqsRetriesUntilReauthentication() {
        // given
        MarketplaceIntegration integration = new MarketplaceIntegration(MARKETPLACE);
        integration.setLoggedIn(false);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(integration);

        // when / then
        assertThrows(IllegalStateException.class, () -> handle(OrderLifecycleEventType.OrderAccepted));
        verifyNoInteractions(provider);
    }

    @Test
    void missingProviderIsIgnored() {
        // given
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(null);

        // when / then
        handle(OrderLifecycleEventType.OrderAccepted);
    }

    @Test
    void invoiceCreatedEventCallsUpdateInvoiceWithClosingDocument() {
        // given
        Document document = mock(Document.class);
        when(document.hasNumberAndLink()).thenReturn(true);
        when(document.getType()).thenReturn(DocumentType.InvoiceVat);
        when(document.getNumber()).thenReturn("FV-1");
        when(document.getLink()).thenReturn("https://x/fv1");
        when(order.getDocuments()).thenReturn(List.of(document));

        // when
        handle(OrderLifecycleEventType.InvoiceCreated);

        // then
        verify(provider).updateInvoice(EXTERNAL_ORDER_ID, new InvoiceUpdate("FV-1", "https://x/fv1"));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void invoiceCreatedEventWithoutMatchingDocumentCallsNothing() {
        // when
        handle(OrderLifecycleEventType.InvoiceCreated);

        // then
        verifyNoInteractions(provider);
    }

    private void handle(OrderLifecycleEventType type) {
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID, type));
    }

    private void handleDeleted(OrderLifecycleEventType type) {
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID, type, EXTERNAL_ORDER_ID, MARKETPLACE));
    }

    private void handleReturn(OrderLifecycleEventType type, MarketplaceReturnAction action) {
        listener.handleMessage(new OrderLifecycleEvent(STORE_ID, ORDER_ID, type, EXTERNAL_ORDER_ID, MARKETPLACE, action));
    }

    @Test
    void returnAcceptedEventRefundsThroughProviderReturns() {
        // given
        when(provider.returns()).thenReturn(Optional.of(returns));
        MarketplaceReturnAction action = new MarketplaceReturnAction("rma-1", "r-1",
                List.of(new MarketplaceReturnAction.Item("SKU-1", 2)), true, "cmd-1", null);

        // when
        handleReturn(OrderLifecycleEventType.ReturnAccepted, action);

        // then
        ArgumentCaptor<ReturnRefund> captor = ArgumentCaptor.forClass(ReturnRefund.class);
        verify(returns).refundReturn(eq(EXTERNAL_ORDER_ID), eq("r-1"), captor.capture());
        assertEquals("cmd-1", captor.getValue().commandId());
        assertTrue(captor.getValue().refundDelivery());
        assertEquals("SKU-1", captor.getValue().items().get(0).manufacturerCode());
        assertEquals(2, captor.getValue().items().get(0).quantity());
    }

    @Test
    void returnAcceptedEventCarriesTheExternalReturnReferenceIntoTheRefund() {
        // given
        when(provider.returns()).thenReturn(Optional.of(returns));
        MarketplaceReturnAction action = new MarketplaceReturnAction("rma-1", "r-1",
                List.of(new MarketplaceReturnAction.Item("SKU-1", 2)), true, "cmd-1", null);
        action.setExternalReturnReference("XGQX/2026");

        // when
        handleReturn(OrderLifecycleEventType.ReturnAccepted, action);

        // then
        ArgumentCaptor<ReturnRefund> captor = ArgumentCaptor.forClass(ReturnRefund.class);
        verify(returns).refundReturn(eq(EXTERNAL_ORDER_ID), eq("r-1"), captor.capture());
        assertEquals("XGQX/2026", captor.getValue().referenceNumber());
    }

    @Test
    void returnRejectedEventRejectsThroughProviderReturns() {
        // given
        when(provider.returns()).thenReturn(Optional.of(returns));
        MarketplaceReturnAction action = new MarketplaceReturnAction("rma-1", "r-1", List.of(), false, null, "Damaged");

        // when
        handleReturn(OrderLifecycleEventType.ReturnRejected, action);

        // then
        ArgumentCaptor<ReturnRejection> captor = ArgumentCaptor.forClass(ReturnRejection.class);
        verify(returns).rejectReturn(eq("r-1"), captor.capture());
        assertEquals("Damaged", captor.getValue().reason());
    }

    @Test
    void returnEventsAreSkippedWhenProviderHasNoReturnsOrPayloadLacksAction() {
        // given
        when(provider.returns()).thenReturn(Optional.empty());

        // when
        handleReturn(OrderLifecycleEventType.ReturnAccepted,
                new MarketplaceReturnAction("rma-1", "r-1", List.of(), false, "cmd-1", null));
        when(provider.returns()).thenReturn(Optional.of(returns));
        handleReturn(OrderLifecycleEventType.ReturnRejected, null);

        // then
        verifyNoInteractions(returns);
    }

    @Test
    void returnEventsStillRequireAnAuthenticatedIntegration() {
        // given
        MarketplaceIntegration loggedOut = new MarketplaceIntegration(MARKETPLACE);
        loggedOut.setLoggedIn(false);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(loggedOut);

        // when / then
        assertThrows(IllegalStateException.class, () -> handleReturn(OrderLifecycleEventType.ReturnAccepted,
                new MarketplaceReturnAction("rma-1", "r-1", List.of(), false, "cmd-1", null)));
    }
}
