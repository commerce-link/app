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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveriesManager;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.InvoiceLinkingService;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.deliveries.details.DeliveryPageModel;
import pl.commercelink.web.deliveries.details.TermsDialog;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.DeliveryTermsForm;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.settings.ConfirmAction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveriesControllerDetailsPageTest {

    private static final String STORE_ID = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesManager deliveriesManager;
    @Mock
    private InvoiceLinkingService invoiceLinkingService;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;
    @InjectMocks
    private DeliveriesController controller;

    private MockedStatic<CustomSecurityContext> security;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        security.when(() -> CustomSecurityContext.hasRole("ADMIN")).thenReturn(true);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
        when(supplierLabels.forStoreId(any())).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        request = new MockHttpServletRequest();
        // OrderFlash.forNextPage writes into the output flash map the DispatcherServlet would have prepared
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    private static Delivery delivery() {
        Delivery delivery = new Delivery(STORE_ID, "MH-1", "Manual-Hurt", LocalDate.of(2026, 10, 8), 0, 0, 14, 1.23);
        delivery.setAllocations(new ArrayList<>());
        return delivery;
    }

    private static DeliveryPageModel page(Model model) {
        return (DeliveryPageModel) model.getAttribute("page");
    }

    private static DeliveryTermsForm terms(Delivery delivery) {
        DeliveryTermsForm form = DeliveryTermsForm.of(delivery);
        form.setShippingCost("19,90");
        return form;
    }

    @Test
    void theDetailsPageIsBuiltFromTheModelWithTheListToReturnTo() {
        // given
        Delivery delivery = delivery();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        Model model = new ConcurrentModel();

        // when
        String view = controller.showDeliveryDetails(delivery.getDeliveryId(), "/dashboard/deliveries?scope=all", "terms", null,
                model, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("deliveries/details");
        assertThat(page(model).backHref()).isEqualTo("/dashboard/deliveries?scope=all");
        assertThat(page(model).openDialog()).isEqualTo("terms");
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).action()).isEqualTo("/dashboard/deliveries/details");
    }

    @Test
    void withoutJavaScriptAConfirmationRendersThePageWithTheSelectionAndTheActionsDialog() {
        // given
        Delivery delivery = delivery();
        Allocation allocation = new Allocation();
        allocation.setInAllocation(true);
        allocation.setMfn("MFN-1");
        allocation.setType(pl.commercelink.inventory.deliveries.AllocationType.Warehouse);
        delivery.setAllocations(new ArrayList<>(List.of(allocation)));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        Allocation posted = new Allocation();
        posted.setSelected(true);
        form.setAllocations(new ArrayList<>(List.of(posted)));
        Model model = new ConcurrentModel();

        // when
        String view = controller.confirmSelection(delivery.getDeliveryId(), "split", form, model, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("deliveries/details");
        assertThat(page(model).openDialog()).isEqualTo("split");
        assertThat(page(model).items().products().get(0).allocations().get(0).checked()).isTrue();
    }

    @Test
    void aConfirmationWithNothingCheckedGoesBackWithAMessage() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        String view = controller.confirmSelection(delivery.getDeliveryId(), "receive", new DeliveryAllocationsForm(),
                new ConcurrentModel(), redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.select.at.least.one");
    }

    @Test
    void anUnknownConfirmationIsNotFound() {
        // when / then
        assertThatThrownBy(() -> controller.confirmSelection("d-1", "explode", new DeliveryAllocationsForm(),
                new ConcurrentModel(), redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void asyncSaveWithErrorsAnswers422WithTheDialogForm() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = terms(delivery);
        form.setVat("150");
        Model model = new ConcurrentModel();

        // when
        String view = controller.updateDelivery(form, "fetch", request, response, model, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("deliveries/details/dialogs :: termsForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).errors()).containsKey("vat");
        verify(deliveriesManager, never()).updateDelivery(any());
    }

    @Test
    void asyncSaveStoresTheTermsKeepsTheCommentAndLeavesANoticeForTheReloadedPage() {
        // given
        Delivery delivery = delivery();
        delivery.setComment("Rampa B");
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = terms(delivery);
        form.setComment("forged");
        Model model = new ConcurrentModel();

        // when
        String view = controller.updateDelivery(form, "fetch", request, response, model, redirectAttributes, PL);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesManager).updateDelivery(saved.capture());
        assertThat(view).isEqualTo("deliveries/details/dialogs :: termsForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE_ID);
        assertThat(saved.getValue().getShippingCost()).isEqualTo(19.9);
        assertThat(saved.getValue().getComment()).isEqualTo("Rampa B");
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).savedMessage()).isEqualTo("deliveries.details.terms.saved");
    }

    @Test
    void theCommentDialogChangesOnlyTheComment() {
        // given
        Delivery delivery = delivery();
        delivery.setEstimatedDeliveryAt(null);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.setSource(DeliveryTermsForm.COMMENT);
        form.setDeliveryId(delivery.getDeliveryId());
        form.setComment("Kierowca dzwoni");

        // when
        String view = controller.updateDelivery(form, "fetch", request, response, new ConcurrentModel(), redirectAttributes, PL);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesManager).updateDelivery(saved.capture());
        assertThat(view).isEqualTo("deliveries/details/dialogs :: commentForm");
        assertThat(saved.getValue().getComment()).isEqualTo("Kierowca dzwoni");
        assertThat(saved.getValue().getTax()).isEqualTo(1.23);
        assertThat(saved.getValue().getPaymentTerms()).isEqualTo(14);
        assertThat(saved.getValue().getEstimatedDeliveryAt()).isNull();
    }

    @Test
    void withoutJavaScriptErrorsRenderThePageWithTheDialogOpen() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = terms(delivery);
        form.setPaymentTerms("dużo");
        Model model = new ConcurrentModel();

        // when
        String view = controller.updateDelivery(form, null, request, response, model, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("deliveries/details");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(page(model).openDialog()).isEqualTo("terms");
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).form().getPaymentTerms()).isEqualTo("dużo");
    }

    @Test
    void withoutJavaScriptASaveRedirectsWithANoticeInThePage() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        String view = controller.updateDelivery(terms(delivery), null, request, response, new ConcurrentModel(), redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(redirectAttributes).addFlashAttribute(OrderFlash.ATTRIBUTE,
                new OrderNotice(OrderLabels.OK, "deliveries.details.terms.saved", null, null));
    }

    @Test
    void savingTermsOfAnotherStoresDeliveryIsNotFound() {
        // given
        Delivery foreign = delivery();
        when(deliveriesRepository.findById(STORE_ID, foreign.getDeliveryId())).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.updateDelivery(terms(foreign), "fetch", request, response, new ConcurrentModel(),
                redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(deliveriesManager, never()).updateDelivery(any());
    }

    @Test
    void theSuperAdminSavesOnTheStoreNamedInThePath() {
        // given
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(true);
        Delivery delivery = delivery();
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById("store-9", delivery.getDeliveryId())).thenReturn(delivery);

        // when
        String view = controller.updateDeliveryForSuperAdmin("store-9", terms(delivery), null, request, response,
                new ConcurrentModel(), redirectAttributes, PL);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesManager).updateDelivery(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo("store-9");
        assertThat(view).isEqualTo("redirect:/dashboard/store/store-9/deliveries/details?deliveryId=" + delivery.getDeliveryId());
    }

    @Test
    void aReceivedDeliveryRefusesTheSaveAndTheStoreAdminCannotChangeOneAwaitingApproval() {
        // given
        Delivery received = delivery();
        received.setReceivedAt(java.time.LocalDateTime.of(2026, 10, 2, 9, 0));
        Delivery awaiting = delivery();
        awaiting.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, received.getDeliveryId())).thenReturn(received);
        when(deliveriesRepository.findById(STORE_ID, awaiting.getDeliveryId())).thenReturn(awaiting);
        Model model = new ConcurrentModel();

        // when
        String async = controller.updateDelivery(terms(received), "fetch", request, response, model, redirectAttributes, PL);
        controller.updateDelivery(terms(awaiting), null, request, new MockHttpServletResponse(), new ConcurrentModel(), redirectAttributes, PL);

        // then
        assertThat(async).isEqualTo("deliveries/details/dialogs :: termsForm");
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).refusal()).isEqualTo("deliveries.details.terms.locked.received");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.edit.locked.awaitingApproval");
        verify(deliveriesManager, never()).updateDelivery(any());
    }

    @Test
    void deletingIsConfirmedOnItsOwnPageWithTheDeliveryNamed() {
        // given
        Delivery delivery = delivery();
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        Model model = new ConcurrentModel();

        // when
        String view = controller.confirmDeleteDelivery(delivery.getDeliveryId(), model, redirectAttributes, PL);

        // then
        ConfirmAction confirm = (ConfirmAction) model.getAttribute("confirm");
        assertThat(view).isEqualTo("settings-confirm");
        assertThat(confirm.destructive()).isTrue();
        assertThat(confirm.actionPath()).isEqualTo("/dashboard/deliveries/" + delivery.getDeliveryId() + "/confirm/delete");
        assertThat(confirm.cancelPath()).isEqualTo("/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
    }

    @Test
    void theDeletionPageIsNotOfferedWhileItemsRemain() {
        // given
        Delivery delivery = delivery();
        delivery.setAllocations(new ArrayList<>(List.of(new Allocation())));
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        String view = controller.confirmDeleteDelivery(delivery.getDeliveryId(), new ConcurrentModel(), redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.reason.removeItemsFirst");
    }

    @Test
    void unlinkingAnInvoiceIsConfirmedThenDoneWithANotice() {
        // given
        Delivery delivery = delivery();
        delivery.addDocument(new Document("inv-1", "FV/1", null, DocumentType.InvoiceVat));
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        Model model = new ConcurrentModel();

        // when
        String page = controller.confirmUnlinkInvoice(delivery.getDeliveryId(), "inv-1", model, redirectAttributes, PL);
        String done = controller.unlinkInvoiceConfirmed(delivery.getDeliveryId(), "inv-1", redirectAttributes, PL);

        // then
        assertThat(page).isEqualTo("settings-confirm");
        assertThat(((ConfirmAction) model.getAttribute("confirm")).destructive()).isFalse();
        verify(invoiceLinkingService).unlinkInvoice(STORE_ID, delivery.getDeliveryId(), "inv-1");
        verify(redirectAttributes).addFlashAttribute(OrderFlash.ATTRIBUTE,
                new OrderNotice(OrderLabels.OK, "deliveries.details.unlink.done", null, null));
        assertThat(done).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
    }

    @Test
    void savingACommentKeepsStoredAmountsTheFormWouldNotReadBack() {
        // given
        Delivery delivery = delivery();
        delivery.setShippingCost(12.345);
        delivery.setPaymentCost(0.005);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.setSource(DeliveryTermsForm.COMMENT);
        form.setDeliveryId(delivery.getDeliveryId());
        form.setComment("Rampa C");

        // when
        String view = controller.updateDelivery(form, "fetch", request, response, new ConcurrentModel(), redirectAttributes, PL);

        // then
        ArgumentCaptor<Delivery> saved = ArgumentCaptor.forClass(Delivery.class);
        verify(deliveriesManager).updateDelivery(saved.capture());
        assertThat(view).isEqualTo("deliveries/details/dialogs :: commentForm");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(saved.getValue().getShippingCost()).isEqualTo(12.345);
        assertThat(saved.getValue().getPaymentCost()).isEqualTo(0.005);
        assertThat(saved.getValue().getComment()).isEqualTo("Rampa C");
    }

    @Test
    void withoutJavaScriptARefusedTermsSaveKeepsTheStoredCommentForTheCommentDialog() {
        // given
        Delivery delivery = delivery();
        delivery.setComment("Rampa B");
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryTermsForm form = terms(delivery);
        form.setComment(null);
        form.setVat("abc");
        Model model = new ConcurrentModel();

        // when
        controller.updateDelivery(form, null, request, response, model, redirectAttributes, PL);

        // then
        assertThat(((TermsDialog) model.getAttribute("termsDialog")).form().getComment()).isEqualTo("Rampa B");
    }

    @Test
    void theDeletionPageOfAnotherStoresDeliveryIsNotFound() {
        // given
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, "foreign")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.confirmDeleteDelivery("foreign", new ConcurrentModel(), redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void theUnlinkPageOfAnotherStoresDeliveryIsNotFound() {
        // given
        when(deliveriesRepository.findById(STORE_ID, "foreign")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.confirmUnlinkInvoice("foreign", "inv-1", new ConcurrentModel(),
                redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(invoiceLinkingService, never()).unlinkInvoice(anyString(), anyString(), anyString());
    }

    @Test
    void deletingAMissingOrForeignDeliveryIsNotFoundAndDeletesNothing() {
        // given
        when(deliveriesRepository.findById(STORE_ID, "foreign")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.deleteDeliveryConfirmed("foreign", redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(deliveriesRepository, never()).delete(any(Delivery.class));
    }

    @Test
    void aConfirmationForAnotherStoresDeliveryIsNotFound() {
        // given
        when(deliveriesRepository.findById(STORE_ID, "foreign")).thenReturn(null);
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        Allocation posted = new Allocation();
        posted.setSelected(true);
        form.setAllocations(new ArrayList<>(List.of(posted)));

        // when / then
        assertThatThrownBy(() -> controller.confirmSelection("foreign", "split", form, new ConcurrentModel(),
                redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(deliveriesQueryService, never()).fetchDeliveryWithAllocations(anyString(), anyString());
    }

    @Test
    void unlinkingAnInvoiceTheDeliveryDoesNotHaveIsNotFound() {
        // given
        Delivery delivery = delivery();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when / then
        assertThatThrownBy(() -> controller.unlinkInvoiceConfirmed(delivery.getDeliveryId(), "inv-x", redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(invoiceLinkingService, never()).unlinkInvoice(anyString(), anyString(), anyString());
    }

    @Test
    void savingTermsWithoutADeliveryIdIsNotFound() {
        // given
        DeliveryTermsForm form = new DeliveryTermsForm();
        form.setDeliveryId(" ");

        // when / then
        assertThatThrownBy(() -> controller.updateDelivery(form, "fetch", request, response, new ConcurrentModel(),
                redirectAttributes, PL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(deliveriesRepository, never()).findById(any(), any());
        verify(deliveriesManager, never()).updateDelivery(any());
    }
}
