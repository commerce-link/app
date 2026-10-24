package pl.commercelink.orders.rma;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.clientreturn.ClientReturnNotice;
import pl.commercelink.web.clientreturn.ClientReturnState;
import pl.commercelink.web.clientreturn.ClientReturnView;
import pl.commercelink.web.clientreturn.ReturnPackageOption;
import pl.commercelink.web.publicpage.PublicPage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.commercelink.testsupport.OptimisticLockingExecutorMocks.passThroughModifyAndSave;

@ExtendWith(MockitoExtension.class)
class RMAClientControllerTest {

    private static final String UNAVAILABLE = "client-return/unavailable";
    private static final String RMA_PATH = "/store/store-1/client/rma/rma-1";
    private static final String NOTICE = "clientReturnNotice";

    @Mock private RMARepository rmaRepository;
    @Mock private RMAItemsRepository rmaItemsRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private RMAShippingService rmaShippingService;
    @Mock private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private RMAClientController controller;

    // GET

    @ParameterizedTest
    @EnumSource(value = RMAStatus.class, names = {"Approved", "WaitingForItems", "ItemsReceived", "Processing", "Completed", "Rejected"})
    void aPageAnswers200ForEveryStatusFromApprovedToCompletedAndRejected(RMAStatus status) {
        // given
        Store store = returnStore();
        when(storesRepository.findById("store-1")).thenReturn(store);
        RMA rma = rma(status);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("client-return");
        assertThat(response.getStatus()).isEqualTo(200);
        PublicPage page = (PublicPage) model.get("publicPage");
        assertThat(page.storeName()).isEqualTo("Nova Tech");
        assertThat(page.otherLanguageHref()).isEqualTo(RMA_PATH + "?lang=en");
        assertThat(model.get("view")).isInstanceOf(ClientReturnView.class);
        assertThat(model.get("returnPath")).isEqualTo(RMA_PATH);
        @SuppressWarnings("unchecked")
        List<ReturnPackageOption> options = (List<ReturnPackageOption>) model.get("packageOptions");
        assertThat(options).extracting(ReturnPackageOption::id).containsExactly("7");
        assertThat(model.get("selectedPackage")).isEqualTo("7");
        RMAReturnForm form = (RMAReturnForm) model.get("form");
        assertThat(form.getShippingDetails().getCountry()).isEqualTo("PL");
        assertThat((Map<?, ?>) model.get("errors")).isEmpty();
    }

