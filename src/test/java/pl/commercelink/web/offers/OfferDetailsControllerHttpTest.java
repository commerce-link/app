package pl.commercelink.web.offers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractView;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketType;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.baskets.ContactDetails;
import pl.commercelink.baskets.OfferItemReloader;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.stores.DeactivationReason;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoreTrialService;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.SecurityContextLogin;
import pl.commercelink.web.activity.DashboardReadOnlyInterceptor;
import pl.commercelink.web.dtos.AddItemsForm;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The offer details page through the HTTP layer: the form field names of the old page still bind, a missing record is a
 * 404 on every address, a refused dialog answers 422 to the page script, and the dashboard of an inactive store refuses
 * the background save the way offer-details.js expects (403 text/plain). Standalone MockMvc does not evaluate
 * PreAuthorize, so the rule against the super admin is pinned on the class.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferDetailsControllerHttpTest {

    private static final String STORE_ID = "abc123def4";
    private static final String OFFER_ID = "offer-1";
    private static final String REFUSED = "Sklep jest nieaktywny.";
    private static final DeactivationStatus INACTIVE = new DeactivationStatus(DeactivationReason.TRIAL_ENDED,
            LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-26"), 6);

    @Mock private BasketsRepository basketsRepository;
    @Mock private OfferDetailsPageFactory pageFactory;
    @Mock private MessageSource messages;
    @Mock private OfferItemReloader offerItemReloader;
    @Mock private OfferItemsDraft offerItemsDraft;
    @Mock private StoresRepository storesRepository;
    @Mock private StoreTrialService storeTrialService;
    @Mock private StoreActivity storeActivity;

    private final Store store = new Store();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        SecurityContextLogin.logInAs("ADMIN", STORE_ID);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeActivity.status(store)).thenReturn(Optional.empty());
        when(messages.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(call -> call.getArgument(0));
        DashboardReadOnlyInterceptor readOnly = new DashboardReadOnlyInterceptor(storesRepository, storeTrialService,
                storeActivity, messages, "/dashboard");
        mvc = MockMvcBuilders.standaloneSetup(new OfferDetailsController(basketsRepository, pageFactory, messages,
                        offerItemReloader, offerItemsDraft))
                .addMappedInterceptors(new String[]{"/dashboard/**"}, readOnly)
                .setViewResolvers(new RenderingTheOfferPage())
                .build();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void itemsSaveBindsTheFieldNamesOfTheOldForm() throws Exception {
        // given
        Basket stored = storedOffer();

        // when
        mvc.perform(post("/dashboard/offer/" + OFFER_ID)
                        .param("basketItems[0].id", "pim-1")
                        .param("basketItems[0].catalogId", "cat-1")
                        .param("basketItems[0].estimatedDeliveryDays", "4")
                        .param("basketItems[0].service", "false")
                        .param("basketItems[0].variantGroupId", "Karta")
                        .param("basketItems[0].variantSelected", "true")
                        .param("basketItems[0].category", "GPU")
                        .param("basketItems[0].name", "RTX 5060")
                        .param("basketItems[0].mfn", "V537-002R")
                        .param("basketItems[0].qty", "2")
                        .param("basketItems[0].unitPrice", "1799.50")
                        .param("basketItems[0].unitCost", "1534.17")
                        .param("basketItems[0].consolidated", "true"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/dashboard/offer/" + OFFER_ID));

        // then
        BasketItem item = saved().getBasketItems().get(0);
        assertThat(item.getId()).isEqualTo("pim-1");
        assertThat(item.getCatalogId()).isEqualTo("cat-1");
        assertThat(item.getEstimatedDeliveryDays()).isEqualTo(4);
        assertThat(item.isService()).isFalse();
        assertThat(item.getVariantGroupId()).isEqualTo("Karta");
        assertThat(item.isVariantSelected()).isTrue();
        assertThat(item.getCategory()).isEqualTo("GPU");
        assertThat(item.getName()).isEqualTo("RTX 5060");
        assertThat(item.getQty()).isEqualTo(2);
        assertThat(item.getUnitPrice()).isEqualTo(1799.50);
        assertThat(item.getUnitCost()).isEqualTo(1534.17);
        assertThat(item.isConsolidated()).isTrue();
        assertThat(stored.getName()).isEqualTo("Gaming PC");
    }

    @Test
    void itemsSaveIgnoresTheFieldsOfTheDialogsPostedAlongside() throws Exception {
        // given
        storedOffer();

        // when
        mvc.perform(post("/dashboard/offer/" + OFFER_ID)
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10")
                        .param("name", "Hacked").param("comment", "hacked").param("gclid", "x")
                        .param("contactDetails.email", "evil@example.com").param("storeId", "other-store")
                        .param("type", "OfferTemplate").param("showPrices", "false"))
                .andExpect(status().isFound());

        // then
        Basket saved = saved();
        assertThat(saved.getName()).isEqualTo("Gaming PC");
        assertThat(saved.getComment()).isEqualTo("Call back on Monday");
        assertThat(saved.getGclid()).isEqualTo("gclid-1");
        assertThat(saved.getContactDetails().getEmail()).isEqualTo("jan@test.local");
        assertThat(saved.getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getType()).isEqualTo(BasketType.Offer);
        assertThat(saved.isShowPrices()).isTrue();
    }

    @Test
    void termsBindTheDateAndTimeOfTheBrowserField() throws Exception {
        // given
        storedOffer();

        // when
        mvc.perform(post("/dashboard/offer/" + OFFER_ID + "/terms")
                        .param("name", "Gaming PC").param("expiresAt", "2026-12-24T15:45")
                        .param("fulfilmentType", "WarehouseFulfilment").param("deliveryOptionId", "pickup"))
                .andExpect(status().isFound());

        // then
        assertThat(saved().getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 12, 24, 15, 45));
        assertThat(saved().getDeliveryOptionId()).isEqualTo("pickup");
    }

    @Test
    void unknownOfferIsNotFoundOnEveryAddressOfThePage() throws Exception {
        // given
        when(basketsRepository.findById(STORE_ID, "missing")).thenReturn(Optional.empty());

        // when / then
        for (String path : List.of("", "/items/0", "/order", "/recalculate")) {
            mvc.perform(get("/dashboard/offer/missing" + path)).andExpect(status().isNotFound());
        }
        for (String path : List.of("", "/terms", "/client", "/note", "/items/0", "/order")) {
            mvc.perform(post("/dashboard/offer/missing" + path).param("name", "x")).andExpect(status().isNotFound());
        }
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void itemOutsideTheOfferIsNotFound() throws Exception {
        // given
        storedOffer();

        // when / then
        mvc.perform(get("/dashboard/offer/" + OFFER_ID + "/items/1")).andExpect(status().isNotFound());
        mvc.perform(get("/dashboard/offer/" + OFFER_ID + "/items/-1")).andExpect(status().isNotFound());
    }

    @Test
    void refusedDialogAnswersThePageScriptWithItsFragment() throws Exception {
        // given
        storedOffer();

        // when / then
        mvc.perform(post("/dashboard/offer/" + OFFER_ID + "/client").header("X-Requested-With", "fetch")
                        .param("name", "").param("email", ""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("offers/details :: clientForm"));
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void inactiveStoreRefusesTheBackgroundSaveWithTheReasonAsPlainText() throws Exception {
        // given
        storedOffer();
        when(storeActivity.status(store)).thenReturn(Optional.of(INACTIVE));
        when(messages.getMessage(eq("store.inactive.read-only"), isNull(), any(Locale.class))).thenReturn(REFUSED);

        // when / then: offer-details.js posts with fetch's default Accept and reads a 403 text/plain as the reason
        mvc.perform(post("/dashboard/offer/" + OFFER_ID).header("Accept", "*/*")
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(REFUSED));
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void theDraftAnswersTheRowsAndWhatWasSkippedWithoutSaving() throws Exception {
        // given
        storedOffer();
        when(offerItemsDraft.add(any(), any())).thenReturn(1);
        when(pageFactory.buildDraft(any(), any(), any(), any())).thenReturn(OfferDetailsRenderingTest.fullOffer());

        // when
        String html = mvc.perform(post("/dashboard/offer/" + OFFER_ID + "/items/draft")
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10")
                        .param("items[0].source", "pricelist").param("items[0].catalogId", "cat-1")
                        .param("items[0].pimId", "pim-1").param("items[0].qty", "2")
                        .param("items[1].source", "inventory").param("items[1].mfn", "GONE-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("offers/details :: itemsDraft"))
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(html).contains("<tr class=\"cl-line\" data-cl-line", "data-cl-draft-meta data-skipped=\"1\" data-requested=\"2\"")
                // both sections of the table, which offer-lines.js puts in place of its own
                .contains("data-cl-lines-section=\"products\"", "data-cl-lines-section=\"services\"");
        ArgumentCaptor<AddItemsForm> added = ArgumentCaptor.forClass(AddItemsForm.class);
        ArgumentCaptor<Basket> drafted = ArgumentCaptor.forClass(Basket.class);
        verify(offerItemsDraft).add(drafted.capture(), added.capture());
        assertThat(added.getValue().entries()).extracting(AddItemsForm.Entry::getPimId).containsExactly("pim-1", null);
        assertThat(added.getValue().entries().get(0).getQty()).isEqualTo(2);
        assertThat(drafted.getValue().getBasketItems()).extracting(BasketItem::getMfn).containsExactly("M-1");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void theDraftOfAnUnknownOfferIsNotFound() throws Exception {
        // given
        when(basketsRepository.findById(STORE_ID, "missing")).thenReturn(Optional.empty());

        // when / then
        mvc.perform(post("/dashboard/offer/missing/items/draft")).andExpect(status().isNotFound());
    }

    @Test
    void aSaveOverAChangedVersionAnswersConflictWithTheAlert() throws Exception {
        // given
        storedOffer().setVersion(8L);
        when(pageFactory.buildDraft(any(), any(), any(), any())).thenReturn(OfferDetailsRenderingTest.fullOffer());

        // when
        String html = mvc.perform(post("/dashboard/offer/" + OFFER_ID).param("version", "7")
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10"))
                .andExpect(status().isConflict())
                .andExpect(view().name("offers/details"))
                .andReturn().getResponse().getContentAsString();

        // then
        assertThat(html).contains("data-cl-offer-conflict", "Oferta zmieniła się od otwarcia strony",
                "name=\"overwrite\" value=\"true\"");
        verify(basketsRepository, never()).save(any());
    }

    @Test
    void overwriteAndABlankVersionSave() throws Exception {
        // given
        storedOffer().setVersion(8L);

        // when
        mvc.perform(post("/dashboard/offer/" + OFFER_ID).param("version", "7").param("overwrite", "true")
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10"))
                .andExpect(status().isFound());
        mvc.perform(post("/dashboard/offer/" + OFFER_ID).param("version", "")
                        .param("basketItems[0].name", "CPU").param("basketItems[0].mfn", "M-1")
                        .param("basketItems[0].category", "CPU").param("basketItems[0].qty", "1")
                        .param("basketItems[0].unitPrice", "10"))
                .andExpect(status().isFound());

        // then
        verify(basketsRepository, times(2)).save(any());
    }

    @Test
    void everyAddressOfThePageRefusesTheSuperAdmin() {
        // when
        PreAuthorize rule = OfferDetailsController.class.getAnnotation(PreAuthorize.class);

        // then
        assertThat(rule).isNotNull();
        assertThat(rule.value()).isEqualTo("!hasRole('SUPER_ADMIN')");
        assertThat(OfferDetailsController.class.getDeclaredMethods())
                .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class));
    }

    private Basket storedOffer() {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(OFFER_ID);
        basket.setType(BasketType.Offer);
        basket.setName("Gaming PC");
        ContactDetails contact = new ContactDetails();
        contact.setName("Jan");
        contact.setEmail("jan@test.local");
        basket.setContactDetails(contact);
        basket.setGclid("gclid-1");
        basket.setComment("Call back on Monday");
        basket.setShowPrices(true);
        basket.setSource(new OrderSource("Anna", OrderSourceType.CallCenter));
        basket.setBasketItems(new ArrayList<>(List.of(new BasketItem("pim", "name", "MFN-OLD", "Laptops", 100.0, 0, 1, null, 3, false))));
        when(basketsRepository.findById(STORE_ID, OFFER_ID)).thenReturn(Optional.of(basket));
        return basket;
    }

    private Basket saved() {
        ArgumentCaptor<Basket> captor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(captor.capture());
        return captor.getValue();
    }

    /**
     * Resolves the views by name; a redirect stays a redirect. The offer page and its draft fragment are rendered from
     * the real templates when the model has a page (as the controller builds it), anything else renders nothing.
     */
    private static final class RenderingTheOfferPage extends UrlBasedViewResolver {
        RenderingTheOfferPage() {
            setViewClass(InternalResourceView.class);
        }

        @Override
        protected View createView(String viewName, Locale locale) throws Exception {
            if (viewName.startsWith(REDIRECT_URL_PREFIX)) {
                return super.createView(viewName, locale);
            }
            String markup = switch (viewName) {
                case "offers/details" -> "<section th:replace=\"~{offers/details :: page}\"></section>";
                case "offers/details :: itemsDraft" -> "<th:block th:replace=\"~{offers/details :: itemsDraft}\"></th:block>";
                default -> null;
            };
            return new AbstractView() {
                @Override
                protected void renderMergedOutputModel(Map<String, Object> model, HttpServletRequest request,
                                                       HttpServletResponse response) throws Exception {
                    if (markup != null && model.get("page") != null) {
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(SettingsTemplateRenderer.render(markup, model));
                    }
                }
            };
        }
    }
}
