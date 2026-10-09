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
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.ShippingForm;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.shipping.AllegroShippingView;
import pl.commercelink.shipping.ParcelForm;
import pl.commercelink.shipping.ShipmentCreationService;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.shipping.ShipmentOwnerType;
import pl.commercelink.shipping.api.ShipmentRequest;
import pl.commercelink.shipping.ShippingIntegrationChoice;
import pl.commercelink.shipping.ShippingIntegrationChoiceView;
import pl.commercelink.shipping.ShippingIntegrationOption;
import pl.commercelink.shipping.ShippingIntegrationViews;
import pl.commercelink.shipping.api.DeliveryPoint;
import pl.commercelink.shipping.api.DeliveryType;
import pl.commercelink.shipping.api.PackageOption;
import pl.commercelink.shipping.api.ShipmentProposal;
import pl.commercelink.stores.PackageTemplate;
import java.math.BigDecimal;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
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
    @Mock private ShipmentCreationService shipmentCreationService;
    @Mock private ShippingIntegrationChoice shippingIntegrationChoice;
    @Mock private ShippingIntegrationViews shippingIntegrationViews;

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
        when(shippingService.isAvailableFor(any(), any())).thenReturn(true);
        when(shippingIntegrationChoice.forOrder(any(), any())).thenReturn(List.of(
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null).suggestedCopy()));
        when(shippingIntegrationViews.choice(any(), any(), any())).thenAnswer(invocation ->
                new ShippingIntegrationChoiceView(invocation.getArgument(0), invocation.getArgument(1), null, null));
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
        String view = controller.initiate(order.getOrderId(), null, null, model, new RedirectAttributesModelMap(), Locale.ENGLISH);

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
        String view = controller.initiate(order.getOrderId(), null, null, model, redirect, Locale.ENGLISH);

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
        String view = controller.initiate(order.getOrderId(), null, null, new ExtendedModelMap(), redirect, Locale.ENGLISH);

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
        assertThatThrownBy(() -> controller.initiate("foreign", null, null, new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH))
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
        when(shippingService.isAvailableFor(any(), eq(order))).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), null, null, new ExtendedModelMap(), redirect, Locale.ENGLISH);

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
        when(shippingService.buildRequest(any(ShippingForm.class), any(), any()))
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
        verify(shipmentCreationService, never()).start(any(), any(), any(), any());
    }

    @Test
    void aBookingWhileAShipmentIsBeingCreatedIsRefused() {
        // given: a creation in flight next to a shipment still without data
        Shipment creating = new Shipment(ShipmentType.Courier);
        creating.setCreation(ShipmentCreationState.pending("cmd-1", java.time.LocalDateTime.now()));
        Order order = orderWithShipments(creating, new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createShipping(new ShippingForm(order.getOrderId(), "orders"), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes()))
                .containsEntry("errorMessage", "shipping.error.creating");
        verify(shipmentCreationService, never()).start(any(), any(), any(), any());
    }

    static Order allegroOrder() {
        Order order = orderWithShipments(new Shipment(ShipmentType.PickupPoint));
        order.setExternalOrderId("29a9b8c0-a87a-11f1-8456-8d3ada2e8e1c");
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        order.setTotalPrice(919.99);
        return order;
    }

    static ShipmentProposal proposal() {
        return ShipmentProposal.available("Allegro One Box, One Kurier", "ALLEGRO", new DeliveryPoint("ALBOX-WAW-0231"),
                DeliveryType.LOCKER, List.of(new PackageOption("PACKAGE", new BigDecimal("64"), new BigDecimal("38"),
                        new BigDecimal("41"), new BigDecimal("25"))), new BigDecimal("5000"), new BigDecimal("5000"));
    }

    private void allegroSuggested(Order order) {
        when(shippingIntegrationChoice.forOrder(any(), eq(order))).thenReturn(List.of(
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null),
                ShippingIntegrationOption.available("allegro", "Wysyłam z Allegro", proposal()).suggestedCopy()));
        when(shippingIntegrationViews.allegro(any(), eq(order), any(), any())).thenReturn(new AllegroShippingView(
                "Allegro One Box, One Kurier", "One by Allegro", "ALBOX-WAW-0231", "shipping.allegro.deliveryType.LOCKER",
                "Katarzyna Wiśniewska", "limits", "cod", "insurance", "shipping.allegro.labelFormat.PDF_A6",
                "/dashboard/store/shipping/allegro"));
    }

    @Test
    void allegroOrderOpensTheAllegroFormSuggestedByTheChoice() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.initiate(order.getOrderId(), null, null, model, new RedirectAttributesModelMap(),
                Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("shipping");
        assertThat(model.get("allegroShipping")).isNotNull();
        assertThat(((ShippingForm) model.get("shippingForm")).getProvider()).isEqualTo("allegro");
        assertThat(((ShippingIntegrationChoiceView) model.get("integrationChoice")).selected()).isEqualTo("allegro");
    }

    @Test
    void theOperatorCanSwitchAnAllegroOrderToTheDefaultIntegration() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.initiate(order.getOrderId(), "furgonetka", null, model, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(model.get("allegroShipping")).isNull();
        assertThat(((ShippingForm) model.get("shippingForm")).getProvider()).isEqualTo("furgonetka");
    }

    @Test
    void anOnlinePaidAllegroOrderThatLooksUnpaidKeepsCashOnDeliveryUnchecked() {
        ShippingForm form = allegroFormFor(PaymentSource.OnlinePayment);

        assertThat(form.isCashOnDelivery()).isFalse();
        assertThat(form.getCashOnDeliveryAmount()).isEqualTo(919.99);
    }

    @Test
    void aCashOnDeliveryAllegroOrderStartsWithCashOnDeliveryCheckedForTheUnpaidAmount() {
        ShippingForm form = allegroFormFor(PaymentSource.CashOnDelivery);

        assertThat(form.isCashOnDelivery()).isTrue();
        assertThat(form.getCashOnDeliveryAmount()).isEqualTo(919.99);
    }

    private ShippingForm allegroFormFor(PaymentSource source) {
        Order order = allegroOrder();
        order.getPayments().clear();
        order.addPayment(new Payment(source));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        ExtendedModelMap model = new ExtendedModelMap();
        controller.initiate(order.getOrderId(), null, null, model, new RedirectAttributesModelMap(), Locale.ENGLISH);
        return (ShippingForm) model.get("shippingForm");
    }

    @Test
    void theAllegroFormStartsWithOneParcelOfTheDefaultTemplateAndTheUnpaidAmount() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        Store store = new Store();
        PackageTemplate template = new PackageTemplate("Karton M", List.of());
        template.setId("t-m");
        template.setDefault(true);
        store.setShippingConfiguration(new pl.commercelink.stores.ShippingConfiguration());
        store.getShippingConfiguration().getPackageTemplates().add(template);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(shippingService.retrieveParcelsListBasedOnPackageTemplate(919.99, "t-m", store)).thenReturn(new ArrayList<>(List.of(
                new ParcelForm(30, 20, 15, 2, 920, "Akcesoria", "package"),
                new ParcelForm(10, 10, 10, 1, 920, "Drugi", "package"))));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.initiate(order.getOrderId(), null, null, model, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        ShippingForm form = (ShippingForm) model.get("shippingForm");
        assertThat(form.getParcels()).hasSize(1);
        assertThat(form.getPackageTemplateId()).isEqualTo("t-m");
    }

    static ShippingForm allegroForm(String orderId) {
        ShippingForm form = new ShippingForm(orderId, "orders");
        form.setProvider("allegro");
        form.setPickUpAddressId("addr-1");
        form.setParcels(new ArrayList<>(List.of(new ParcelForm(30, 20, 15, 2, 920, "Akcesoria", "package"))));
        return form;
    }

    @Test
    void createForAnOrderOutsideTheAllegroChoiceReturnsToTheOrderWithTheUnavailableNotice() {
        // given: a shop order; the post claims Wysyłam z Allegro anyway (a stale or forged form)
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(shippingIntegrationChoice.forOrder(any(), eq(order))).thenReturn(List.of(
                ShippingIntegrationOption.unavailable("allegro", "Wysyłam z Allegro", "shipping.integration.reason.allegroOnly", null),
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null).suggestedCopy()));
        when(messageSource.getMessage(eq("shipping.integration.error.unavailable"), any(), any(Locale.class)))
                .thenReturn("unavailable");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createAllegroShipping(order.getOrderId(), allegroForm(order.getOrderId()),
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes())).containsEntry("errorMessage", "unavailable");
        verifyNoInteractions(shipmentCreationService);
        verify(shippingService, never()).buildAllegroRequest(any(), any(), any());
    }

    @Test
    void createWhenTheAllegroProposalIsUnavailableReturnsToTheOrderWithTheUnavailableNotice() {
        // given: an Allegro order whose delivery method Allegro does not ship
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        when(shippingIntegrationChoice.forOrder(any(), eq(order))).thenReturn(List.of(
                ShippingIntegrationOption.unavailable("allegro", "Wysyłam z Allegro", "shipping.integration.reason.proposal", "x"),
                ShippingIntegrationOption.available("furgonetka", "Furgonetka", null).suggestedCopy()));
        when(messageSource.getMessage(eq("shipping.integration.error.unavailable"), any(), any(Locale.class)))
                .thenReturn("unavailable");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createAllegroShipping(order.getOrderId(), allegroForm(order.getOrderId()),
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes())).containsEntry("errorMessage", "unavailable");
        verifyNoInteractions(shipmentCreationService);
    }

    @Test
    void theCashOnDeliveryAmountIsPrefilledRoundedToGrosze() {
        // given: 79.98 - 20.0 is 59.980000000000004 in a double
        Order order = allegroOrder();
        order.setTotalPrice(79.98);
        order.addPayment(new Payment("ref", "paid", PaymentSource.OnlinePayment, 20.0, 0));
        assertThat(order.getUnpaidAmount()).isNotEqualTo(59.98);
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.initiate(order.getOrderId(), null, null, model, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        ShippingForm form = (ShippingForm) model.get("shippingForm");
        assertThat(form.getCashOnDeliveryAmount()).isEqualTo(59.98);
    }

    @Test
    void forgedAllegroProviderOnTheDefaultStepsIsRefused() {
        // given
        Order order = orderWithShipments(new Shipment(ShipmentType.Courier));
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        Store store = new Store();
        store.setConfigurationValue(pl.commercelink.stores.IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.createShipping(allegroForm(order.getOrderId()), redirect, Locale.ENGLISH);

        // then
        assertThat(new HashMap<String, Object>(redirect.getFlashAttributes()))
                .containsEntry("errorMessage", "shipping.integration.error.unavailable");
        verifyNoInteractions(shipmentCreationService);
    }

    @Test
    void allegroCreateStartsTheCommandWithThePlaceholderOfTheBuyersMethod() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        ShipmentRequest request = ShipmentRequest.builder().build();
        when(shippingService.buildAllegroRequest(any(), any(), eq(order))).thenReturn(request);
        when(shipmentCreationService.start(any(), eq(request), any(), any(), eq("allegro")))
                .thenReturn(ShipmentCreationStart.startedForTest());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = controller.createAllegroShipping(order.getOrderId(), allegroForm(order.getOrderId()),
                new ExtendedModelMap(), redirect, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/" + order.getOrderId());
        verify(shipmentCreationService).start(argThat(seed -> seed.getOwnerType() == ShipmentOwnerType.ORDER
                        && order.getOrderId().equals(seed.getOwnerId()) && "addr-1".equals(seed.getPickUpAddressId())),
                eq(request), any(), argThat(placeholder -> "Allegro One Box, One Kurier".equals(placeholder.getCarrier())
                        && "ALBOX-WAW-0231".equals(placeholder.getCollectionPointCode())
                        && placeholder.getType() == ShipmentType.PickupPoint), eq("allegro"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void allegroCreateWithParcelOverTheLimitRendersTheFormWithTheReason() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        allegroSuggested(order);
        when(shippingIntegrationViews.errors(any(), any())).thenReturn(Map.of("parcel", "Paczka przekracza wagę metody: najwyżej 25 kg."));
        ShippingForm form = allegroForm(order.getOrderId());
        form.getParcels().get(0).setWeight(30);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.createAllegroShipping(order.getOrderId(), form, model, new RedirectAttributesModelMap(),
                Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("shipping");
        assertThat(((Map<String, String>) model.get("allegroErrors"))).containsKey("parcel");
        verifyNoInteractions(shipmentCreationService);
        // the choice is asked once in the request: the page reuses it
        verify(shippingIntegrationChoice, times(1)).forOrder(any(), eq(order));
    }

    @Test
    void defaultStepsOfAMarketplaceOrderCarryItsReference() {
        // given
        Order order = allegroOrder();
        when(ordersRepository.findById(STORE_ID, order.getOrderId())).thenReturn(order);
        Store store = new Store();
        store.setConfigurationValue(pl.commercelink.stores.IntegrationType.SHIPPING_PROVIDER, "furgonetka");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ShipmentRequest built = ShipmentRequest.builder().carrierId("svc-1").build();
        when(shippingService.buildRequest(any(), any(), any())).thenReturn(built);
        when(shipmentCreationService.start(any(), any(), any(), any())).thenReturn(ShipmentCreationStart.startedForTest());
        ShippingForm form = new ShippingForm(order.getOrderId(), "orders");
        form.setProvider("furgonetka");

        // when
        controller.createShipping(form, new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        verify(shipmentCreationService).start(any(), argThat(r -> r.orderReference() != null
                && order.getExternalOrderId().equals(r.orderReference().externalOrderId())), any(), any());
    }
}
