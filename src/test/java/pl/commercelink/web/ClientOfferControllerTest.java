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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.RedirectView;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.checkout.Checkout;
import pl.commercelink.checkout.CheckoutResponse;
import pl.commercelink.invoicing.InvoicingService;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.payments.api.PaymentLink;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.clientoffer.ClientOfferDataForm;
import pl.commercelink.web.clientoffer.ClientOfferNotice;
import pl.commercelink.web.clientoffer.ClientOfferPage;
import pl.commercelink.web.clientoffer.ClientOfferPageFactory;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.publicpage.PublicPage;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientOfferControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String OFFER_ID = "offer-1-abcdefgh";
    private static final String OFFER_PATH = "/store/store-1/client/offer/" + OFFER_ID;
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Checkout checkout;
    @Mock
    private InvoicingService invoicingService;
    @Mock
    private MessageSource messageSource;
    @Mock
    private ClientOfferPageFactory pageFactory;

    @InjectMocks
    private ClientOfferController controller;

    private MockedStatic<CustomSecurityContext> security;
    private Store store;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Nova Tech");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(pageFactory.build(any(), any(), anyBoolean(), any())).thenReturn(page(false));
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(invocation -> {
            Object[] args = invocation.getArgument(1);
            String key = invocation.getArgument(0);
            return args == null ? key : key + ":" + MessageFormat.format("{0}", args);
        });
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void missingOfferShowsUnavailablePageWith404() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", OFFER_PATH);

        // when
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        ExtendedModelMap getModel = new ExtendedModelMap();
        String getView = controller.getOfferForClient(STORE_ID, OFFER_ID, null, getModel, PL, request, getResponse);

        MockHttpServletResponse submitResponse = new MockHttpServletResponse();
        String submitView = controller.submitClientOfferForm(STORE_ID, OFFER_ID, new ClientDataDto(), null, null, null,
                new ExtendedModelMap(), new RedirectAttributesModelMap(), PL, request, submitResponse);

        MockHttpServletResponse selectResponse = new MockHttpServletResponse();
        String selectView = controller.selectVariant(STORE_ID, OFFER_ID, "SSD", 1, new ExtendedModelMap(), PL,
                request, selectResponse);

        MockHttpServletResponse checkoutResponse = new MockHttpServletResponse();
        ModelAndView checkoutView = controller.createPaymentLink(STORE_ID, OFFER_ID, null,
                new RedirectAttributesModelMap(), PL, request, checkoutResponse);

        MockHttpServletResponse proformaResponse = new MockHttpServletResponse();
        String proformaView = controller.createProformaInvoice(STORE_ID, OFFER_ID, PL, new RedirectAttributesModelMap(),
                new ExtendedModelMap(), request, proformaResponse);

        // then
        assertThat(List.of(getView, submitView, selectView, proformaView, checkoutView.getViewName()))
                .containsOnly("client-offer/unavailable");
        assertThat(List.of(getResponse.getStatus(), submitResponse.getStatus(), selectResponse.getStatus(),
                checkoutResponse.getStatus(), proformaResponse.getStatus())).containsOnly(404);
        assertThat(getModel.get("shortId")).isEqualTo("offer-1-");
        PublicPage publicPage = (PublicPage) getModel.get("publicPage");
        assertThat(publicPage.storeName()).isEqualTo("Nova Tech");
        assertThat(publicPage.otherLanguageHref()).isEqualTo(OFFER_PATH + "?lang=en");
        assertThat(checkoutView.getModel()).containsKeys("publicPage", "shortId");
        verify(basketsRepository, never()).save(any());
        verifyNoInteractions(checkout, invoicingService);
    }

    @Test
    void missingOfferOfAnUnknownStoreShowsTheUnbrandedFrame() {
        // given
        when(storesRepository.findById("ghost")).thenReturn(null);
        when(basketsRepository.findById("ghost", OFFER_ID)).thenReturn(Optional.empty());
        String path = "/store/ghost/individual/offer/" + OFFER_ID;
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.getOfferForClient("ghost", OFFER_ID, null, model, PL,
                new MockHttpServletRequest("GET", path), response);

        // then
        assertThat(view).isEqualTo("client-offer/unavailable");
        assertThat(response.getStatus()).isEqualTo(404);
        PublicPage publicPage = (PublicPage) model.get("publicPage");
        assertThat(publicPage.storeName()).isEmpty();
        assertThat(publicPage.otherLanguageHref()).isEqualTo(path + "?lang=en");
    }

    @Test
    void expiredOfferRefusesEveryAction() {
        // given
        Basket basket = submittedBasket();
        basket.setType(BasketType.Offer);
        basket.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", OFFER_PATH + "/submit");

        // when
        String submitView = controller.submitClientOfferForm(STORE_ID, OFFER_ID, validDto(), "person", "same", null,
                new ExtendedModelMap(), new RedirectAttributesModelMap(), PL, request, new MockHttpServletResponse());
        String selectView = controller.selectVariant(STORE_ID, OFFER_ID, "SSD", 1, new ExtendedModelMap(), PL,
                request, new MockHttpServletResponse());
        ModelAndView checkoutView = controller.createPaymentLink(STORE_ID, OFFER_ID, null,
                new RedirectAttributesModelMap(), PL, request, new MockHttpServletResponse());
        String proformaView = controller.createProformaInvoice(STORE_ID, OFFER_ID, PL, new RedirectAttributesModelMap(),
                new ExtendedModelMap(), request, new MockHttpServletResponse());

        // then
        assertThat(List.of(submitView, selectView, proformaView, checkoutView.getViewName()))
                .containsOnly("redirect:" + OFFER_PATH);
        verify(basketsRepository, never()).save(any());
        verifyNoInteractions(checkout, invoicingService);
    }

    @Test
    void expiredOfferStillRendersThePage() {
        // given
        Basket basket = basket();
        basket.setType(BasketType.Offer);
        basket.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getOfferForClient(STORE_ID, OFFER_ID, null, new ExtendedModelMap(), PL,
                new MockHttpServletRequest("GET", OFFER_PATH), response);

        // then
        assertThat(view).isEqualTo("clientOffer");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void getRendersThePageModel() {
        // given
        Basket basket = basket();
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.getOfferForClient(STORE_ID, OFFER_ID, null, model, PL,
                new MockHttpServletRequest("GET", OFFER_PATH), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("clientOffer");
        assertThat(model).containsKeys("publicPage", "page", "form", "errors", "editing", "countries");
        assertThat(model.get("editing")).isEqualTo(false);
        assertThat((Map<?, ?>) model.get("errors")).isEmpty();
        assertThat(((PublicPage) model.get("publicPage")).otherLanguageHref()).isEqualTo(OFFER_PATH + "?lang=en");
        verify(pageFactory).build(basket, store, true, PL);
    }

    @Test
    void invalidDataRendersTheFormWithErrorsAndSavesNothing() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket()));
        ExtendedModelMap model = new ExtendedModelMap();
        ClientDataDto dto = new ClientDataDto();
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        dto.setBillingDetails(billing);

        // when
        String view = controller.submitClientOfferForm(STORE_ID, OFFER_ID, dto, "person", "same", "data", model,
                new RedirectAttributesModelMap(), PL, new MockHttpServletRequest("POST", OFFER_PATH + "/submit"),
                new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("clientOffer");
        assertThat((Map<?, ?>) model.get("errors")).isNotEmpty();
        assertThat(model.get("editing")).isEqualTo(true);
        ClientOfferDataForm form = (ClientOfferDataForm) model.get("form");
        assertThat(form.dto().getBillingDetails().getName()).isEqualTo("Jan");
        assertThat(model).containsKeys("publicPage", "page", "countries");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void invalidDataOnAnOfferWithSavedDataShowsTheFormWithoutTheEditParameter() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(submittedBasket()));
        when(pageFactory.build(any(), any(), anyBoolean(), any())).thenReturn(page(true));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.submitClientOfferForm(STORE_ID, OFFER_ID, new ClientDataDto(), "person", "same", null,
                model, new RedirectAttributesModelMap(), PL, new MockHttpServletRequest("POST", OFFER_PATH + "/submit"),
                new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("clientOffer");
        assertThat((Map<?, ?>) model.get("errors")).isNotEmpty();
        assertThat(model.get("editing")).isEqualTo(true);
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void validDataIsSavedWithTheCopiedShippingAddress() {
        // given
        Basket basket = basket();
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        // when
        String view = controller.submitClientOfferForm(STORE_ID, OFFER_ID, validDto(), "person", "same", null,
                new ExtendedModelMap(), flash, PL, new MockHttpServletRequest("POST", OFFER_PATH + "/submit"),
                new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:" + OFFER_PATH);
        ArgumentCaptor<Basket> saved = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(saved.capture());
        assertThat(saved.getValue().getBillingDetails().getCity()).isEqualTo("Kraków");
        assertThat(saved.getValue().getShippingDetails().getCity()).isEqualTo("Kraków");
        assertThat(saved.getValue().getShippingDetails().getStreetAndNumber()).isEqualTo("ul. Długa 5");
        ClientOfferNotice notice = (ClientOfferNotice) flash.getFlashAttributes().get("clientOfferNotice");
        assertThat(notice.tone()).isEqualTo("ok");
        assertThat(notice.text()).isEqualTo("client.offer.notice.saved");
    }

    @Test
    void editParameterOpensAnEmptyForm() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(submittedBasket()));
        when(pageFactory.build(any(), any(), anyBoolean(), any())).thenReturn(page(true));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        controller.getOfferForClient(STORE_ID, OFFER_ID, "data", model, PL,
                new MockHttpServletRequest("GET", OFFER_PATH), new MockHttpServletResponse());

        // then
        assertThat(model.get("editing")).isEqualTo(true);
        ClientOfferDataForm form = (ClientOfferDataForm) model.get("form");
        assertThat(form.dto().getBillingDetails().getName()).isNull();
        assertThat(form.dto().getBillingDetails().getEmail()).isNull();
        assertThat(form.customerType()).isEqualTo(ClientOfferDataForm.PERSON);
    }

    @Test
    void unknownVariantPositionRedirectsWithoutSaving() {
        // given
        Basket basket = basket();
        BasketItem item = new BasketItem("pim-1", "SSD", "MFN-SSD-1", "SSD", 100.0, 0, 1, null, 3, false);
        item.setVariantGroupId("SSD");
        basket.setBasketItems(List.of(item));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = controller.selectVariant(STORE_ID, OFFER_ID, "SSD", 999, new ExtendedModelMap(), PL,
                new MockHttpServletRequest("POST", OFFER_PATH + "/select-variant"), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:" + OFFER_PATH);
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void selectVariantMarksChosenItemSavesAndRedirects() {
        // given
        Basket basket = basket();
        BasketItem ssd1 = new BasketItem("pim-1", "SSD 1", "MFN-SSD-1", "SSD", 100.0, 0, 1, null, 3, false);
        BasketItem ssd2 = new BasketItem("pim-2", "SSD 2", "MFN-SSD-2", "SSD", 100.0, 0, 1, null, 3, false);
        ssd1.setVariantGroupId("SSD");
        ssd2.setVariantGroupId("SSD");
        basket.setBasketItems(List.of(ssd1, ssd2));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));

        // when
        String view = controller.selectVariant(STORE_ID, OFFER_ID, "SSD", ssd2.getPosition(), new ExtendedModelMap(),
                PL, new MockHttpServletRequest("POST", OFFER_PATH + "/select-variant"), new MockHttpServletResponse());

        // then
        verify(basketsRepository).save(basket);
        assertThat(ssd1.isVariantSelected()).isFalse();
        assertThat(ssd2.isVariantSelected()).isTrue();
        assertThat(view).isEqualTo("redirect:" + OFFER_PATH);
    }

    @Test
    void checkoutWithoutDataRedirects() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket()));

        // when
        ModelAndView view = controller.createPaymentLink(STORE_ID, OFFER_ID, "payu", new RedirectAttributesModelMap(),
                PL, new MockHttpServletRequest("POST", OFFER_PATH + "/checkout"), new MockHttpServletResponse());

        // then
        assertThat(view.getViewName()).isEqualTo("redirect:" + OFFER_PATH);
        verifyNoInteractions(checkout);
    }

    @Test
    void failedCheckoutReturnsToTheOfferWithANotice() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(submittedBasket()));
        when(checkout.create(STORE_ID, OFFER_ID, "payu")).thenThrow(new IllegalStateException("no integration"));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        // when
        ModelAndView view = controller.createPaymentLink(STORE_ID, OFFER_ID, "payu", flash, PL,
                new MockHttpServletRequest("POST", OFFER_PATH + "/checkout"), new MockHttpServletResponse());

        // then
        assertThat(view.getViewName()).isEqualTo("redirect:" + OFFER_PATH);
        ClientOfferNotice notice = (ClientOfferNotice) flash.getFlashAttributes().get("clientOfferNotice");
        assertThat(notice.tone()).isEqualTo("bad");
        assertThat(notice.text()).isEqualTo("client.offer.notice.payment.failed");
    }

    @Test
    void postCheckoutRendersTheBridgeWithTheStoreFrame() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(submittedBasket()));
        when(checkout.create(STORE_ID, OFFER_ID, "payu")).thenReturn(new CheckoutResponse(
                new PaymentLink("https://pay.example/go", "POST", Map.of("token", "t-1"))));

        // when
        ModelAndView view = controller.createPaymentLink(STORE_ID, OFFER_ID, "payu", new RedirectAttributesModelMap(),
                PL, new MockHttpServletRequest("POST", OFFER_PATH + "/checkout"), new MockHttpServletResponse());

        // then
        assertThat(view.getViewName()).isEqualTo("payments/payment-bridge");
        assertThat(view.getModel()).containsEntry("url", "https://pay.example/go")
                .containsEntry("params", Map.of("token", "t-1"));
        assertThat(((PublicPage) view.getModel().get("publicPage")).storeName()).isEqualTo("Nova Tech");
    }

    @Test
    void getCheckoutRedirectsToTheProvider() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(submittedBasket()));
        when(checkout.create(STORE_ID, OFFER_ID, "payu")).thenReturn(new CheckoutResponse(
                new PaymentLink("https://pay.example/go", "GET", Map.of())));

        // when
        ModelAndView view = controller.createPaymentLink(STORE_ID, OFFER_ID, "payu", new RedirectAttributesModelMap(),
                PL, new MockHttpServletRequest("POST", OFFER_PATH + "/checkout"), new MockHttpServletResponse());

        // then
        assertThat(view.getView()).isInstanceOf(RedirectView.class);
        assertThat(((RedirectView) view.getView()).getUrl()).isEqualTo("https://pay.example/go");
    }

    @Test
    void proformaWithoutDataRedirects() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket()));

        // when
        String view = controller.createProformaInvoice(STORE_ID, OFFER_ID, PL, new RedirectAttributesModelMap(),
                new ExtendedModelMap(), new MockHttpServletRequest("POST", OFFER_PATH + "/send-proforma-invoice"),
                new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:" + OFFER_PATH);
        verifyNoInteractions(invoicingService);
    }

    @Test
    void proformaNoticeMasksTheEmail() {
        // given
        Basket basket = submittedBasket();
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(invoicingService.createProforma(basket, PL, true))
                .thenReturn(new InvoicingService.OperationResult("inv-1", "PF/1", null, null));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        // when
        String view = controller.createProformaInvoice(STORE_ID, OFFER_ID, PL, flash, new ExtendedModelMap(),
                new MockHttpServletRequest("POST", OFFER_PATH + "/send-proforma-invoice"), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("redirect:" + OFFER_PATH);
        ClientOfferNotice notice = (ClientOfferNotice) flash.getFlashAttributes().get("clientOfferNotice");
        assertThat(notice.tone()).isEqualTo("ok");
        assertThat(notice.text()).isEqualTo("client.offer.notice.proforma.sent:jan***")
                .doesNotContain("kowalski");
    }

    @Test
    void failedProformaShowsAFixedNoticeInsteadOfTheProviderText() {
        // given
        Basket basket = submittedBasket();
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        when(invoicingService.createProforma(basket, PL, true))
                .thenReturn(new InvoicingService.OperationResult(null, null, null, "Brak konfiguracji"));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        // when
        controller.createProformaInvoice(STORE_ID, OFFER_ID, PL, flash, new ExtendedModelMap(),
                new MockHttpServletRequest("POST", OFFER_PATH + "/send-proforma-invoice"), new MockHttpServletResponse());

        // then
        assertThat(flash.getFlashAttributes().get("clientOfferNotice"))
                .isEqualTo(new ClientOfferNotice("bad", "client.offer.notice.proforma.failed"));
    }

    @Test
    void individualAndClientPathsBothWork() {
        // given
        Basket oldOffer = basket();
        oldOffer.setCreatedAt(LocalDateTime.of(2026, 9, 9, 8, 0));
        Basket newOffer = basket();
        newOffer.setCreatedAt(LocalDateTime.of(2026, 9, 10, 8, 0));
        when(basketsRepository.findById(STORE_ID, "old")).thenReturn(Optional.of(oldOffer));
        when(basketsRepository.findById(STORE_ID, "new")).thenReturn(Optional.of(newOffer));
        oldOffer.setBasketId("old");
        newOffer.setBasketId("new");

        // when
        String oldView = controller.submitClientOfferForm(STORE_ID, "old", validDto(), "person", "same", null,
                new ExtendedModelMap(), new RedirectAttributesModelMap(), PL,
                new MockHttpServletRequest("POST", "/store/store-1/individual/offer/old/submit"),
                new MockHttpServletResponse());
        String newView = controller.submitClientOfferForm(STORE_ID, "new", validDto(), "person", "same", null,
                new ExtendedModelMap(), new RedirectAttributesModelMap(), PL,
                new MockHttpServletRequest("POST", "/store/store-1/client/offer/new/submit"),
                new MockHttpServletResponse());

        // then
        assertThat(oldView).isEqualTo("redirect:/store/store-1/individual/offer/old");
        assertThat(newView).isEqualTo("redirect:/store/store-1/client/offer/new");
    }

    @Test
    void maskedEmailKeepsThreeCharacters() {
        // when / then
        assertThat(ClientOfferController.maskEmail("jan.kowalski@example.com")).isEqualTo("jan***");
        assertThat(ClientOfferController.maskEmail("ab")).isEqualTo("ab***");
        assertThat(ClientOfferController.maskEmail(" ")).isEqualTo("***");
        assertThat(ClientOfferController.maskEmail(null)).isEqualTo("***");
    }

    private static ClientDataDto validDto() {
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan");
        billing.setSurname("Kowalski");
        billing.setStreetAndNumber("ul. Długa 5");
        billing.setPostalCode("30-001");
        billing.setCity("Kraków");
        billing.setCountry("PL");
        billing.setEmail("jan.kowalski@example.com");
        billing.setPhone("+48 600 100 200");
        ClientDataDto dto = new ClientDataDto();
        dto.setBillingDetails(billing);
        dto.setShippingDetails(new ShippingDetails());
        return dto;
    }

    private Basket basket() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        basket.setCreatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return basket;
    }

    private Basket submittedBasket() {
        Basket basket = basket();
        ClientOfferDataForm form = ClientOfferDataForm.of(validDto(), "person", "same", false);
        basket.setBillingDetails(form.toBilling());
        basket.setShippingDetails(form.toShipping());
        return basket;
    }

    private static ClientOfferPage page(boolean submitted) {
        return new ClientOfferPage(OFFER_ID, "offer-1-", OFFER_PATH, null, false, false, true, List.of(),
                new ClientOfferPage.Delivery("Kurier", null, "20,00", false), false, "100,00", "18,70", "81,30",
                submitted, null, new ClientOfferPage.Payment(submitted, false, List.of(), null, null,
                submitted ? "jan.kowalski@example.com" : null));
    }
}
