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
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
        when(order.isMarketplaceAccepted()).thenReturn(true);
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
    }

    @Test
    void orderAcceptedEventCallsAcceptOrderAndRecordsAcceptance() {
        // given
        when(order.isMarketplaceAccepted()).thenReturn(false);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verify(order).markMarketplaceAccepted();
        verify(ordersRepository).save(order);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void orderAcceptedEventIsSkippedWhenAcceptanceAlreadyRecorded() {
        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verifyNoInteractions(provider);
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void orderAcceptedEventIsSkippedWhenOrderIsCancelled() {
        // given
        when(order.isMarketplaceAccepted()).thenReturn(false);
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
        when(order.isMarketplaceAccepted()).thenReturn(false);
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
        when(order.isMarketplaceAccepted()).thenReturn(false);
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
        when(order.isMarketplaceAccepted()).thenReturn(false);
        when(order.getStatus()).thenReturn(OrderStatus.Assembly);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
        verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void shipmentCreatedEventAcceptsOrderBeforeShippingWhenAcceptanceNotRecorded() {
        // given
        when(order.isMarketplaceAccepted()).thenReturn(false);
        Shipment shipment = mock(Shipment.class);
        when(shipment.hasShippingData()).thenReturn(true);
        when(shipment.getTrackingNo()).thenReturn("TRACK-9");
        when(shipment.getCarrier()).thenReturn("DPD");
        when(shipment.getTrackingUrl()).thenReturn("https://track.example/TRACK-9");
        when(order.getShipments()).thenReturn(List.of(shipment));

        // when
        handle(OrderLifecycleEventType.ShipmentCreated);

        // then
        InOrder inOrder = inOrder(provider);
        inOrder.verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        inOrder.verify(provider).shipOrder(EXTERNAL_ORDER_ID, new ShipmentUpdate("TRACK-9", "DPD", "https://track.example/TRACK-9"));
        verify(order).markMarketplaceAccepted();
        verify(ordersRepository).save(order);
    }

    @Test
    void orderCompletedEventAcceptsOrderBeforeCompletingWhenAcceptanceNotRecorded() {
        // given
        when(order.isMarketplaceAccepted()).thenReturn(false);
        when(order.getStatus()).thenReturn(OrderStatus.Completed);

        // when
        handle(OrderLifecycleEventType.OrderCompleted);

        // then
        InOrder inOrder = inOrder(provider);
        inOrder.verify(provider).acceptOrder(EXTERNAL_ORDER_ID);
        inOrder.verify(provider).completeOrder(EXTERNAL_ORDER_ID);
    }

    @Test
    void inactiveMarketplaceIntegrationIsIgnored() {
        // given
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(false);

        // when
        handle(OrderLifecycleEventType.OrderAccepted);

        // then
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
}
