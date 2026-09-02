package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.api.ShippingWebhookResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShippingWebhookRegistryTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 9, 2, 13, 30);

    @Mock private ShippingProviderFactory shippingProviderFactory;
    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderLifecycle orderLifecycle;
    @Mock private RMARepository rmaRepository;
    @Mock private GoodsOutEventPublisher goodsOutEventPublisher;
    @Mock private OrderEventsRepository orderEventsRepository;
    @Mock private ShipmentTrackingsRepository shipmentTrackingsRepository;
    @Mock private Store store;

    private ShippingWebhookRegistry registry;

    @BeforeEach
    void setUp() {
        when(shippingProviderFactory.availableProviders()).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        registry = new ShippingWebhookRegistry(shippingProviderFactory, storesRepository, ordersRepository,
                orderLifecycle, rmaRepository, goodsOutEventPublisher, orderEventsRepository, shipmentTrackingsRepository);
    }

    private static Shipment courier(String trackingNo) {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setCarrier("DPD");
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(DELIVERED_AT.minusDays(2));
        return shipment;
    }

    private void process(ShippingWebhookResult result) {
        ReflectionTestUtils.invokeMethod(registry, "processResult", STORE_ID, result);
    }

    @Test
    void deliveredWebhookFillsDeliveredAtOnOrderFoundThroughIndexEvenWhenOrderIsStillInAssembly() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        order.setStatus(OrderStatus.Assembly);
        order.setShipments(new ArrayList<>(List.of(courier("PKG-1"), courier("PKG-2"))));
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "PKG-1", "order-1", null, DELIVERED_AT)));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        process(new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.DELIVERED, DELIVERED_AT));

        // then
        assertThat(order.getShipments().get(0).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        assertThat(order.getShipments().get(1).getDeliveredAt()).isNull();
        verify(orderLifecycle).update(order);
        verify(orderEventsRepository).save(any());
        verify(ordersRepository, never()).findAllByStoreIdAndStatus(any(), any());
    }

    @Test
    void unknownTrackingNoIsIgnored() {
        // given
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-X")).thenReturn(Optional.empty());

        // when
        process(new ShippingWebhookResult("PKG-X", ShippingWebhookResult.ShipmentState.DELIVERED, DELIVERED_AT));

        // then
        verifyNoInteractions(ordersRepository, rmaRepository, orderLifecycle);
    }

    @Test
    void collectedWebhookPublishesGoodsOut() {
        // given
        Order order = new Order(STORE_ID);
        order.setOrderId("order-1");
        order.setShipments(new ArrayList<>(List.of(courier("PKG-1"))));
        when(shipmentTrackingsRepository.find(STORE_ID, "PKG-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "PKG-1", "order-1", null, DELIVERED_AT)));
        when(ordersRepository.findById(STORE_ID, "order-1")).thenReturn(order);

        // when
        process(new ShippingWebhookResult("PKG-1", ShippingWebhookResult.ShipmentState.COLLECTED, DELIVERED_AT));

        // then
        verify(goodsOutEventPublisher).publish(order, "System");
        verify(orderLifecycle, never()).update(order);
    }

    @Test
    void deliveredWebhookForRmaMarksItemsReceivedWhenAllShipmentsDelivered() {
        // given
        RMA rma = new RMA();
        rma.setRmaId("rma-1");
        rma.setStoreId(STORE_ID);
        rma.setStatus(RMAStatus.WaitingForItems);
        rma.setShipments(new ArrayList<>(List.of(courier("RET-1"))));
        when(shipmentTrackingsRepository.find(STORE_ID, "RET-1"))
                .thenReturn(Optional.of(new ShipmentTracking(STORE_ID, "RET-1", null, "rma-1", DELIVERED_AT)));
        when(rmaRepository.findById(STORE_ID, "rma-1")).thenReturn(rma);

        // when
        process(new ShippingWebhookResult("RET-1", ShippingWebhookResult.ShipmentState.DELIVERED, DELIVERED_AT));

        // then
        assertThat(rma.getShipments().get(0).getDeliveredAt()).isEqualTo(DELIVERED_AT);
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.ItemsReceived);
        verify(rmaRepository).save(rma);
    }
}
