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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.DeliveriesManager;
import pl.commercelink.inventory.deliveries.DeliveriesQueryService;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.inventory.deliveries.DeliveryOrderedQtyUpdateService;
import pl.commercelink.inventory.deliveries.DeliveryReceptionService;
import pl.commercelink.inventory.deliveries.InvoiceLinkingService;
import pl.commercelink.inventory.deliveries.InvoiceSyncPreviewBuilder;
import pl.commercelink.inventory.deliveries.InvoiceSyncService;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveriesControllerDetailsFixesTest {

    private static final String STORE_ID = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private DeliveriesRepository deliveriesRepository;
    @Mock
    private DeliveriesQueryService deliveriesQueryService;
    @Mock
    private DeliveriesManager deliveriesManager;
    @Mock
    private DeliveryReceptionService deliveryReceptionService;
    @Mock
    private DeliveryOrderedQtyUpdateService deliveryOrderedQtyUpdateService;
    @Mock
    private InvoiceLinkingService invoiceLinkingService;
    @Mock
    private InvoiceSyncPreviewBuilder invoiceSyncPreviewBuilder;
    @Mock
    private InvoiceSyncService invoiceSynchronizationService;
    @Mock
    private MessageSource messageSource;
    @Mock
    private RedirectAttributes redirectAttributes;
    @InjectMocks
    private DeliveriesController controller;

    private MockedStatic<CustomSecurityContext> security;

    @BeforeEach
    void stubSecurityAndMessages() {
        security = mockStatic(CustomSecurityContext.class);
        security.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
        security.when(() -> CustomSecurityContext.hasRole("SUPER_ADMIN")).thenReturn(false);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void closeSecurity() {
        security.close();
    }

    private static Delivery warehouse() {
        return new Delivery(STORE_ID, "MH-1", "Manual-Hurt");
    }

    private static DeliveryAllocationsForm formFor(Delivery delivery, String storeIdInTheForm) {
        DeliveryAllocationsForm form = new DeliveryAllocationsForm();
        form.setStoreId(storeIdInTheForm);
        form.setProvider("Forged");
        form.setDeliveryId(delivery.getDeliveryId());
        return form;
    }

    @Test
    void receiveIgnoresTheStoreIdCarriedByTheForm() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveryReceptionService.receive(any(), any(), any(), any(), any(), any())).thenReturn(OperationResult.success(null));

        // when
        controller.markSelectedAllocationsAsReceived(formFor(delivery, "other-store"), redirectAttributes, PL);

        // then
        verify(deliveriesRepository, never()).findById(eq("other-store"), any());
        verify(deliveryReceptionService).receive(eq(STORE_ID), eq("Manual-Hurt"), eq(delivery.getDeliveryId()), any(), any(), any());
    }

    @Test
    void receiveOfAnotherStoresDeliveryIsNotFound() {
        // given
        Delivery foreign = warehouse();
        when(deliveriesRepository.findById(STORE_ID, foreign.getDeliveryId())).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> controller.markSelectedAllocationsAsReceived(formFor(foreign, "other-store"), redirectAttributes, PL))
                .isInstanceOf(ResponseStatusException.class);
        verify(deliveryReceptionService, never()).receive(any(), any(), any(), any(), any(), any());
    }

    @Test
    void receiveIsRefusedWhileTheOrderIsBeingPlaced() {
        // given
        Delivery delivery = warehouse();
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        String view = controller.markSelectedAllocationsAsReceived(formFor(delivery, STORE_ID), redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.edit.locked.orderPending");
        verify(deliveryReceptionService, never()).receive(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aReceptionFailureIsShownInTheOperatorsLanguage() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveryReceptionService.receive(any(), any(), any(), any(), any(), any()))
                .thenReturn(OperationResult.failure("deliveries.receive.error.warehouseConfig"));

        // when
        controller.markSelectedAllocationsAsReceived(formFor(delivery, STORE_ID), redirectAttributes, PL);

        // then
        verify(messageSource).getMessage("deliveries.receive.error.warehouseConfig", null,
                "deliveries.receive.error.warehouseConfig", PL);
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.receive.error.warehouseConfig");
    }

    @Test
    void splitWithoutANumberSplitsNothing() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryAllocationsForm form = formFor(delivery, STORE_ID);
        form.setTargetExternalDeliveryId("  ");

        // when
        controller.splitSelectedAllocations(form, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.split.error.number");
        verify(deliveriesManager, never()).splitAllocations(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSplitRefusedForItsPaymentSaysSoInTheOperatorsLanguage() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        DeliveryAllocationsForm form = formFor(delivery, STORE_ID);
        form.setTargetExternalDeliveryId("MH-1-B");
        form.setTargetEstimatedDeliveryAt(LocalDate.of(2026, 10, 8));
        doThrow(new IllegalArgumentException("Delivery with partial payment can't be split."))
                .when(deliveriesManager).splitAllocations(any(), any(), any(), any(), any(), any());

        // when
        controller.splitSelectedAllocations(form, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.split.error.payment");
    }

    @Test
    void mergeWithoutATargetSaysWhatToChoose() {
        // given
        Delivery delivery = warehouse();
        DeliveryAllocationsForm form = formFor(delivery, STORE_ID);

        // when
        controller.mergeSelectedAllocations(form, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.merge.error.target");
        verify(deliveriesManager, never()).reassignAllocations(any(), any(), any(), any(), any());
    }

    @Test
    void orderedQuantityIsNotChangedWhileTheOrderIsBeingPlaced() {
        // given
        Delivery delivery = warehouse();
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.updateDeliveryItemQty(delivery.getDeliveryId(), "MFN-1", 3, redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.edit.locked.orderPending");
        verify(deliveryOrderedQtyUpdateService, never()).run(any(), any(), any(), anyInt());
    }

    @Test
    void aDeliveryWithItemsIsNotDeleted() {
        // given
        Delivery delivery = warehouse();
        Delivery withItems = warehouse();
        withItems.setAllocations(List.of(new Allocation()));
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        when(deliveriesQueryService.fetchDeliveryWithAllocations(STORE_ID, delivery.getDeliveryId())).thenReturn(withItems);

        // when
        String view = controller.deleteDeliveryConfirmed(delivery.getDeliveryId(), redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.reason.removeItemsFirst");
        verify(deliveriesRepository, never()).delete(any(Delivery.class));
    }

    @Test
    void linkingByInvoiceIdGoesThroughTheSameEndpoint() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.linkInvoices(delivery.getDeliveryId(), "byId", " 4711 ", redirectAttributes, PL);

        // then
        verify(invoiceLinkingService).linkInvoiceById(STORE_ID, delivery.getDeliveryId(), "4711");
        verify(invoiceLinkingService, never()).linkInvoices(any(), any());
    }

    @Test
    void linkingByIdWithoutAnIdLinksNothing() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.linkInvoices(delivery.getDeliveryId(), "byId", "", redirectAttributes, PL);

        // then
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.invoice.error.id");
        verify(invoiceLinkingService, never()).linkInvoiceById(any(), any(), any());
    }

    @Test
    void linkingByTheSupplierNumberIsTheDefault() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);

        // when
        controller.linkInvoices(delivery.getDeliveryId(), null, null, redirectAttributes, PL);

        // then
        verify(invoiceLinkingService).linkInvoices(STORE_ID, delivery.getDeliveryId());
    }

    @Test
    void aPaymentAddedFromThePaymentsScreenReturnsThere() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        AddPaymentForm form = new AddPaymentForm();
        form.setBankAmount("120,00");
        form.setProcessingFee("");
        form.setSource(PaymentSource.BankTransfer);

        // when
        form.setReturnTo("/dashboard/payments");
        String fromPayments = controller.addPayment(delivery.getDeliveryId(), form, redirectAttributes, PL);
        form.setReturnTo(null);
        String fromDetails = controller.addPayment(delivery.getDeliveryId(), form, redirectAttributes, PL);

        // then
        assertThat(fromPayments).isEqualTo("redirect:/dashboard/payments");
        assertThat(fromDetails).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        assertThat(delivery.getPayments()).extracting(p -> p.getAmount()).containsOnly(120.0);
        verify(redirectAttributes, never()).addFlashAttribute(eq("errorMessage"), any());
    }

    @Test
    void anAppliedInvoiceSyncReturnsToTheDeliveryWithANotice() {
        // given
        Delivery delivery = warehouse();
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        InvoiceSyncPreview form = new InvoiceSyncPreview();
        form.setDeliveryId(delivery.getDeliveryId());

        // when
        String view = controller.applyInvoiceSync(form, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=" + delivery.getDeliveryId());
        verify(invoiceSynchronizationService).apply(STORE_ID, form);
        verify(redirectAttributes).addFlashAttribute(OrderFlash.ATTRIBUTE,
                new OrderNotice(OrderLabels.OK, "deliveries.details.invoice.synced", null, null));
    }

    @Test
    void anInvoiceSyncIsNotAppliedWhileTheDeliveryAwaitsApproval() {
        // given
        Delivery delivery = warehouse();
        delivery.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        when(deliveriesRepository.findById(STORE_ID, delivery.getDeliveryId())).thenReturn(delivery);
        InvoiceSyncPreview form = new InvoiceSyncPreview();
        form.setDeliveryId(delivery.getDeliveryId());

        // when
        controller.applyInvoiceSync(form, redirectAttributes, PL);

        // then
        verify(invoiceSynchronizationService, never()).apply(any(), any());
        verify(redirectAttributes, never()).addFlashAttribute(eq(OrderFlash.ATTRIBUTE), any());
    }

    @Test
    void anInvoiceWhosePreviewCannotBeBuiltSendsTheOperatorBackWithAnError() {
        // given
        when(invoiceSyncPreviewBuilder.build(STORE_ID, "d-1", "inv-9")).thenReturn(null);
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = controller.showInvoiceSyncPreview("d-1", "inv-9", model, redirectAttributes, PL);

        // then
        assertThat(view).isEqualTo("redirect:/dashboard/deliveries/details?deliveryId=d-1");
        verify(redirectAttributes).addFlashAttribute("errorMessage", "deliveries.details.invoice.preview.error");
        assertThat(model.containsAttribute("preview")).isFalse();
    }
}