    @Test
    void newMissingAndItemlessReturnsStayUnavailable() {
        // given
        Store store = namedStore();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.New));
        when(rmaRepository.findById("store-1", "rma-2")).thenReturn(null);
        when(rmaRepository.findById("store-1", "rma-3")).thenReturn(rma(RMAStatus.Approved));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of());

        for (String rmaId : List.of("rma-1", "rma-2", "rma-3")) {
            ExtendedModelMap model = new ExtendedModelMap();
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            String view = controller.getRMAForClient("store-1", rmaId, model, response, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo(UNAVAILABLE);
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(((PublicPage) model.get("publicPage")).storeName()).isEqualTo("Nova Tech");
            assertThat(model).doesNotContainKeys("view", "form");
        }
    }

    @Test
    void aNewReturnAnswersTheUnavailablePageWith404() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.New));
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        PublicPage page = (PublicPage) model.get("publicPage");
        assertThat(page.storeName()).isEqualTo("Nova Tech");
        assertThat(page.otherLanguageHref()).isEqualTo(RMA_PATH + "?lang=en");
        assertThat(model).doesNotContainKeys("rma", "view");
        verifyNoInteractions(rmaItemsRepository);
    }

    @Test
    void aMissingReturnAnswersTheSameUnavailablePage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(((PublicPage) model.get("publicPage")).storeName()).isEqualTo("Nova Tech");
    }

    @Test
    void aReturnWithoutItemsAnswersTheUnavailablePage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.Approved));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", new ExtendedModelMap(), response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void anUnknownStoreAnswersTheUnavailablePageWithoutABrand() {
        // given
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.getRMAForClient("store-1", "rma-1", model, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        PublicPage page = (PublicPage) model.get("publicPage");
        assertThat(page.storeId()).isNull();
        assertThat(page.storeName()).isEmpty();
        verifyNoInteractions(rmaRepository);
    }

    @Test
    void aStoreWithoutReturnSettingsShowsThePageAndLogsWhenTheFormIsNeeded() {
        // given: neither a carrier nor return templates
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.Approved));
        when(rmaRepository.findById("store-1", "rma-2")).thenReturn(rma(RMAStatus.WaitingForItems));
        when(rmaItemsRepository.findByRmaId(anyString())).thenReturn(List.of(new RMAItem()));
        ExtendedModelMap approvedModel = new ExtendedModelMap();
        MockHttpServletResponse approvedResponse = new MockHttpServletResponse();
        ExtendedModelMap waitingModel = new ExtendedModelMap();
        MockHttpServletResponse waitingResponse = new MockHttpServletResponse();

        // when
        String approved = controller.getRMAForClient("store-1", "rma-1", approvedModel, approvedResponse, Locale.forLanguageTag("pl"));
        String waiting = controller.getRMAForClient("store-1", "rma-2", waitingModel, waitingResponse, Locale.forLanguageTag("pl"));

        // then
        assertThat(approved).isEqualTo("client-return");
        assertThat(approvedResponse.getStatus()).isEqualTo(200);
        assertThat(((ClientReturnView) approvedModel.get("view")).state()).isEqualTo(ClientReturnState.BOOK_UNAVAILABLE);
        assertThat(((ClientReturnView) approvedModel.get("view")).missingConfiguration()).isEqualTo("carrier");
        assertThat(approvedModel.get("selectedPackage")).isNull();
        assertThat(waiting).isEqualTo("client-return");
        assertThat(waitingResponse.getStatus()).isEqualTo(200);
        assertThat(((ClientReturnView) waitingModel.get("view")).state()).isEqualTo(ClientReturnState.WAITING);
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void flashedFormAndErrorsReachTheModel() {
        // given: a refused submission redirected here with what the customer typed
        when(storesRepository.findById("store-1")).thenReturn(returnStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.Approved));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        RMAReturnForm typed = validForm("7");
        typed.getShippingDetails().setPostalCode("00950");
        Map<String, String> errors = Map.of("shippingDetails.postalCode", "client.return.form.postal.invalid");
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("clientReturnForm", typed);
        model.addAttribute("clientReturnErrors", errors);

        // when
        controller.getRMAForClient("store-1", "rma-1", model, new MockHttpServletResponse(), Locale.forLanguageTag("pl"));

        // then
        assertThat(model.get("form")).isSameAs(typed);
        assertThat(model.get("errors")).isEqualTo(errors);
        assertThat(model.get("selectedPackage")).isEqualTo("7");
    }

    // POST — guards before the carrier

    @ParameterizedTest
    @EnumSource(value = RMAStatus.class, names = {"ItemsReceived", "Processing", "Completed", "Rejected"})
    void aStaleSubmissionOfAReturnPastTheParcelShowsTheReturnPage(RMAStatus status) {
        // given: an old browser tab of a return the store has moved on
        when(storesRepository.findById("store-1")).thenReturn(returnStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(status));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", validForm("7"), new ExtendedModelMap(),
                redirect, response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(redirect.getFlashAttributes()).isEmpty();
        verifyNoInteractions(rmaShippingService);
        verifyNoInteractions(optimisticLockingExecutor);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aStaleSubmissionOfAReturnWaitingForItsParcelPointsAtTheParcel() {
        // given: the booking from another tab went through and its parcel is on the RMA
        RMA rma = rma(RMAStatus.WaitingForItems);
        rma.setShipments(new ArrayList<>(List.of(unconfirmedReturn())));
        when(storesRepository.findById("store-1")).thenReturn(returnStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        verifyNoInteractions(rmaShippingService);
        verifyNoInteractions(optimisticLockingExecutor);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aStaleSubmissionOfAReturnWaitingWithoutAParcelCardHasNoAnchor() {
        // given: waiting for the items, the store arranged the parcel outside the page
        when(storesRepository.findById("store-1")).thenReturn(returnStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.WaitingForItems));
        when(rmaItemsRepository.findByRmaId("rma-1")).thenReturn(List.of(new RMAItem()));
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH);
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        verifyNoInteractions(rmaShippingService);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aSubmissionForANewMissingOrItemlessReturnAnswersTheUnavailablePageLinkedToTheReturnPage() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma(RMAStatus.New));
        when(rmaRepository.findById("store-1", "rma-2")).thenReturn(null);
        RMA itemless = rma(RMAStatus.WaitingForItems);
        itemless.setRmaId("rma-3");
        when(rmaRepository.findById("store-1", "rma-3")).thenReturn(itemless);
        when(rmaItemsRepository.findByRmaId("rma-3")).thenReturn(List.of());

        for (String rmaId : List.of("rma-1", "rma-2", "rma-3")) {
            ExtendedModelMap model = new ExtendedModelMap();
            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            String view = controller.postClientBillingShippingForm("store-1", rmaId, new RMAReturnForm(), model,
                    new RedirectAttributesModelMap(), response, Locale.forLanguageTag("pl"));

            // then
            assertThat(view).isEqualTo(UNAVAILABLE);
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(((PublicPage) model.get("publicPage")).otherLanguageHref())
                    .isEqualTo("/store/store-1/client/rma/" + rmaId + "?lang=en");
        }
        verifyNoInteractions(rmaShippingService);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aSubmissionInAnUnknownStoreAnswersTheUnavailablePage() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String view = controller.postClientBillingShippingForm("store-1", "rma-1", new RMAReturnForm(), new ExtendedModelMap(),
                new RedirectAttributesModelMap(), response, Locale.forLanguageTag("pl"));

        // then
        assertThat(view).isEqualTo(UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void aReturnWithASavedAddressIsNotBookedAgain() {
        // given: an old browser tab of a return whose pickup address the store already has
        RMA rma = approvedRma();
        ShippingDetails operatorsAddress = validForm("7").getShippingDetails();
        rma.setShippingDetails(operatorsAddress);
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setCity("Kraków");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(form, redirect);

        // then: the page shows no parcel card then, the notice sits above the first card and the address is below it
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-address");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.storeBooks"));
        assertThat(rma.getShippingDetails()).isSameAs(operatorsAddress);
        assertThat(rma.getShippingDetails().getCity()).isEqualTo("Warszawa");
        verifyNoInteractions(rmaShippingService);
        verifyNoInteractions(optimisticLockingExecutor);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aReturnWithACustomerParcelIsNotBookedAgain() {
        // given: the save after a started booking failed, so the RMA has the parcel but no address
        RMA rma = approvedRma();
        rma.getShipments().add(unconfirmedReturn());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        verifyNoInteractions(rmaShippingService);
        verifyNoInteractions(optimisticLockingExecutor);
        verify(rmaRepository, never()).save(any());
    }

    @Test
    void aMarketplaceParcelWithoutAProviderCountsAsTheCustomersParcel() {
        // given: an imported Allegro return parcel
        RMA rma = approvedRma();
        rma.setExternalReturnId("allegro-return-1");
        rma.getShipments().add(new Shipment(ShipmentType.Courier));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        submit(validForm("7"), redirect);

        // then
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void anInvalidFormNeverReachesTheCarrier() {
        // given
        approvedRma();
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setPhone("601-234-567");
        form.getShippingDetails().setName("  ");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(form, redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) redirect.getFlashAttributes().get("clientReturnErrors");
        assertThat(errors).containsOnlyKeys("shippingDetails.name", "shippingDetails.phone");
        RMAReturnForm flashed = (RMAReturnForm) redirect.getFlashAttributes().get("clientReturnForm");
        assertThat(flashed.getShippingDetails().getPhone()).isEqualTo("601-234-567");
        assertThat(flashed.getShippingDetails().getName()).isEqualTo("  ");
        assertThat(redirect.getFlashAttributes()).doesNotContainKey(NOTICE);
        verifyNoInteractions(rmaShippingService);
        verifyNoInteractions(optimisticLockingExecutor);
    }

    @Test
    void aTemplateOutsideTheListIsRefusedBeforeTheCarrier() {
        // given: a template of the store, but not one offered for returns
        approvedRma();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("outgoing-box"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) redirect.getFlashAttributes().get("clientReturnErrors");
        assertThat(errors).containsOnlyKeys("selectedPackageTemplateId");
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void aStoreWithoutReturnTemplatesRefusesTheBookingWithoutTheCarrier() {
        // given
        RMA rma = rma(RMAStatus.Approved);
        rma.setShipments(new ArrayList<>());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(storesRepository.findById("store-1")).thenReturn(namedStore());
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RMAReturnForm form = validForm("7");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(form, redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "client.return.notice.unavailable"));
        assertThat(redirect.getFlashAttributes().get("clientReturnForm")).isSameAs(form);
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void aStoreWithoutACarrierRefusesTheBookingBeforeCheckingTheForm() {
        // given: a return template is there, the carrier is not; the page of such a store shows no form fields
        RMA rma = rma(RMAStatus.Approved);
        rma.setShipments(new ArrayList<>());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        Store store = returnStore();
        store.setRmaConfiguration(null);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setPhone("601-234-567");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(form, redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "client.return.notice.unavailable"));
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("clientReturnErrors");
        assertThat(redirect.getFlashAttributes().get("clientReturnForm")).isSameAs(form);
        verifyNoInteractions(rmaShippingService);
    }

    @Test
    void theOutcomeTextsAreResolvedBeforeTheCarrierIsCalled() {
        // given
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        submit(validForm("7"), redirect);

        // then: no lookup after the carrier call could turn a started booking into the generic failure
        InOrder order = inOrder(messageSource, rmaShippingService);
        order.verify(messageSource).getMessage(eq("client.return.notice.booked"), any(), any());
        order.verify(messageSource).getMessage(eq("client.return.notice.inProgress"), any(), any());
        order.verify(messageSource).getMessage(eq("client.return.notice.failed"), any(), any());
        order.verify(rmaShippingService).startReturnShipment(any(), any());
        order.verify(messageSource, never()).getMessage(anyString(), any(), any());
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.ok(ClientReturnNotice.PARCEL, "client.return.notice.booked"));
    }

    @Test
    void theCountryIsAlwaysPoland() {
        // given
        approvedRma();
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setCountry("DE");
        form.getShippingDetails().setCity("  Warszawa ");
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        ArgumentCaptor<RMAShipmentRequest> request = ArgumentCaptor.forClass(RMAShipmentRequest.class);

        // when
        submit(form, new RedirectAttributesModelMap());

        // then
        verify(rmaShippingService).startReturnShipment(request.capture(), any());
        assertThat(request.getValue().getCustomerAddress().getCountry()).isEqualTo("PL");
        assertThat(request.getValue().getCustomerAddress().getCity()).isEqualTo("Warszawa");
        assertThat(request.getValue().getPackageTemplateId()).isEqualTo("7");
        assertThat(request.getValue().getRmaId()).isEqualTo("rma-1");
    }

    // POST — outcomes of the booking

    @Test
    void theCustomersPackageTemplateIsKeptWithTheAddressForARetryOfTheReturn() {
        // given
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setStreetAndNumber(" Prosta 1 ");

        // when
        submit(form, new RedirectAttributesModelMap());

        // then: the address saved is the one the carrier was given
        assertThat(rma.getReturnPackageTemplateId()).isEqualTo("7");
        assertThat(rma.getShippingDetails().getStreetAndNumber()).isEqualTo("Prosta 1");
        assertThat(rma.getShippingDetails().getName()).isEqualTo("Jan");
        assertThat(rma.getShippingDetails().getCountry()).isEqualTo("PL");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.WaitingForItems);
    }

    @Test
    void aStartedReturnIsReportedAsBookedInTheParcelCard() {
        // given
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.ok(ClientReturnNotice.PARCEL, "client.return.notice.booked"));
        assertThat(redirect.getFlashAttributes()).doesNotContainKey("clientReturnForm");
    }

    @Test
    void aStartedReturnIsReportedAsStartedEvenWhenTheRmaCouldNotBeUpdatedAfterwards() {
        // given
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.STARTED, null, false));
        doThrow(new RuntimeException("DynamoDB")).when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.ok(ClientReturnNotice.PARCEL, "client.return.notice.booked"));
    }

    @Test
    void aResubmissionRefusedBecauseTheReturnIsAlreadyHandledGetsANeutralMessage() {
        // given: a booking from another tab got there first, after this RMA was read
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.GONE, null, false));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
    }

    @Test
    void anUnconfirmedReturnIsNotExplainedToTheCustomerInTheOperatorsWords() {
        // given: the check could not be sent, so the paid label may exist and only the operator can tell
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenAnswer(i -> {
            rma.getShipments().add(unconfirmedReturn());
            return new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED,
                    "Furgonetka nie potwierdziła nadania — sprawdź przesyłkę w jej panelu", false);
        });
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-parcel");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.info(ClientReturnNotice.PARCEL, "client.return.notice.inProgress"));
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.WaitingForItems);
        assertThat(rma.getReturnPackageTemplateId()).isEqualTo("7");
        assertThat(rma.getShippingDetails()).isNotNull();
    }

    @Test
    void aCleanRefusalShowsTheReasonSoTheCustomerCanCorrectTheData() {
        // given
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any()))
                .thenReturn(new ShipmentCreationStart(ShipmentCreationStart.Outcome.REFUSED, "Nieprawidłowy kod pocztowy", true));
        RMAReturnForm form = validForm("7");
        form.getShippingDetails().setCity(" Warszawa ");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(form, redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "Nieprawidłowy kod pocztowy"));
        assertThat(redirect.getFlashAttributes().get("clientReturnForm")).isSameAs(form);
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Approved);
        verify(optimisticLockingExecutor, never()).modifyAndSave(any(), any(), any());
    }

    @Test
    void aRefusalWithoutTheProvidersAnswerIsNotShownToTheCustomerInTechnicalWords() {
        // given: the adapter gave up before the provider answered, in its own English words
        RMA rma = approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenReturn(new ShipmentCreationStart(
                ShipmentCreationStart.Outcome.REFUSED, "could not create the package: Read timed out", false));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "client.return.notice.failed"));
        assertThat(redirect.getFlashAttributes()).containsKey("clientReturnForm");
        assertThat(rma.getStatus()).isEqualTo(RMAStatus.Approved);
    }

    @Test
    void aMissingCarrierIsReportedInTheCustomersWordsNotTheExceptions() {
        // given
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenThrow(
                new InvalidReturnConfigurationException("Store return settings not configured. Contact store administrator."));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        // when
        String view = submit(validForm("7"), redirect);

        // then
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "client.return.notice.unavailable"));
    }

    @Test
    void anyOtherFailureKeepsTheCustomersDataWithAGeneralMessage() {
        // given
        approvedRma();
        when(rmaShippingService.startReturnShipment(any(), any())).thenThrow(new IllegalStateException("boom"));
        RMAReturnForm form = validForm("7");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        Logger logger = (Logger) LoggerFactory.getLogger(RMAClientController.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);

        // when
        String view;
        try {
            view = submit(form, redirect);
        } finally {
            logger.detachAppender(logs);
        }

        // then: the operator learns of it, the customer only gets the general message
        assertThat(logs.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("Return shipment of RMA rma-1 in store store-1 failed to start");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
        });
        assertThat(view).isEqualTo("redirect:" + RMA_PATH + "#return-form");
        assertThat(redirect.getFlashAttributes().get(NOTICE))
                .isEqualTo(ClientReturnNotice.bad(ClientReturnNotice.FORM, "client.return.notice.failed"));
        assertThat(redirect.getFlashAttributes().get("clientReturnForm")).isSameAs(form);
    }

    // fixtures

    private static Store namedStore() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setName("Nova Tech");
        return store;
    }

    /** A store with a return carrier, the return template "7" and an outgoing template that is not offered for returns. */
    private static Store returnStore() {
        Store store = namedStore();
        RMAConfiguration rmaConfiguration = new RMAConfiguration();
        rmaConfiguration.setCarrier(new AuthorizedCarrier("c1", "Carrier", "Carrier"));
        store.setRmaConfiguration(rmaConfiguration);
        PackageTemplate returnBox = new PackageTemplate("RMA - Karton S", List.of());
        returnBox.setId("7");
        PackageTemplate outgoingBox = new PackageTemplate("Karton wysyłkowy", List.of());
        outgoingBox.setId("outgoing-box");
        ShippingConfiguration shipping = new ShippingConfiguration();
        shipping.setPackageTemplates(new ArrayList<>(List.of(returnBox, outgoingBox)));
        store.setShippingConfiguration(shipping);
        return store;
    }

    private static RMA rma(RMAStatus status) {
        RMA rma = new RMA("store-1");
        rma.setRmaId("rma-1");
        rma.setStatus(status);
        return rma;
    }

    private RMA approvedRma() {
        RMA rma = rma(RMAStatus.Approved);
        rma.setShipments(new ArrayList<>());
        when(rmaRepository.findById("store-1", "rma-1")).thenReturn(rma);
        when(storesRepository.findById("store-1")).thenReturn(returnStore());
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(optimisticLockingExecutor.modifyAndSave(any(), any(), any())).thenAnswer(passThroughModifyAndSave());
        return rma;
    }

    private static RMAReturnForm validForm(String templateId) {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setStreetAndNumber("Prosta 1");
        details.setPostalCode("00-950");
        details.setCity("Warszawa");
        details.setEmail("jan@example.com");
        details.setPhone("+48 601 234 567");
        details.setCountry("PL");
        RMAReturnForm form = new RMAReturnForm();
        form.setShippingDetails(details);
        form.setSelectedPackageTemplateId(templateId);
        return form;
    }

    private static Shipment unconfirmedReturn() {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setProvider("furgonetka");
        s.setCreation(ShipmentCreationState.pending("cmd-0", LocalDateTime.now())
                .failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));
        return s;
    }

    private String submit(RMAReturnForm form, RedirectAttributesModelMap redirect) {
        return controller.postClientBillingShippingForm("store-1", "rma-1", form, new ExtendedModelMap(), redirect,
                new MockHttpServletResponse(), Locale.forLanguageTag("pl"));
    }
}
