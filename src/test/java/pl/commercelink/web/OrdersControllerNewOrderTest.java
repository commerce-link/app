package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.imports.BasketOrderImporter;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.orders.NewOrderPage;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersControllerNewOrderTest {

    private static final String STORE_ID = "store-1";
    private static final String BASKET_ID = "8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b";

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private BasketOrderImporter basketOrderImporter;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private OrdersController ordersController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void getRendersThePageWithTheBasketValues() {
        // given
        Basket basket = basket(BasketType.Basket);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(messageSource.getMessage(anyString(), isNull(), anyString(), eq(Locale.ENGLISH)))
                .thenAnswer(call -> "label " + call.getArgument(0));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.submitOrderBasedOnBasket(BASKET_ID, model, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("newOrder_clientDataCollection");
        NewOrderPage page = (NewOrderPage) model.get("page");
        assertThat(page.backHref()).isEqualTo("/dashboard/basket/view/" + BASKET_ID);
        assertThat(page.deliveryName()).isEqualTo("Kurier DPD");
        ClientDataDto form = (ClientDataDto) model.get("form");
        assertThat(form.getOrderReference()).isEqualTo(BASKET_ID);
        assertThat(form.getShipmentType()).isEqualTo(ShipmentType.Courier);
        assertThat(form.getPaymentSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(form.getBillingDetails().getCity()).isEqualTo("Warszawa");
        assertThat(model.get("shippingAddress")).isEqualTo("same");
        assertThat(model.get("billing")).isNotNull();
        assertThat(model.get("shipping")).isNotNull();
        assertThat((Map<?, ?>) model.get("errors")).isEmpty();
        assertThat((List<PickerOption>) model.get("paymentOptions"))
                .hasSize(PaymentSource.values().length)
                .startsWith(new PickerOption("BankTransfer", "label PaymentSource.BankTransfer"));
    }

    @Test
    void getOfMissingBasketIsNotFound() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.empty());

        // when
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> ordersController.submitOrderBasedOnBasket(BASKET_ID, new ExtendedModelMap(), Locale.ENGLISH));

        // then
        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getOfTemplateIsNotFound() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket(BasketType.OfferTemplate)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());

        // when
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> ordersController.submitOrderBasedOnBasket(BASKET_ID, new ExtendedModelMap(), Locale.ENGLISH));

        // then
        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void postWithErrorsRendersThePageAndCreatesNothing() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket(BasketType.Basket)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ClientDataDto dto = posted(ShipmentType.Courier);
        dto.getBillingDetails().setCity(" ");
        dto.getBillingDetails().setStreetAndNumber("Typed 5");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "other", model,
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("newOrder_clientDataCollection");
        assertThat((Map<String, String>) model.get("errors"))
                .containsEntry("billingDetails.city", "billing.city.required")
                .containsKey("shippingDetails.streetAndNumber");
        ClientDataDto form = (ClientDataDto) model.get("form");
        assertThat(form.getBillingDetails().getStreetAndNumber()).isEqualTo("Typed 5");
        assertThat(model.get("shippingAddress")).isEqualTo("other");
        assertThat(model.get("page")).isInstanceOf(NewOrderPage.class);
        verifyNoInteractions(basketOrderImporter);
    }

    @Test
    void postWithErrorsShowsTheBasketShippingWhenNoShippingFieldWasSent() {
        // given
        Basket basket = basket(BasketType.Basket);
        ShippingDetails pickup = ShippingDetails._default();
        pickup.setStreetAndNumber("Paczkomat WAW01A");
        basket.setShippingDetails(pickup);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ClientDataDto dto = posted(ShipmentType.Courier);
        dto.setShippingDetails(null);
        dto.getBillingDetails().setPhone("12");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "same", model,
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("newOrder_clientDataCollection");
        ClientDataDto form = (ClientDataDto) model.get("form");
        assertThat(form.getShippingDetails().getStreetAndNumber()).isEqualTo("Paczkomat WAW01A");
        assertThat(form.getBillingDetails().getPhone()).isEqualTo("12");
        verifyNoInteractions(basketOrderImporter);
    }

    @Test
    void postForBasketWithUnknownDeliveryOptionRendersThePageAndCreatesNothing() {
        // given
        Basket basket = basket(BasketType.Basket);
        basket.setDeliveryOptionId("removed-option");
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ClientDataDto dto = posted(ShipmentType.Courier);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "same", model,
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("newOrder_clientDataCollection");
        assertThat((Map<String, String>) model.get("errors"))
                .containsExactly(Map.entry(NewOrderPage.DELIVERY_FIELD, "order.new.delivery.unknown"));
        verifyNoInteractions(basketOrderImporter);
    }

    @Test
    void postWithAnUnreadableChoiceAsksForIt() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket(BasketType.Basket)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ClientDataDto dto = posted(null);
        dto.setPaymentSource(null);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), null, model,
                new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("newOrder_clientDataCollection");
        assertThat((Map<String, String>) model.get("errors"))
                .containsEntry("shipmentType", "order.new.shipment.required")
                .containsEntry("paymentSource", "order.new.payment.required");
        verifyNoInteractions(basketOrderImporter);
    }

    @Test
    void postOfTemplateIsNotFound() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket(BasketType.OfferTemplate)));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ClientDataDto dto = posted(ShipmentType.Courier);

        // when
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "same",
                        new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH));

        // then
        assertThat(thrown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(basketOrderImporter);
    }

    @Test
    void postValidImportsTheAssembledDtoAndRedirects() {
        // given
        Basket basket = basket(BasketType.Basket);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        Order order = new Order(STORE_ID);
        order.setOrderId("order-9");
        ArgumentCaptor<ClientDataDto> imported = ArgumentCaptor.forClass(ClientDataDto.class);
        when(basketOrderImporter._import(eq(STORE_ID), imported.capture())).thenReturn(order);
        ClientDataDto dto = posted(ShipmentType.Courier);
        dto.getShippingDetails().setCity("Ignored");

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "same",
                new ExtendedModelMap(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/order-9");
        ClientDataDto sent = imported.getValue();
        assertThat(sent.getOrderReference()).isEqualTo(BASKET_ID);
        assertThat(sent.getShippingDetails().getCity()).isEqualTo("Warszawa");
        assertThat(sent.getShippingDetails().getStreetAndNumber()).isEqualTo("Main 1");
        assertThat(sent.getShippingDetails().getCountry()).isEqualTo("PL");
        assertThat(sent.getBillingDetails().getCountry()).isEqualTo("PL");
    }

    @Test
    void postForGoneBasketRedirectsToTheOrdersListWithAMessage() {
        // given
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.empty());
        when(messageSource.getMessage(eq("order.new.basket.gone"), isNull(), eq(Locale.ENGLISH))).thenReturn("Gone");
        ClientDataDto dto = posted(ShipmentType.Courier);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // when
        String view = ordersController.submitOrder(dto, new BeanPropertyBindingResult(dto, "form"), "same",
                new ExtendedModelMap(), redirectAttributes, Locale.ENGLISH);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Gone");
        verify(messageSource).getMessage(eq("order.new.basket.gone"), isNull(), eq(Locale.ENGLISH));
        verifyNoInteractions(basketOrderImporter);
    }

    private static Store store() {
        DeliveryOption courier = new DeliveryOption();
        courier.setId("dpd");
        courier.setName("Kurier DPD");
        courier.setPrice(19.99);
        courier.setType(ShipmentType.Courier);
        CheckoutConfiguration checkout = new CheckoutConfiguration();
        checkout.setDeliveryOptions(new LinkedList<>(List.of(courier)));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setCheckoutConfiguration(checkout);
        return store;
    }

    private static Basket basket(BasketType type) {
        BasketItem item = new BasketItem();
        item.setName("Laptop");
        item.setMfn("L-1");
        item.setQty(1);
        item.setUnitPrice(100.0);
        Basket basket = new Basket.Builder(STORE_ID)
                .withBasketId(BASKET_ID)
                .withType(type)
                .withDeliveryOptionId("dpd")
                .build();
        basket.setBasketItems(new LinkedList<>(List.of(item)));
        basket.setBillingDetails(billing());
        return basket;
    }

    private static BillingDetails billing() {
        BillingDetails billing = BillingDetails._default();
        billing.setName("Anna");
        billing.setStreetAndNumber("Main 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setEmail("anna@example.com");
        billing.setPhone("601234567");
        return billing;
    }

    private static ClientDataDto posted(ShipmentType type) {
        ClientDataDto dto = new ClientDataDto();
        dto.setOrderReference(BASKET_ID);
        dto.setShipmentType(type);
        dto.setPaymentSource(PaymentSource.BankTransfer);
        BillingDetails billing = billing();
        billing.setCountry("pl");
        dto.setBillingDetails(billing);
        dto.setShippingDetails(ShippingDetails._default());
        return dto;
    }
}
