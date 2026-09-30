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
import pl.commercelink.shipping.ShippingPageView;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.shipping.ShippingUnavailableException;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(shippingService.isAvailable(any())).thenReturn(true);
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
        assertThat(model.get("shippingPage")).isEqualTo(new ShippingPageView("/dashboard/orders/" + order.getOrderId(),
                "order.page.title", order.getShortenedOrderId(), "shipping.lead.order", order.getShortenedOrderId()));
    }

    @Test
    void anOrderWhoseOnlyShipmentWasRemovedStillOpensTheShippingForm() {
        // given: "Usuń" of the only shipment leaves no shipment (2026-09-30); the booking creates the shipment
        Order order = orderWithShipments();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), model, redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("shipping");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("errorMessage");
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

    @Test
    void courierPageWithoutAShippingProviderRedirectsWithTheReason() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(shippingService.isAvailable(any())).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes()))
                .containsEntry("errorMessage", "shipping.error.no.provider.order");
    }

    @Test
    void estimateWithoutAShippingProviderShowsAMessageNotAnError() {
        // given: an address typed in by hand, or the RMA and warehouse routes that reach the page without the check
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(shippingService.estimateServicePrices(any(), any(), any()))
                .thenThrow(new ShippingUnavailableException(STORE_ID));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.estimateShipping(new ShippingForm(order.getOrderId(), "orders"), model, Locale.ENGLISH);

        // then: the page again, with the reason as its alert and no offers
        assertThat(view).isEqualTo("shipping");
        assertThat(model.get("shippingUnavailable")).isEqualTo("shipping.error.no.provider.order");
        assertThat(model.get("errorMessage")).isNull();
        assertThat(model.get("servicePrices")).isNull();
    }

    @Test
    void bookingWithoutAShippingProviderReturnsToTheOrderWithTheReason() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(shippingService.createShipping(any(ShippingForm.class), any(), any()))
                .thenThrow(new ShippingUnavailableException(STORE_ID));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createShipping(new ShippingForm(order.getOrderId(), "orders"), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes()))
                .containsEntry("errorMessage", "shipping.error.no.provider.order");
    }

    @Test
    void aSecondCourierOrderForShipmentsWithShippingDataIsRefused() {
        // given: the first click (or another tab) already booked the courier, so the shipment has its data
        Shipment booked = new Shipment(ShipmentType.Courier);
        booked.setExternalId("ext-1");
        booked.setCarrier("DPD");
        booked.setTrackingNo("T-1");
        booked.setShippedAt(java.time.LocalDateTime.now());
        Order order = orderWithShipments(booked);
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createShipping(new ShippingForm(order.getOrderId(), "orders"), redirect, Locale.ENGLISH);

        // then: no second label is booked
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes()))
                .containsEntry("errorMessage", "shipping.error.all.defined");
        verify(shippingService, never()).createShipping(any(ShippingForm.class), any(), any());
    }
}
