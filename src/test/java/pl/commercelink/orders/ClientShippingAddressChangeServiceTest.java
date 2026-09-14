package pl.commercelink.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.ClientShippingAddressChangeException.Reason;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.event.OrderEvent;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.orders.notifications.OrderShippingAddressChangedEmailNotification;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.starter.email.EmailNotification;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientShippingAddressChangeServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private EmailClient emailClient;

    private ClientShippingAddressChangeService service;

    @BeforeEach
    void setUp() {
        service = new ClientShippingAddressChangeService(ordersRepository, orderEventsRepository, emailClient, "https://app.example.com");
        when(orderEventsRepository.hasEvent(any(), any(), any())).thenReturn(false);
        when(emailClient.send(eq(STORE_ID), eq(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED), any(EmailNotification.class)))
                .thenReturn(true);
    }

    @Test
    @DisplayName("isEditable is true for a courier order before shipping with both flags on and no label")
    void editableWhenAllRulesPass() {
        // when / then
        assertThat(service.isEditable(editableOrder(), store(true, true))).isTrue();
    }

    @Test
    @DisplayName("isEditable is false when the client shipping address change flag is off")
    void notEditableWhenChangeFlagOff() {
        // when / then
        assertThat(service.isEditable(editableOrder(), store(true, false))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false when the client order page flag is off even if the change flag is on")
    void notEditableWhenPageFlagOff() {
        // when / then
        assertThat(service.isEditable(editableOrder(), store(false, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false for pickup point delivery")
    void notEditableForPickupPoint() {
        // given
        Order order = editableOrder();
        Shipment pickup = new Shipment(ShipmentType.PickupPoint);
        pickup.setCollectionPointCode("PKT-1");
        order.setShipments(List.of(pickup));

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false for personal collection")
    void notEditableForPersonalCollection() {
        // given
        Order order = editableOrder();
        order.setShipments(List.of(new Shipment(ShipmentType.PersonalCollection)));

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false once the order is in Shipping status")
    void notEditableAfterShipping() {
        // given
        Order order = editableOrder();
        order.setStatus(OrderStatus.Shipping);

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false once a shipment has a tracking number")
    void notEditableWithTrackingNo() {
        // given
        Order order = editableOrder();
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setTrackingNo("TRK-1");
        order.setShipments(List.of(courier));

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false once a shipment has an external label id")
    void notEditableWithExternalId() {
        // given
        Order order = editableOrder();
        Shipment courier = new Shipment(ShipmentType.Courier);
        courier.setExternalId("ext-1");
        order.setShipments(List.of(courier));

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false for marketplace orders")
    void notEditableForMarketplaceOrder() {
        // given
        Order order = editableOrder();
        order.setExternalOrderId("ALLEGRO-1");
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false without a billing e-mail to send the code to")
    void notEditableWithoutBillingEmail() {
        // given
        Order order = editableOrder();
        order.getBillingDetails().setEmail("");

        // when / then
        assertThat(service.isEditable(order, store(true, true))).isFalse();
    }

    @Test
    @DisplayName("isEditable is false after the client already changed the address once")
    void notEditableAfterPreviousClientChange() {
        // given
        when(orderEventsRepository.hasEvent(ORDER_ID, EventType.email, ClientShippingAddressChangeService.CHANGED_EVENT))
                .thenReturn(true);

        // when / then
        assertThat(service.isEditable(editableOrder(), store(true, true))).isFalse();
    }

    @Test
    @DisplayName("validate rejects a different delivery country")
    void validateRejectsCountryChange() {
        // given
        ShippingDetails requested = requestedAddress();
        requested.setCountry("DE");

        // when / then
        assertThatThrownBy(() -> service.validate(editableOrder(), requested))
                .isInstanceOf(ClientShippingAddressChangeException.class)
                .extracting("reason").isEqualTo(Reason.COUNTRY_CHANGED);
    }

    @Test
    @DisplayName("validate rejects an address with a missing required field")
    void validateRejectsIncompleteAddress() {
        // given
        ShippingDetails requested = requestedAddress();
        requested.setCity(" ");

        // when / then
        assertThatThrownBy(() -> service.validate(editableOrder(), requested))
                .isInstanceOf(ClientShippingAddressChangeException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_ADDRESS);
    }

    @Test
    @DisplayName("validate rejects HTML in address fields")
    void validateRejectsHtml() {
        // given
        ShippingDetails requested = requestedAddress();
        requested.setStreetAndNumber("<script>alert(1)</script>");

        // when / then
        assertThatThrownBy(() -> service.validate(editableOrder(), requested))
                .isInstanceOf(ClientShippingAddressChangeException.class)
                .extracting("reason").isEqualTo(Reason.INVALID_ADDRESS);
    }

    @Test
    @DisplayName("validate keeps the previous country and id and trims the submitted fields")
    void validateKeepsCountryAndIdAndTrims() {
        // given
        Order order = editableOrder();
        order.getShippingDetails().setId("addr-1");
        ShippingDetails requested = requestedAddress();
        requested.setCountry(null);
        requested.setCity("  Kraków ");

        // when
        ShippingDetails validated = service.validate(order, requested);

        // then
        assertThat(validated.getCountry()).isEqualTo("PL");
        assertThat(validated.getId()).isEqualTo("addr-1");
        assertThat(validated.getCity()).isEqualTo("Kraków");
        assertThat(validated.getStreetAndNumber()).isEqualTo("Nowa 2");
    }

    @Test
    @DisplayName("change saves the order with the new address and records the e-mail event once the confirmation is sent")
    void changeSavesOrderAndEmailEvent() {
        // given
        Order order = editableOrder();

        // when
        service.change(order, requestedAddress(), store(true, true));

        // then
        assertThat(order.getShippingDetails().getStreetAndNumber()).isEqualTo("Nowa 2");
        verify(ordersRepository).save(order);
        ArgumentCaptor<OrderEvent> events = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventsRepository, times(1)).save(events.capture());
        assertThat(events.getValue().getType()).isEqualTo(EventType.email);
        assertThat(events.getValue().getName()).isEqualTo("ORDER_SHIPPING_ADDRESS_CHANGED");
    }

    @Test
    @DisplayName("isEditable is false when the store has not enabled the verification code notification")
    void notEditableWithoutVerificationCodeTemplate() {
        // given
        Store store = store(true, true);
        store.getClientNotificationsConfiguration().disableNotification(EmailNotificationType.CLIENT_VERIFICATION_CODE);

        // when / then
        assertThat(service.isEditable(editableOrder(), store)).isFalse();
    }

    @Test
    @DisplayName("isEditable is false when the store has not enabled the address changed notification")
    void notEditableWithoutAddressChangedTemplate() {
        // given
        Store store = store(true, true);
        store.getClientNotificationsConfiguration().disableNotification(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED);

        // when / then
        assertThat(service.isEditable(editableOrder(), store)).isFalse();
    }

    @Test
    @DisplayName("change e-mails the billing address and the previous shipping e-mail when it differs")
    void changeNotifiesBillingAndPreviousShippingEmail() {
        // given
        Order order = editableOrder();
        order.getShippingDetails().setEmail("old-recipient@example.com");

        // when
        service.change(order, requestedAddress(), store(true, true));

        // then
        ArgumentCaptor<EmailNotification> messages = ArgumentCaptor.forClass(EmailNotification.class);
        verify(emailClient, times(2)).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED), messages.capture());
        assertThat(messages.getAllValues()).extracting(EmailNotification::getRecipientEmail)
                .containsExactly("payer@example.com", "old-recipient@example.com");
        OrderShippingAddressChangedEmailNotification first = (OrderShippingAddressChangedEmailNotification) messages.getAllValues().get(0);
        assertThat(first.getPreviousShippingDetails().getStreetAndNumber()).isEqualTo("Stara 1");
        assertThat(first.getNewShippingDetails().getStreetAndNumber()).isEqualTo("Nowa 2");
        assertThat(first.getOrderStatusLink()).isEqualTo("https://app.example.com/store/store-1/client/order/order-1");
        assertThat(first.getContactEmail()).isEqualTo("shop@example.com");
    }

    @Test
    @DisplayName("change sends a single e-mail when the previous shipping e-mail equals the billing e-mail")
    void changeSendsSingleEmailWhenAddressesMatch() {
        // given
        Order order = editableOrder();
        order.getShippingDetails().setEmail("Payer@example.com");

        // when
        service.change(order, requestedAddress(), store(true, true));

        // then
        verify(emailClient, times(1)).send(eq(STORE_ID), eq(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED), any());
    }

    @Test
    @DisplayName("change records no e-mail event when the confirmation could not be sent")
    void changeRecordsNoEventWhenEmailNotSent() {
        // given
        when(emailClient.send(any(), any(), any())).thenReturn(false);

        // when
        service.change(editableOrder(), requestedAddress(), store(true, true));

        // then
        verify(ordersRepository).save(any(Order.class));
        verify(orderEventsRepository, never()).save(any());
    }

    @Test
    @DisplayName("change refuses and saves nothing when the order is not editable")
    void changeRefusesWhenNotEditable() {
        // given
        Order order = editableOrder();
        order.setStatus(OrderStatus.Shipping);

        // when / then
        assertThatThrownBy(() -> service.change(order, requestedAddress(), store(true, true)))
                .isInstanceOf(ClientShippingAddressChangeException.class)
                .extracting("reason").isEqualTo(Reason.NOT_EDITABLE);
        verify(ordersRepository, never()).save(any());
        verify(orderEventsRepository, never()).save(any());
        verify(emailClient, never()).send(any(), any(), any());
    }

    private static Order editableOrder() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(OrderStatus.Assembly);
        BillingDetails billing = BillingDetails._default();
        billing.setName("Jan");
        billing.setEmail("payer@example.com");
        order.setBillingDetails(billing);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Jan");
        shipping.setSurname("Kowalski");
        shipping.setStreetAndNumber("Stara 1");
        shipping.setPostalCode("00-001");
        shipping.setCity("Warszawa");
        shipping.setCountry("PL");
        shipping.setEmail("payer@example.com");
        shipping.setPhone("+48123456789");
        order.setShippingDetails(shipping);
        order.setShipments(List.of(new Shipment(ShipmentType.Courier)));
        return order;
    }

    private static ShippingDetails requestedAddress() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Nowa 2");
        details.setPostalCode("30-001");
        details.setCity("Kraków");
        details.setCountry("PL");
        details.setEmail("jan@example.com");
        details.setPhone("+48123456789");
        return details;
    }

    private static Store store(boolean pageEnabled, boolean changeEnabled) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setClientOrderPageEnabled(pageEnabled);
        configuration.setClientShippingAddressChangeEnabled(changeEnabled);
        store.setFulfilmentConfiguration(configuration);
        BillingDetails storeBilling = BillingDetails._default();
        storeBilling.setEmail("shop@example.com");
        store.setBillingDetails(storeBilling);
        ClientNotificationsConfiguration notifications = new ClientNotificationsConfiguration();
        notifications.enableNotification(EmailNotificationType.CLIENT_VERIFICATION_CODE, "ClientVerificationCodeTemplate");
        notifications.enableNotification(EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED, "OrderShippingAddressChangedTemplate");
        store.setClientNotificationsConfiguration(notifications);
        return store;
    }
}
