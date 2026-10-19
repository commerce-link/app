package pl.commercelink.web.offers;

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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class OfferDetailsControllerTest {

    private static final String STORE_ID = "store-1";
    private static final String OFFER_ID = "offer-1";
    private static final Locale LOCALE = Locale.forLanguageTag("pl");
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 11, 1, 23, 59);

    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private OfferDetailsPageFactory pageFactory;
    @Mock
    private MessageSource messages;

    @InjectMocks
    private OfferDetailsController controller;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setup() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        securityStub.when(CustomSecurityContext::getLoggedInUserName).thenReturn("test-user");
        when(messages.getMessage(any(String.class), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    @Test
    void itemsSaveKeepsFieldsOutsideTheTable() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        Basket form = new Basket();
        form.setBasketItems(List.of(item("MFN-NEW", 2)));
        form.setName(null);
        form.setComment(null);
        form.setShowPrices(false);
        form.setExpiresAt(null);
        form.setDeliveryOptionId(null);
        form.setContactDetails(null);
        form.setGclid(null);
        form.setFulfilmentType(FulfilmentType.WarehouseFulfilment);

        // when
        String view = controller.saveItems(OFFER_ID, form, null, new RedirectAttributesModelMap(), LOCALE);

        // then
        Basket saved = savedBasket();
        assertThat(saved.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-NEW");
        assertThat(saved.getName()).isEqualTo("Gaming PC");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES);
        assertThat(saved.getDeliveryOptionId()).isEqualTo("courier");
        assertThat(saved.getContactDetails().getEmail()).isEqualTo("jan@test.local");
        assertThat(saved.getGclid()).isEqualTo("gclid-1");
        assertThat(saved.getComment()).isEqualTo("Call back on Monday");
        assertThat(saved.isShowPrices()).isTrue();
        assertThat(saved.getFulfilmentType()).isEqualTo(FulfilmentType.DirectToConsumer);
        assertThat(view).isEqualTo("redirect:/dashboard/offer/offer-1");
    }

    @Test
    void itemsSaveDropsIncompleteItemsAsToday() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        Basket form = new Basket();
        form.setBasketItems(List.of(item("MFN-OK", 1), item("MFN-ZERO", 0)));

        // when
        controller.saveItems(OFFER_ID, form, null, new RedirectAttributesModelMap(), LOCALE);

        // then
        assertThat(savedBasket().getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-OK");
    }

    @Test
    void itemsSaveKeepsSubmittedOrderAndReindexesPositions() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        Basket form = new Basket();
        form.setBasketItems(List.of(item("MFN-B", 1), item("MFN-A", 1), item("MFN-C", 1)));

        // when
        controller.saveItems(OFFER_ID, form, null, new RedirectAttributesModelMap(), LOCALE);

        // then
        List<BasketItem> saved = savedBasket().getBasketItems();
        assertThat(saved).extracting(BasketItem::getMfn).containsExactly("MFN-B", "MFN-A", "MFN-C");
        assertThat(saved).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void itemsSaveKeepsAMigratedServiceWithoutCategory() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        BasketItem service = new BasketItem("pim", "name", "MONTAZ-1", "", 100.0, 0, 1, null, 3, false);
        service.setService(true);
        BasketItem product = item("MFN-P", 1);
        Basket form = new Basket();
        form.setBasketItems(List.of(service, product));

        // when
        controller.saveItems(OFFER_ID, form, null, new RedirectAttributesModelMap(), LOCALE);

        // then
        List<BasketItem> saved = savedBasket().getBasketItems();
        assertThat(saved).hasSize(2);
        assertThat(saved).filteredOn(BasketItem::isService).singleElement().satisfies(i -> assertThat(i.getCategory()).isNull());
        assertThat(saved).filteredOn(i -> !i.isService()).noneMatch(BasketItem::isService);
    }

    @Test
    void termsSaveChangesOnlyTerms() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        stored.setBasketItems(List.of(item("MFN-A", 1)));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        OfferTermsForm form = new OfferTermsForm();
        form.setName("Renamed");
        form.setExpiresAt(EXPIRES.plusDays(7));
        form.setDeliveryOptionId("pickup");
        form.setShowPrices(false);
        form.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        form.setGclid("gclid-2");

        // when
        String view = controller.saveTerms(OFFER_ID, form, null, null, new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        Basket saved = savedBasket();
        assertThat(saved.getName()).isEqualTo("Renamed");
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES.plusDays(7));
        assertThat(saved.getDeliveryOptionId()).isEqualTo("pickup");
        assertThat(saved.getGclid()).isEqualTo("gclid-2");
        assertThat(saved.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-A");
        assertThat(saved.getContactDetails().getEmail()).isEqualTo("jan@test.local");
        assertThat(saved.getComment()).isEqualTo("Call back on Monday");
        assertThat(view).isEqualTo("redirect:/dashboard/offer/offer-1");
    }

    @Test
    void termsWithBlankNameAnswer422WhenAsync() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        OfferTermsForm form = new OfferTermsForm();
        form.setName("  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveTerms(OFFER_ID, form, null, "fetch", model, LOCALE,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("offers/details :: termsForm");
        assertThat((Map<String, String>) model.getAttribute("errors")).containsEntry("name", "offers.name.required");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void asyncTermsSaveAnswersTheFragment() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        OfferTermsForm form = new OfferTermsForm();
        form.setName("Renamed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.saveTerms(OFFER_ID, form, null, "fetch", new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(view).isEqualTo("offers/details :: termsForm");
        verify(basketsRepository).save(any());
    }

    @Test
    void blankNameWithoutScriptRendersThePageWithTheDialogOpen() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveTerms(OFFER_ID, new OfferTermsForm(), null, null, model, LOCALE,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        assertThat(view).isEqualTo("offers/details");
        assertThat(model.getAttribute("open")).isEqualTo("terms");
    }

    @Test
    void templateTermsIgnoreOfferOnlyFields() {
        // given
        Basket template = new Basket();
        template.setStoreId(STORE_ID);
        template.setBasketId(OFFER_ID);
        template.setType(BasketType.OfferTemplate);
        template.setExpiresAt(null);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(template));
        OfferTermsForm form = new OfferTermsForm();
        form.setName("Template");
        form.setShowPrices(true);
        form.setFulfilmentType(FulfilmentType.DirectToConsumer);
        form.setExpiresAt(EXPIRES);
        form.setDeliveryOptionId("courier");
        form.setGclid("gclid-x");

        // when
        controller.saveTerms(OFFER_ID, form, null, null, new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        Basket saved = savedBasket();
        assertThat(saved.getName()).isEqualTo("Template");
        assertThat(saved.isShowPrices()).isTrue();
        assertThat(saved.getFulfilmentType()).isEqualTo(FulfilmentType.DirectToConsumer);
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getDeliveryOptionId()).isNull();
        assertThat(saved.getGclid()).isNull();
    }

    @Test
    void clientRuleMatchesOfferCreation() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        ContactDetails surnameOnly = new ContactDetails();
        surnameOnly.setSurname("Kowalski");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveClient(OFFER_ID, surnameOnly, null, "fetch", model, LOCALE,
                new RedirectAttributesModelMap(), response);

        // then
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(view).isEqualTo("offers/details :: clientForm");
        assertThat((Map<String, String>) model.getAttribute("errors")).containsEntry("contact", "offers.contact.invalid");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void clientSaveReplacesOnlyTheContact() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        ContactDetails contact = new ContactDetails();
        contact.setCompanyName("ACME");
        contact.setPhone("123");

        // when
        controller.saveClient(OFFER_ID, contact, null, null, new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        Basket saved = savedBasket();
        assertThat(saved.getContactDetails().getCompanyName()).isEqualTo("ACME");
        assertThat(saved.getName()).isEqualTo("Gaming PC");
    }

    @Test
    void noteSaveChangesOnlyTheComment() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        OfferNoteForm form = new OfferNoteForm();
        form.setComment("New note");

        // when
        controller.saveNote(OFFER_ID, form, null, null, new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap(), new MockHttpServletResponse());

        // then
        Basket saved = savedBasket();
        assertThat(saved.getComment()).isEqualTo("New note");
        assertThat(saved.getName()).isEqualTo("Gaming PC");
    }

    @Test
    void clientOnTemplateIsNotFound() {
        // given
        Basket template = fullOffer(BasketType.OfferTemplate);
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(template));

        // when / then
        assertThatThrownBy(() -> controller.saveClient(OFFER_ID, new ContactDetails(), null, null,
                new ExtendedModelMap(), LOCALE, new RedirectAttributesModelMap(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unknownOfferIsNotFound() {
        // given
        when(basketsRepository.findById(STORE_ID, "missing")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.view("missing", null, null, new ExtendedModelMap(), LOCALE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void offerOfAnotherStoreIsNotFound() {
        // given: the lookup is by the session's store, so another store's record is simply absent
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> controller.saveItems(OFFER_ID, new Basket(), null, new RedirectAttributesModelMap(), LOCALE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void itemEditSavesOneItem() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        stored.setBasketItems(List.of(item("MFN-A", 1), item("MFN-B", 1)));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        OfferItemForm form = new OfferItemForm();
        form.setName("Renamed item");
        form.setCategory("Laptops");
        form.setMfn("MFN-B");
        form.setUnitCost(42.5);
        form.setConsolidated(true);

        // when
        String view = controller.saveItem(OFFER_ID, 1, form, null, new ExtendedModelMap(), LOCALE,
                new RedirectAttributesModelMap());

        // then
        List<BasketItem> items = savedBasket().getBasketItems();
        assertThat(items.get(1).getName()).isEqualTo("Renamed item");
        assertThat(items.get(1).getUnitCost()).isEqualTo(42.5);
        assertThat(items.get(1).isConsolidated()).isTrue();
        assertThat(items.get(0).getName()).isEqualTo("name");
        assertThat(items.get(0).getUnitCost()).isEqualTo(0);
        assertThat(view).isEqualTo("redirect:/dashboard/offer/offer-1");
    }

    @Test
    void itemEditWithBlankNameIsRejectedNotDropped() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        stored.setBasketItems(List.of(item("MFN-A", 1)));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));
        OfferItemForm form = new OfferItemForm();
        form.setName(" ");
        form.setCategory("Laptops");
        form.setMfn("MFN-A");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.saveItem(OFFER_ID, 0, form, null, model, LOCALE, new RedirectAttributesModelMap());

        // then
        assertThat(view).isEqualTo("offers/item-edit");
        assertThat((Map<String, String>) model.getAttribute("errors")).containsKey("name");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void itemEditOutsideTheListIsNotFound() {
        // given
        Basket stored = fullOffer(BasketType.Offer);
        stored.setBasketItems(List.of(item("MFN-A", 1), item("MFN-B", 1)));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(stored));

        // when / then
        assertThatThrownBy(() -> controller.saveItem(OFFER_ID, 5, new OfferItemForm(), null, new ExtendedModelMap(),
                LOCALE, new RedirectAttributesModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.showItem(OFFER_ID, 5, null, new ExtendedModelMap(), LOCALE))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void orderConfirmPostRedirectsToNewOrder() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));

        // when
        String view = controller.order(OFFER_ID);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/orders/new/from-basket?basketId=offer-1");
    }

    @Test
    void confirmPagesUseTheSharedConfirmView() {
        // given
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(fullOffer(BasketType.Offer)));
        ExtendedModelMap orderModel = new ExtendedModelMap();
        ExtendedModelMap recalcModel = new ExtendedModelMap();

        // when
        String orderView = controller.confirmOrder(OFFER_ID, null, orderModel, LOCALE);
        String recalcView = controller.confirmRecalculate(OFFER_ID, null, recalcModel, LOCALE);

        // then
        assertThat(orderView).isEqualTo("settings-confirm");
        assertThat(recalcView).isEqualTo("settings-confirm");
        assertThat(orderModel.getAttribute("confirm")).hasFieldOrPropertyWithValue("actionPath", "/dashboard/offer/offer-1/order");
        assertThat(recalcModel.getAttribute("confirm")).hasFieldOrPropertyWithValue("actionPath", "/dashboard/offer/offer-1/recalculate");
    }

    private Basket savedBasket() {
        ArgumentCaptor<Basket> captor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Basket fullOffer(BasketType type) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        basket.setType(type);
        basket.setName("Gaming PC");
        basket.setExpiresAt(EXPIRES);
        basket.setDeliveryOptionId("courier");
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setEmail("jan@test.local");
        basket.setContactDetails(contact);
        basket.setGclid("gclid-1");
        basket.setComment("Call back on Monday");
        basket.setShowPrices(true);
        basket.setFulfilmentType(FulfilmentType.DirectToConsumer);
        basket.setBasketItems(List.of(item("MFN-OLD", 1)));
        return basket;
    }

    private static BasketItem item(String mfn, long qty) {
        return new BasketItem("pim", "name", mfn, "Laptops", 100.0, 0, qty, null, 3, false);
    }
}
