package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.clientaccess.ClientVerificationException;
import pl.commercelink.clientaccess.ClientVerificationPurpose;
import pl.commercelink.clientaccess.ClientVerificationRateLimiter;
import pl.commercelink.clientaccess.ClientVerificationService;
import pl.commercelink.clientaccess.ClientVerificationSubject;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ClientShippingAddressChangeException;
import pl.commercelink.orders.ClientPreferredShippingDateService;
import pl.commercelink.orders.ClientShippingAddressChangeService;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.stores.Branding;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.dtos.ClientOrderView;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOrderControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "3f9a2c1e-7b44-4d0e-9a1f-52c8e0b7d611";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private CategoryLocalizer categoryLocalizer;
    @Mock
    private ClientVerificationService clientVerificationService;
    @Mock
    private ClientVerificationRateLimiter clientVerificationRateLimiter;
    @Mock
    private ClientShippingAddressChangeService addressChangeService;
    @Mock
    private ClientPreferredShippingDateService preferredShippingDateService;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ClientOrderController controller;

    @BeforeEach
    void setUp() {
        when(clientVerificationRateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("getOrderForClient renders the status page with the view, store and branding for a trackable order")
    void rendersStatusPageForTrackableOrder() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(model.getAttribute("store")).isSameAs(store);
        assertThat(model.getAttribute("branding")).isInstanceOf(Branding.class);
        ClientOrderView view = (ClientOrderView) model.getAttribute("view");
        assertThat(view.getShortOrderId()).isEqualTo("3f9a2c1e");
    }

    @Test
    @DisplayName("getOrderForClient returns 404 when the order does not exist")
    void returns404WhenOrderMissing() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient returns 404 for a completed order so the public link expires with it")
    void returns404ForCompletedOrder() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient still renders a cancelled order")
    void rendersCancelledOrder() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Cancelled));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        Model model = new ExtendedModelMap();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(((ClientOrderView) model.getAttribute("view")).isCancelled()).isTrue();
    }

    @Test
    @DisplayName("getOrderForClient returns 404 when the store has the client order page disabled")
    void returns404WhenClientOrderPageDisabled() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(new Store());

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("error/404");
        verifyNoInteractions(ordersRepository, orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient exposes the address change button when the service allows it")
    void exposesAddressChangeWhenEditable() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(((ClientOrderView) model.getAttribute("view")).isShippingAddressEditable()).isTrue();
    }

    @Test
    @DisplayName("requestAddressCode issues a code to the billing e-mail and redirects to the code form")
    void requestAddressCodeIssuesCodeAndRedirects() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        when(clientVerificationService.issue(any(), eq(ClientVerificationPurpose.SHIPPING_ADDRESS_CHANGE), eq("payer@example.com"), eq("Jan")))
                .thenReturn("ver-1");

        // when
        String result = controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?v=ver-1");
    }

    @Test
    @DisplayName("requestAddressCode redirects with an error and issues nothing when the address is not editable")
    void requestAddressCodeRefusesWhenNotEditable() {
        // given
        Order order = order(OrderStatus.Shipping);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(addressChangeService.isEditable(order, store)).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), redirect, Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID);
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("client.order.address.error.not.editable");
        verify(clientVerificationService, never()).issue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("requestAddressCode redirects with an error when the IP limiter refuses")
    void requestAddressCodeRefusesWhenRateLimited() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationRateLimiter.tryAcquire(anyString())).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), redirect, Locale.ENGLISH);

        // then
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("client.order.address.error.too.many.requests");
        verifyNoInteractions(clientVerificationService);
    }

    @Test
    @DisplayName("confirmAddressCode redirects to the edit form with the edit token on success")
    void confirmAddressCodeRedirectsToEditForm() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationService.confirm(any(), eq("ver-1"), eq("123456"))).thenReturn("tok-1");

        // when
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", " 123456 ", new MockHttpServletRequest(), new RedirectAttributesModelMap(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?t=tok-1");
    }

    @Test
    @DisplayName("confirmAddressCode returns to the code form with the verification error as flash")
    void confirmAddressCodeReturnsToCodeFormOnError() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationService.confirm(any(), eq("ver-1"), eq("000000")))
                .thenThrow(new ClientVerificationException(ClientVerificationException.Reason.INVALID_CODE));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", "000000", new MockHttpServletRequest(), redirect, Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?v=ver-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("client.order.address.error.invalid.code");
    }

    @Test
    @DisplayName("getOrderForClient with a valid edit token renders the inline edit form with the unmasked address")
    void rendersInlineEditFormWithValidToken() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(clientVerificationService.findByEditToken(any(ClientVerificationSubject.class), eq("tok-1")))
                .thenReturn(Optional.of(new pl.commercelink.clientaccess.ClientVerification()));
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, "tok-1", model, response, Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(model.getAttribute("addressStep")).isEqualTo("edit");
        assertThat(model.getAttribute("editToken")).isEqualTo("tok-1");
        assertThat(((ShippingDetails) model.getAttribute("form")).getStreetAndNumber()).isEqualTo("Stara 1");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    @Test
    @DisplayName("getOrderForClient with an unknown edit token renders the page with an error and no form")
    void rendersErrorForUnknownToken() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationService.findByEditToken(any(), any())).thenReturn(Optional.empty());
        Model model = new ExtendedModelMap();

        // when
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, "bad", model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("clientOrder");
        assertThat(model.getAttribute("addressStep")).isNull();
        assertThat(model.getAttribute("errorMessage")).isEqualTo("client.order.address.error.invalid.token");
    }

    @Test
    @DisplayName("getOrderForClient with a verification id renders the inline code form with the masked billing e-mail")
    void rendersInlineCodeForm() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, "ver-1", null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(model.getAttribute("addressStep")).isEqualTo("code");
        assertThat(model.getAttribute("verificationId")).isEqualTo("ver-1");
        assertThat(model.getAttribute("maskedEmail")).isEqualTo("p***@example.com");
    }

    @Test
    @DisplayName("changeAddress validates, consumes the token, changes the address and redirects with success")
    void changeAddressConsumesTokenAndChanges() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        ShippingDetails form = new ShippingDetails();
        ShippingDetails validated = new ShippingDetails();
        when(addressChangeService.validate(order, form)).thenReturn(validated);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID);
        verify(clientVerificationService).consume(any(ClientVerificationSubject.class), eq("tok-1"));
        verify(addressChangeService).change(order, validated, store);
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("client.order.address.success");
    }

    @Test
    @DisplayName("changeAddress returns to the edit form without consuming the token when the address is invalid")
    void changeAddressKeepsTokenOnInvalidAddress() {
        // given
        Order order = order(OrderStatus.Assembly);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ShippingDetails form = new ShippingDetails();
        when(addressChangeService.validate(order, form))
                .thenThrow(new ClientShippingAddressChangeException(ClientShippingAddressChangeException.Reason.INVALID_ADDRESS));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?t=tok-1");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("client.order.address.error.invalid.address");
        verify(clientVerificationService, never()).consume(any(), any());
        verify(addressChangeService, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("changeAddress redirects with an error and changes nothing when the token was already used")
    void changeAddressRefusesUsedToken() {
        // given
        Order order = order(OrderStatus.Assembly);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        ShippingDetails form = new ShippingDetails();
        when(addressChangeService.validate(order, form)).thenReturn(form);
        when(clientVerificationService.consume(any(), eq("tok-1")))
                .thenThrow(new ClientVerificationException(ClientVerificationException.Reason.INVALID_TOKEN));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID);
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("client.order.address.error.invalid.token");
        verify(addressChangeService, never()).change(any(), any(), any());
    }

    private static Order order(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        BillingDetails billing = BillingDetails._default();
        billing.setName("Jan");
        billing.setEmail("payer@example.com");
        order.setBillingDetails(billing);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setStreetAndNumber("Stara 1");
        shipping.setCountry("PL");
        order.setShippingDetails(shipping);
        return order;
    }

    private static Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Sklep");
        FulfilmentConfiguration configuration = new FulfilmentConfiguration();
        configuration.setClientOrderPageEnabled(true);
        store.setFulfilmentConfiguration(configuration);
        return store;
    }
}
