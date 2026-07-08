package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.ShipmentUpdate;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderLifecycleEventListenerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ORDER_ID = "EXT-1";
    private static final String MARKETPLACE = "Empik";

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private MarketplaceProviderFactory providerFactory;

    @Mock private Store store;
    @Mock private Order order;
    @Mock private OrderSource source;
    @Mock private MarketplaceProvider provider;

    @InjectMocks
    private MarketplaceOrderLifecycleEventListener listener;

    @BeforeEach
    void setUpDefaults() {
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(order.isMarketplaceOrder()).thenReturn(true);
        when(order.getSource()).thenReturn(source);
        when(source.getName()).thenReturn(MARKETPLACE);
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
    void listenerNeverWritesToTheOrdersRepository() {
        // when
        handle(OrderLifecycleEventType.OrderAccepted);

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
        verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate("TRACK-9", "DPD", "https://track.example/TRACK-9"));
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
        verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate(null, null, null));
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
    void orderAcceptedEventCallsAcceptOrderWhenOrderIsStillInAssembly() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Assembly);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void shipmentCreatedEventDoesNotAcceptBeforeShipping() {
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
        verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate("TRACK-9", "DPD", "https://track.example/TRACK-9"));
        verify(provider, never()).acceptOrder(any());
    }

    @Test
    void orderCompletedEventDoesNotAcceptBeforeCompleting() {
        // given
        when(order.getStatus()).thenReturn(OrderStatus.Completed);

        // when
        handle(OrderLifecycleEventType.OrderCompleted);

        // then
        verify(provider).completeOrder(EXTERNAL_ORDER_ID);
        verify(provider, never()).acceptOrder(any());
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
}
