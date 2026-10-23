package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.CategoryLocalizer;
import pl.commercelink.web.clientorder.ClientOrderNotice;
import pl.commercelink.web.dtos.ClientOrderView;
import pl.commercelink.web.publicpage.PublicPage;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
    @DisplayName("getOrderForClient renders the status page with the view and the public page for a trackable order")
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
        assertThat(((PublicPage) model.getAttribute("publicPage")).storeId()).isEqualTo(STORE_ID);
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
        MockHttpServletResponse response = new MockHttpServletResponse();
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), response, Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("client-order/unavailable");
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(orderItemsRepository);
    }

    @Test
    @DisplayName("getOrderForClient returns 404 for a completed order so the public link expires with it")
    void returns404ForCompletedOrder() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));

        // when
        MockHttpServletResponse response = new MockHttpServletResponse();
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), response, Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("client-order/unavailable");
        assertThat(response.getStatus()).isEqualTo(404);
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
        MockHttpServletResponse response = new MockHttpServletResponse();
        String template = controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, new ExtendedModelMap(), response, Locale.ENGLISH);

        // then
        assertThat(template).isEqualTo("client-order/unavailable");
        assertThat(response.getStatus()).isEqualTo(404);
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
    @DisplayName("requestAddressCode issues a code and redirects to the code form without an anchor, so the code field takes the focus")
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
        String result = controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), new RedirectAttributesModelMap(), new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

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
        String result = controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "#delivery");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.not.editable"));
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
        controller.requestAddressCode(STORE_ID, ORDER_ID, new MockHttpServletRequest(), redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.too.many.requests"));
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
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", " 123456 ", new MockHttpServletRequest(), new RedirectAttributesModelMap(), new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?t=tok-1#delivery");
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
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", "000000", new MockHttpServletRequest(), redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?v=ver-1");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.codeError("client.order.address.error.invalid.code"));
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
        assertThat(model.getAttribute("clientOrderNotice"))
                .isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.invalid.token"));
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
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "#delivery");
        verify(clientVerificationService).consume(any(ClientVerificationSubject.class), eq("tok-1"));
        verify(addressChangeService).change(order, validated, store);
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.ok("delivery", "client.order.address.success"));
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
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?t=tok-1#delivery");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.invalid.address"));
        assertThat(redirect.getFlashAttributes().get("form")).isSameAs(form);
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
        String result = controller.changeAddress(STORE_ID, ORDER_ID, "tok-1", form, redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "#delivery");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.invalid.token"));
        verify(addressChangeService, never()).change(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page switched off", "order missing", "order completed", "store unknown"})
    @DisplayName("every action answers 404 with the public unavailable page when the order cannot be shown")
    void unavailableOrderAnswers404WithThePublicPage(String reason) {
        // given
        Store disabled = new Store();
        disabled.setStoreId(STORE_ID);
        switch (reason) {
            case "page switched off" -> when(storesRepository.findById(STORE_ID)).thenReturn(disabled);
            case "order missing" -> {
                when(storesRepository.findById(STORE_ID)).thenReturn(store());
                when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);
            }
            case "order completed" -> {
                when(storesRepository.findById(STORE_ID)).thenReturn(store());
                when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));
            }
            default -> when(storesRepository.findById(STORE_ID)).thenReturn(null);
        }
        List<ExtendedModelMap> models = List.of(new ExtendedModelMap(), new ExtendedModelMap(), new ExtendedModelMap(),
                new ExtendedModelMap(), new ExtendedModelMap());
        List<MockHttpServletResponse> responses = List.of(new MockHttpServletResponse(), new MockHttpServletResponse(),
                new MockHttpServletResponse(), new MockHttpServletResponse(), new MockHttpServletResponse());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        List<String> views = List.of(
                controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, models.get(0), responses.get(0), Locale.ENGLISH),
                controller.requestAddressCode(STORE_ID, ORDER_ID, request, redirect, models.get(1), responses.get(1), Locale.ENGLISH),
                controller.confirmAddressCode(STORE_ID, ORDER_ID, "v", "1", request, redirect, models.get(2), responses.get(2), Locale.ENGLISH),
                controller.changeAddress(STORE_ID, ORDER_ID, "t", new ShippingDetails(), redirect, models.get(3), responses.get(3), Locale.ENGLISH),
                controller.changePreferredShipping(STORE_ID, ORDER_ID, "2026-01-05", redirect, models.get(4), responses.get(4), Locale.ENGLISH));

        // then
        List<String> actions = List.of("GET", "request-code", "confirm-code", "address", "preferred-shipping");
        for (int i = 0; i < views.size(); i++) {
            assertThat(views.get(i)).as(actions.get(i)).isEqualTo("client-order/unavailable");
            assertThat(responses.get(i).getStatus()).as(actions.get(i)).isEqualTo(404);
            assertThat(models.get(i).getAttribute("publicPage")).as(actions.get(i)).isInstanceOf(PublicPage.class);
        }
        verifyNoInteractions(clientVerificationService, orderItemsRepository);
    }

    @Test
    @DisplayName("the unavailable page of an unknown store has no store id and no short id")
    void unknownStoreGivesNeutralUnavailablePage() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(null);
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(((PublicPage) model.getAttribute("publicPage")).storeId()).isNull();
        assertThat(model.getAttribute("shortId")).isNull();
    }

    @Test
    @DisplayName("the unavailable page of a completed order shows its short id")
    void completedOrderShowsShortIdOnUnavailablePage() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Completed));
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, null, null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(model.getAttribute("shortId")).isEqualTo("3f9a2c1e");
    }

    @Test
    @DisplayName("the page carries the public page and keeps the verification parameter in the language link")
    void pageCarriesPublicPageKeepingVerificationParameter() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, "x", null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        PublicPage page = (PublicPage) model.getAttribute("publicPage");
        assertThat(page.otherLanguageHref()).isEqualTo("/store/store-1/client/order/" + ORDER_ID + "?v=x&lang=pl");
    }

    @Test
    @DisplayName("a flashed form from a rejected address replaces the stored address in the edit step")
    void editStepUsesFlashedFormWhenPresent() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(clientVerificationService.findByEditToken(any(ClientVerificationSubject.class), eq("tok-1")))
                .thenReturn(Optional.of(new pl.commercelink.clientaccess.ClientVerification()));
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        ShippingDetails typed = new ShippingDetails();
        typed.setStreetAndNumber("Typed 9");
        Model model = new ExtendedModelMap();
        model.addAttribute("form", typed);

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, null, "tok-1", model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(model.getAttribute("form")).isSameAs(typed);
    }

    @Test
    @DisplayName("the code step on an order whose address is no longer editable shows the notice in the delivery card")
    void codeStepNotEditableShowsNotice() {
        // given
        Order order = order(OrderStatus.Shipping);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        Model model = new ExtendedModelMap();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, "ver-1", null, model, new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(model.getAttribute("addressStep")).isNull();
        assertThat(model.getAttribute("clientOrderNotice"))
                .isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.not.editable"));
    }

    @ParameterizedTest
    @EnumSource(value = ClientVerificationException.Reason.class, names = {"INVALID_CODE", "CODE_EXPIRED", "TOO_MANY_ATTEMPTS", "NOT_FOUND"})
    @DisplayName("every wrong-code failure returns to the code step with the field error")
    void wrongCodeReturnsToCodeStepWithFieldError(ClientVerificationException.Reason reason) {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationService.confirm(any(), eq("ver-1"), eq("000000"))).thenThrow(new ClientVerificationException(reason));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", "000000", new MockHttpServletRequest(), redirect,
                new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?v=ver-1");
        ClientOrderNotice notice = notice(redirect);
        assertThat(notice.field()).isEqualTo("code");
        assertThat(notice.tone()).isEqualTo("bad");
        assertThat(notice.target()).isEqualTo("delivery");
    }

    @Test
    @DisplayName("a confirm failure that is not about the code carries no field")
    void otherConfirmFailureHasNoField() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        when(clientVerificationService.confirm(any(), eq("ver-1"), eq("000000")))
                .thenThrow(new ClientVerificationException(ClientVerificationException.Reason.INVALID_TOKEN));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.confirmAddressCode(STORE_ID, ORDER_ID, "ver-1", "000000", new MockHttpServletRequest(), redirect,
                new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "?v=ver-1");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("delivery", "client.order.address.error.invalid.token"));
    }

    @Test
    @DisplayName("a preferred day that was saved puts the notice in the date card")
    void preferredDaySavedNoticeTargetsDate() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.changePreferredShipping(STORE_ID, ORDER_ID, "2026-01-05", redirect, new ExtendedModelMap(),
                new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "#date");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.ok("date", "client.order.preferred.shipping.success"));
        verify(preferredShippingDateService).change(any(Order.class), eq(LocalDate.of(2026, 1, 5)), any(Store.class));
    }

    @Test
    @DisplayName("clearing the preferred day uses the cleared message in the date card")
    void preferredDayClearedNoticeTargetsDate() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        controller.changePreferredShipping(STORE_ID, ORDER_ID, " ", redirect, new ExtendedModelMap(), new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.ok("date", "client.order.preferred.shipping.cleared"));
        verify(preferredShippingDateService).change(any(Order.class), isNull(), any(Store.class));
    }

    @Test
    @DisplayName("an unparsable preferred date puts the error in the date card")
    void invalidPreferredDateNoticeTargetsDate() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order(OrderStatus.Assembly));
        when(storesRepository.findById(STORE_ID)).thenReturn(store());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String result = controller.changePreferredShipping(STORE_ID, ORDER_ID, "nonsense", redirect, new ExtendedModelMap(),
                new MockHttpServletResponse(), Locale.ENGLISH);

        // then
        assertThat(result).isEqualTo("redirect:/store/store-1/client/order/" + ORDER_ID + "#date");
        assertThat(notice(redirect)).isEqualTo(ClientOrderNotice.bad("date", "client.order.preferred.shipping.error.invalid.date"));
        verifyNoInteractions(preferredShippingDateService);
    }

    @Test
    @DisplayName("the edit step keeps the same-origin referrer policy so the edit token stays out of third-party logs")
    void editStepKeepsReferrerPolicy() {
        // given
        Order order = order(OrderStatus.Assembly);
        Store store = store();
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(clientVerificationService.findByEditToken(any(ClientVerificationSubject.class), eq("tok-1")))
                .thenReturn(Optional.of(new pl.commercelink.clientaccess.ClientVerification()));
        when(addressChangeService.isEditable(order, store)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.getOrderForClient(STORE_ID, ORDER_ID, null, "tok-1", new ExtendedModelMap(), response, Locale.ENGLISH);

        // then
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
    }

    private static ClientOrderNotice notice(RedirectAttributesModelMap redirect) {
        return (ClientOrderNotice) redirect.getFlashAttributes().get("clientOrderNotice");
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
