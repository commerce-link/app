package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.time.LocalDateTime;
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
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private Store store;
    @Mock
    private ShippingProvider shippingProvider;

    @InjectMocks
    private ShipmentCancelService shipmentCancelService;

    @BeforeEach
    void setupExecutorPassThrough() {
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
    @DisplayName("cancelShipping cancels carrier shipment, replaces order shipments and deletes ORDER_SHIPPING notification event")
    void cancelShippingCallsCarrierAndResetsOrderShipmentsAndDeletesNotificationEvent() {
        // given
        Shipment shipment = courierShipment(EXTERNAL_ID);
        Order order = orderWithShipments(shipment);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(shippingProviderFactory.get(store)).thenReturn(shippingProvider);

        // when
        shipmentCancelService.cancelShipping(ORDER_ID, STORE_ID);

        // then
        verify(shippingProvider).cancelShipment(EXTERNAL_ID);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        List<Shipment> savedShipments = orderCaptor.getValue().getShipments();
        assertThat(savedShipments).hasSize(1);
        assertThat(savedShipments.get(0).getType()).isEqualTo(ShipmentType.Courier);
        assertThat(savedShipments.get(0).getExternalId()).isNull();
        assertThat(savedShipments.get(0).getTrackingNo()).isNull();
        verify(orderEventsRepository).deleteByOrderIdAndName(ORDER_ID, EmailNotificationType.ORDER_SHIPPING.name());
    }

    private Order orderWithShipments(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setShipments(List.of(shipments));
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
