package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrdersShippingControllerTest {

    private static final String STORE_ID = "store-1";

    @Mock private OrdersRepository ordersRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private MessageSource messageSource;
    @Mock private ShippingService shippingService;

    @InjectMocks
    private OrdersShippingController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    static Order orderWithShipments(Shipment... shipments) {
        Order order = new Order(STORE_ID);
        order.setShippingDetails(new ShippingDetails());
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    @Test
    void aShipmentWithoutDataOpensTheShippingForm() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), model, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("shipping");
        assertThat(((ShippingForm) model.get("shippingForm")).getShippingEntityType()).isEqualTo("orders");
    }

    @Test
    void whenEveryShipmentHasDataTheOperatorReturnsToTheOrderWithAMessage() {
        // given
        Shipment sent = new Shipment(ShipmentType.Courier);
        sent.setCarrier("DPD");
        sent.setTrackingNo("T-1");
        sent.setShippedAt(java.time.LocalDateTime.now());
        Order order = orderWithShipments(sent);
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new java.util.HashMap<String, Object>(redirect.getFlashAttributes())).containsEntry("errorMessage", "shipping.error.all.defined");
    }

    @Test
    void anotherStoresOrderIsNotFound() {
        // given
        when(ordersRepository.findById(STORE_ID, "foreign")).thenReturn(null);
        ShippingForm form = new ShippingForm("foreign", "orders");

        // when / then
        assertThatThrownBy(() -> controller.initiate("foreign", new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.createShipping(form, new RedirectAttributesModelMap(), Locale.ENGLISH))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.loadTemplates(form, new ExtendedModelMap()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(shippingService);
    }
}
