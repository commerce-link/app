package pl.commercelink.orders.notifications;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.taxonomy.ProductCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderNotificationsServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "abc123-orderpart";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private EmailClient emailClient;

    @InjectMocks
    private OrderNotificationsService orderNotificationsService;

    @Test
    @DisplayName("send returns immediately and triggers no side effects when email notifications are disabled on the order")
    void sendReturnsImmediatelyWhenEmailNotificationsDisabled() {
        // given
        Order order = orderBase(OrderStatus.New);
        order.setEmailNotificationsEnabled(false);

        // when
        orderNotificationsService.send(order);

        // then
        verifyNoInteractions(emailClient, orderEventsRepository, ordersRepository, orderItemsRepository);
    }

    @Test
    @DisplayName("send dispatches qualifying ORDER_ASSEMBLY email and persists matching OrderEvent")
    void sendDispatchesQualifyingOrderAssemblyEmailAndPersistsEvent() {
        // given
        Order order = orderBase(OrderStatus.Assembly);
        order.updateEstimatedAssemblyAt(LocalDate.now().plusDays(2));
        when(orderEventsRepository.hasEvent(eq(ORDER_ID), eq(EventType.email), anyString())).thenReturn(false);
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_ASSEMBLY), any(EmailNotification.class)))
                .thenReturn(true);

        // when
        orderNotificationsService.send(order);

        // then
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventsRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getName()).isEqualTo(EmailNotificationType.ORDER_ASSEMBLY.name());
        assertThat(eventCaptor.getValue().getType()).isEqualTo(EventType.email);
    }

    @Test
    @DisplayName("send updates review status to InProgress and saves order when ORDER_REVIEW email dispatched")
    void sendUpdatesReviewStatusAndSavesOrderWhenReviewEmailDispatched() {
        // given
        Order order = orderBase(OrderStatus.Delivered);
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        Shipment delivered = new Shipment(ShipmentType.Courier);
        delivered.setShippedAt(LocalDateTime.now().minusDays(5));
        delivered.setDeliveredAt(LocalDateTime.now().minusDays(3));
        order.setShipments(List.of(delivered));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_REVIEW), any(EmailNotification.class)))
                .thenReturn(true);

        // when
        orderNotificationsService.send(order);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        OrderReview savedReview = orderCaptor.getValue().getReview();
        assertThat(savedReview.getStatus()).isEqualTo(OrderReviewStatus.InProgress);
        assertThat(savedReview.getReferenceNo()).isEqualTo("abc123");
        assertThat(savedReview.getRequestedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("send does not persist order when only non-review notifications were dispatched")
    void sendDoesNotPersistOrderWhenOnlyNonReviewNotificationsSent() {
        // given
        Order order = orderBase(OrderStatus.Shipping);
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setType(ShipmentType.Courier);
        courier.setCarrier("DHL");
        courier.setTrackingNo("TRK-123");
        courier.setShippedAt(LocalDateTime.now().minusHours(1));
        courier.setExternalId("PKG-1");
        order.setShipments(List.of(courier));
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_SHIPPING), any(EmailNotification.class)))
                .thenReturn(true);

        // when
        orderNotificationsService.send(order);

        // then
        verify(orderEventsRepository, times(1)).save(any(OrderEvent.class));
        verify(ordersRepository, never()).save(any());
    }

    private Order orderBase(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        order.setEmailNotificationsEnabled(true);
        order.setBillingDetails(BillingDetails._default());
        order.setShippingDetails(filledShippingDetails());
        return order;
    }

    private ShippingDetails filledShippingDetails() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Marszalkowska 1");
        details.setPostalCode("00-001");
        details.setCity("Warszawa");
        details.setCountry("PL");
        details.setEmail("jan@example.com");
        details.setPhone("+48123456789");
        return details;
    }
}
