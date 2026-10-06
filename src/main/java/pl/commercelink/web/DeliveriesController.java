package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.web.deliveries.approval.ApprovalPage;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.AmountEditor;
import pl.commercelink.web.orders.OrderFlash;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderNotice;
import pl.commercelink.web.deliveries.details.DeliveryConfirmPages;
import pl.commercelink.web.deliveries.details.DeliveryLinks;
import pl.commercelink.web.deliveries.details.DeliveryPageData;
import pl.commercelink.web.deliveries.details.DeliveryPageModel;
import pl.commercelink.web.deliveries.details.DeliveryPageModelFactory;
import pl.commercelink.web.deliveries.details.DeliveryRules;
import pl.commercelink.web.deliveries.details.DeliveryViewer;
import pl.commercelink.web.deliveries.details.TermsDialog;
import pl.commercelink.web.dtos.DeliveryTermsForm;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.payments.PaymentsQuery;
import pl.commercelink.web.payments.PaymentsReturn;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.web.dtos.RoutedOrderView;
import pl.commercelink.web.dtos.RoutedSupplierView;
import pl.commercelink.web.dtos.SupplierOrderChoicesParams;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;

import static pl.commercelink.starter.security.CustomSecurityContext.getStoreId;

@Controller
public class DeliveriesController {

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private DeliveriesRepository deliveriesRepository;

    @Autowired
    private DeliveriesManager deliveriesManager;

    @Autowired
    private DeliveriesQueryService deliveriesQueryService;

    @Autowired
    private DeliveryOrderedQtyUpdateService deliveryOrderedQtyUpdateService;

    @Autowired
    private DeliveryReceptionService deliveryReceptionService;

    @Autowired
    private OrdersManager ordersManager;

    @Autowired
    private InvoiceLinkingService invoiceLinkingService;

    @Autowired
    private InvoiceSyncPreviewBuilder invoiceSyncPreviewBuilder;

    @Autowired
    private InvoiceSyncService invoiceSynchronizationService;

    @Autowired
    private SupplierRegistry supplierRegistry;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SupplierPurchaseService supplierPurchaseService;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrderIdRefreshService orderIdRefreshService;

    @Autowired
    private DropshipOrderLocator dropshipOrderLocator;

    @Autowired
    private DropshipDeliveryCompletion dropshipDeliveryCompletion;

    @Autowired
    private ShipmentCarrierOptions shipmentCarrierOptions;

    @Autowired
    private DropshipTrackingService dropshipTrackingService;

    @Autowired
    private SupplierLabels supplierLabels;

    /** A delivery of the session's store; another store's id (or a stale one) is a 404, not an NPE further down. */
    private Delivery requireDelivery(String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(getStoreId(), deliveryId);
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return delivery;
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String deliveryId,
                             @ModelAttribute AddPaymentForm form,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Delivery delivery = requireDelivery(deliveryId);

        Optional<String> back = PaymentsReturn.target(form.getReturnTo());
        String redirectTarget = back.map(target -> "redirect:" + target)
                .orElse("redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId);
        // the Payments page shows its own outcome messages; the details page keeps the layout's banner
        String errorAttribute = back.isPresent() ? PaymentsReturn.ERROR : "errorMessage";

        if (delivery.isAwaitingApproval()) {
            redirectAttributes.addFlashAttribute(errorAttribute,
                    messageSource.getMessage("deliveries.edit.locked.awaitingApproval", null, locale));
            return redirectTarget;
        }

        // a delivery keeps its sign as typed: a payout to the supplier is stored positive
        String invalid = form.validate();
        if (invalid != null) {
            redirectAttributes.addFlashAttribute(errorAttribute, messageSource.getMessage(invalid, null, locale));
            return redirectTarget;
        }

        Payment target = delivery.getPayments().stream()
                .filter(Payment::isUnsettled)
                .findFirst()
                .orElseGet(() -> {
                    Payment p = new Payment();
                    delivery.addPayment(p);
                    return p;
                });

        target.setSource(form.getSource());
        target.setDirection(form.getDirection() != null ? form.getDirection() : PaymentDirection.Outgoing);
        target.setReferenceNo(form.getReferenceNo());
        target.setName(form.getName());
        target.setAmount(form.amount());
        target.setFee(form.fee());
        target.setBankTransactionNo(form.getBankTransactionNo());
        target.setBankTransactionDate(form.getBankTransactionDate());

        delivery.recomputePaid();
        deliveriesRepository.save(delivery);
        if (back.isPresent()) {
            redirectAttributes.addFlashAttribute(PaymentsReturn.NOTICE,
                    messageSource.getMessage(form.amount() < 0 ? "payments.notice.delivery.refund" : "payments.notice.delivery",
                            new Object[]{delivery.getShortenedDeliveryId()}, locale));
        }
        return redirectTarget;
    }

    /**
     * The payment dialogs of the delivery details (deliveries/details/payments.html) post their amounts as text: read
     * them like every other payment amount (AmountParser), whatever the browser's language, instead of Double.valueOf,
     * which refused "149,99".
     */
    @InitBinder("delivery")
    void paymentAmounts(WebDataBinder binder) {
        binder.registerCustomEditor(double.class, "payments.amount", new AmountEditor());
        binder.registerCustomEditor(double.class, "payments.fee", new AmountEditor());
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/updatePayments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updatePayments(@PathVariable String deliveryId, @ModelAttribute("delivery") Delivery updatedDelivery,
                                 BindingResult binding, RedirectAttributes redirectAttributes, Locale locale) {
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.amount.format", null, locale));
            return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
        }
        Delivery existingDelivery = requireDelivery(deliveryId);
        if (existingDelivery.isAwaitingApproval()) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        if (updatedDelivery.getPayments() != null) {
            List<Payment> payments = updatedDelivery.getPayments().stream()
                    .filter(Payment::isComplete)
                    .collect(Collectors.toList());

            existingDelivery.setPayments(payments);
            existingDelivery.recomputePaid();
        }
        deliveriesRepository.save(existingDelivery);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/markSelectedAsReceived")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String markSelectedAllocationsAsReceived(@ModelAttribute DeliveryAllocationsForm form,
                                                    RedirectAttributes redirectAttributes, Locale locale) {
        // the store comes from the session: the form's storeId would let a store reach another store's delivery
        String storeId = getStoreId();
        Delivery delivery = deliveriesRepository.findById(storeId, form.getDeliveryId());
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (delivery.isAwaitingApproval()) {
            return redirectEditLocked(storeId, form.getDeliveryId(), redirectAttributes, locale);
        }
        if (delivery.isOrderPending()) {
            return redirectOrderingInProgress(storeId, form.getDeliveryId(), redirectAttributes, locale);
        }
        if (delivery.isAwaitingSupplierConfirmation()) {
            // the items are claimed for the purchase: receiving now would mark the delivery received with nothing received
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.purchase.awaitingSupplier.locked", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }
        if (delivery.isDropship()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.receive.error.dropship", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }
        OperationResult<Document> result = deliveryReceptionService.receive(
                storeId,
                delivery.getProvider(),
                form.getDeliveryId(),
                form.getSelectedOrderAllocations(),
                form.getSelectedWarehouseAllocations(),
                form.getRemainingAllocations()
        );

        if (!result.isSuccess()) {
            // the reception's own refusals are message keys; a warehouse handler may still answer with plain text
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, result.getMessage(), locale));
        } else if (result.hasPayload()) {
            return "redirect:/dashboard/warehouse-documents/details?documentId=" + result.getPayload().getId();
        }

        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/confirmDropshipShipment")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDropshipShipment(@ModelAttribute DeliveryAllocationsForm form,
                                          RedirectAttributes redirectAttributes, Locale locale) {
        return confirmDropshipShipment(getStoreId(), form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/confirmDropshipShipment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmDropshipShipmentForSuperAdmin(@PathVariable("storeId") String storeId,
                                                       @ModelAttribute DeliveryAllocationsForm form,
                                                       RedirectAttributes redirectAttributes, Locale locale) {
        return confirmDropshipShipment(storeId, form, redirectAttributes, locale);
    }

    private String confirmDropshipShipment(String storeId, DeliveryAllocationsForm form,
                                           RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(storeId, form.getDeliveryId());
        if (delivery == null || !delivery.isDropship()) {
            return flashError("deliveries.dropship.shipment.error.notDropship", storeId, form, redirectAttributes, locale);
        }
        if (delivery.getOrderStatus() != null || delivery.hasBeenReceived()) {
            return flashError("deliveries.dropship.confirm.unavailable", storeId, form, redirectAttributes, locale);
        }
        List<Allocation> selected = form.getSelectedOrderAllocations();
        if (selected.isEmpty()) {
            return flashError("deliveries.select.at.least.one", storeId, form, redirectAttributes, locale);
        }
        DropshipShipment shipment = form.toDropshipShipment();
        String validationError = shipment.validationError();
        if (validationError != null) {
            return flashError(validationError, storeId, form, redirectAttributes, locale);
        }
        OperationResult<DropshipShipmentResult> result = dropshipDeliveryCompletion.confirmShipped(
                storeId, delivery, selected, form.getRemainingAllocations(), shipment);
        if (!result.isSuccess()) {
            return flashError(result.getMessage(), storeId, form, redirectAttributes, locale);
        }
        String successKey = result.getPayload() == DropshipShipmentResult.COMPLETED
                ? "deliveries.dropship.shipment.success"
                : "deliveries.dropship.shipment.success.partial";
        OrderFlash.saved(redirectAttributes, messageSource.getMessage(successKey, null, locale));
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    private String flashError(String messageKey, String storeId, DeliveryAllocationsForm form,
                              RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(messageKey, null, locale));
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    @PostMapping("/dashboard/deliveries/deleteSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form,
                                            RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        return deleteAllocations(getStoreId(), form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/deleteSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form,
                                                         RedirectAttributes redirectAttributes, Locale locale) {
        return deleteAllocations(storeId, form, redirectAttributes, locale);
    }

    private String deleteAllocations(String storeId, DeliveryAllocationsForm form,
                                     RedirectAttributes redirectAttributes, Locale locale) {
        if (isPurchaseInFlight(storeId, form.getDeliveryId())) {
            return redirectOrderingInProgress(storeId, form.getDeliveryId(), redirectAttributes, locale);
        }
        deliveriesManager.deleteAllocations(storeId, form.getDeliveryId(), form.getSelectedAllocations());
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    @PostMapping("/dashboard/deliveries/mergeSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String mergeSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form,
                                           RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        return mergeAllocations(getStoreId(), form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/mergeSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String mergeSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form,
                                                        RedirectAttributes redirectAttributes, Locale locale) {
        return mergeAllocations(storeId, form, redirectAttributes, locale);
    }

    private String mergeAllocations(String storeId, DeliveryAllocationsForm form,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        if (StringUtils.isBlank(form.getTargetDeliveryId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.merge.error.target", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }

        Delivery source = deliveriesRepository.findById(storeId, form.getDeliveryId());
        Delivery target = deliveriesRepository.findById(storeId, form.getTargetDeliveryId());
        if (source == null || target == null || source.getOrderStatus() != target.getOrderStatus()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.merge.error.statusMismatch", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }
        if (source.isDropship() || target.isDropship()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.merge.error.dropship", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }
        if (source.isOrderPending() || source.isOrderDispatched()) {
            return redirectOrderingInProgress(storeId, form.getDeliveryId(), redirectAttributes, locale);
        }

        try {
            deliveriesManager.reassignAllocations(
                    storeId,
                    form.getDeliveryId(),
                    form.getTargetDeliveryId(),
                    form.getSelectedOrderAllocations(),
                    form.getSelectedWarehouseAllocations()
            );
        } catch (IllegalArgumentException e) {
            // like a split, a move is refused only by the payment rule (Delivery.validateSplittablePayment), before any write
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.merge.error.payment", null, locale));
        }
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    @PostMapping("/dashboard/deliveries/splitSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String splitSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form,
                                           RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        return splitAllocations(getStoreId(), form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/splitSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String splitSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form,
                                                        RedirectAttributes redirectAttributes, Locale locale) {
        return splitAllocations(storeId, form, redirectAttributes, locale);
    }

    private String splitAllocations(String storeId, DeliveryAllocationsForm form,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(storeId, form.getDeliveryId());
        if (delivery != null && delivery.isDropship()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.merge.error.dropship", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }
        if (isOrderingInProgress(storeId, form.getDeliveryId())) {
            return redirectOrderingInProgress(storeId, form.getDeliveryId(), redirectAttributes, locale);
        }
        if (StringUtils.isBlank(form.getTargetExternalDeliveryId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.split.error.number", null, locale));
            return detailsRedirect(storeId, form.getDeliveryId());
        }

        try {
            deliveriesManager.splitAllocations(
                    storeId,
                    form.getDeliveryId(),
                    form.getTargetExternalDeliveryId(),
                    form.getTargetEstimatedDeliveryAt(),
                    form.getSelectedOrderAllocations(),
                    form.getSelectedWarehouseAllocations()
            );
        } catch (IllegalArgumentException e) {
            // the only refusal of a split is the payment rule (Delivery.validateSplittablePayment)
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.split.error.payment", null, locale));
        }
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    private String deleteDelivery(String storeId, String deliveryId,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (delivery.isAwaitingApproval()) {
            return redirectEditLocked(storeId, deliveryId, redirectAttributes, locale);
        }
        if (delivery.isOrderPending() || delivery.isOrderDispatched()) {
            return redirectOrderingInProgress(storeId, deliveryId, redirectAttributes, locale);
        }
        Delivery withAllocations = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (withAllocations != null && !withAllocations.getAllocations().isEmpty()) {
            // removing a delivery that still holds items would orphan their order and warehouse lines
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.reason.removeItemsFirst", null, locale));
            return detailsRedirect(storeId, deliveryId);
        }
        deliveriesRepository.delete(delivery);
        return "redirect:/dashboard/deliveries";
    }

    private static final Map<String, String> CONFIRM_DIALOGS = Map.of("receive", "receive",
            "remove-allocations", "remove", "merge", "merge", "split", "split", "ship", "ship");

    @PostMapping("/dashboard/deliveries/{deliveryId}/confirm/{action}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public String confirmSelection(@PathVariable String deliveryId, @PathVariable String action,
                                   @ModelAttribute DeliveryAllocationsForm form, Model model,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        return confirmSelection(getStoreId(), deliveryId, action, form, model, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/confirm/{action}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmSelectionForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable String deliveryId,
                                                @PathVariable String action, @ModelAttribute DeliveryAllocationsForm form,
                                                Model model, RedirectAttributes redirectAttributes, Locale locale) {
        return confirmSelection(storeId, deliveryId, action, form, model, redirectAttributes, locale);
    }

    /** The selection row without JavaScript: the same page, the checked destinations kept, the action's dialog open. */
    private String confirmSelection(String storeId, String deliveryId, String action, DeliveryAllocationsForm form,
                                    Model model, RedirectAttributes redirectAttributes, Locale locale) {
        String dialog = CONFIRM_DIALOGS.get(action);
        if (dialog == null || deliveriesRepository.findById(storeId, deliveryId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Set<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < form.getAllocations().size(); i++) {
            Allocation allocation = form.getAllocations().get(i);
            if (allocation != null && allocation.isSelected()) {
                selected.add(i);
            }
        }
        if (selected.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.select.at.least.one", null, locale));
            return detailsRedirect(storeId, deliveryId);
        }
        return showDeliveryDetails(storeId, deliveryId, null, dialog, null, selected, model, redirectAttributes, locale);
    }

    @GetMapping("/dashboard/deliveries/{deliveryId}/confirm/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDeleteDelivery(@PathVariable String deliveryId, Model model,
                                        RedirectAttributes redirectAttributes, Locale locale) {
        return confirmDelete(getStoreId(), deliveryId, model, redirectAttributes, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/confirm/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmDeleteDeliveryForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable String deliveryId,
                                                     Model model, RedirectAttributes redirectAttributes, Locale locale) {
        return confirmDelete(storeId, deliveryId, model, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/confirm/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDeliveryConfirmed(@PathVariable String deliveryId, RedirectAttributes redirectAttributes, Locale locale) {
        return deleteDelivery(getStoreId(), deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/confirm/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteDeliveryConfirmedForSuperAdmin(@PathVariable("storeId") String storeId, @PathVariable String deliveryId,
                                                       RedirectAttributes redirectAttributes, Locale locale) {
        return deleteDelivery(storeId, deliveryId, redirectAttributes, locale);
    }

    /** The same refusal as deleting: the page must not offer a confirmation the POST would refuse. */
    private String confirmDelete(String storeId, String deliveryId, Model model, RedirectAttributes redirectAttributes,
                                 Locale locale) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String reason = DeliveryRules.deleteReasonKey(delivery);
        if (reason != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(reason, null, locale));
            return detailsRedirect(storeId, deliveryId);
        }
        return DeliveryConfirmPages.delete(model, delivery, supplierLabels.forStoreId(storeId).of(delivery.getProvider()),
                DeliveryLinks.of(isSuperAdmin(), storeId, deliveryId), messageSource, locale);
    }

    @GetMapping("/dashboard/deliveries/{deliveryId}/confirm/unlink-invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmUnlinkInvoice(@PathVariable String deliveryId, @RequestParam String invoiceId, Model model,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(getStoreId(), deliveryId);
        Document invoice = linkedInvoiceOrNotFound(delivery, invoiceId);
        if (delivery.isAwaitingApproval()) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        return DeliveryConfirmPages.unlinkInvoice(model, delivery, invoice, DeliveryLinks.of(false, getStoreId(), deliveryId),
                messageSource, locale);
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/confirm/unlink-invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public String unlinkInvoiceConfirmed(@PathVariable String deliveryId, @RequestParam String invoiceId,
                                         RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(getStoreId(), deliveryId);
        Document invoice = linkedInvoiceOrNotFound(delivery, invoiceId);
        if (delivery.isAwaitingApproval()) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        invoiceLinkingService.unlinkInvoice(getStoreId(), deliveryId, invoiceId);
        OrderFlash.saved(redirectAttributes,
                messageSource.getMessage("deliveries.details.unlink.done", new Object[]{invoice.getNumber()}, locale));
        return detailsRedirect(getStoreId(), deliveryId);
    }

    /**
     * The page offers unlinking only for a VAT invoice; unlinking removes whatever document matches the id, so a forged
     * id of a goods receipt (PZ) would otherwise strip it from the delivery.
     */
    private static Document linkedInvoiceOrNotFound(Delivery delivery, String invoiceId) {
        Document invoice = delivery == null ? null : delivery.findDocumentById(invoiceId);
        if (invoice == null || invoice.getType() != DocumentType.InvoiceVat) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return invoice;
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/approval")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showApprovalScreen(@PathVariable("storeId") String storeId,
                                     @PathVariable("deliveryId") String deliveryId,
                                     @RequestParam(value = "open", required = false) String open,
                                     Model model, RedirectAttributes redirectAttributes) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null || !delivery.isAwaitingApproval()) {
            if (model.containsAttribute("errorMessage")) {
                redirectAttributes.addFlashAttribute("errorMessage", model.getAttribute("errorMessage"));
            }
            return storeDeliveryDetailsRedirect(storeId, deliveryId);
        }
        model.addAttribute("delivery", delivery);
        Store store = storesRepository.findById(storeId);
        SupplierOrderOptionsContext optionsContext;
        Order dropshipOrder = null;
        if (delivery.isDropship()) {
            dropshipOrder = resolveDropshipOrder(storeId, delivery);
            if (dropshipOrder != null && dropshipOrder.getShippingDetails() != null) {
                model.addAttribute("consignee", dropshipOrder.getShippingDetails());
            }
            model.addAttribute("pickupShipment",
                    dropshipOrder != null ? DropshipPurchaseService.pickupShipment(dropshipOrder).orElse(null) : null);
            optionsContext = dropshipOrder != null
                    ? DropshipPurchaseService.optionsContext(dropshipOrder)
                    : SupplierOrderOptionsContext.dropship(null);
            // approve() refuses a dropship whose order is gone, so the screen says why up front
            model.addAttribute("dropshipOrderMissing", dropshipOrder == null);
        } else {
            addApprovalAddresses(storeId, delivery, model);
            addSuggestedAddress(store, model);
            optionsContext = SupplierOrderOptionsContext.warehouse();
        }
        List<Order> requestOrders = requestOrdersOf(storeId, delivery, dropshipOrder);
        model.addAttribute("routedOrders", routedOrdersOf(store, requestOrders));
        OrderOptionsModel.addOrderOptions(supplierPurchaseService, storeId, delivery.getProvider(),
                optionsContext, delivery.getSupplierOrderChoices(), model);
        SupplierLabelMap labels = supplierLabels.forStoreId(storeId);
        model.addAttribute("supplierLabels", labels);
        model.addAttribute("page", ApprovalPage.of(delivery, store, requestOrders,
                labels.of(delivery.getProvider()),
                (ShippingDetails) model.getAttribute("consignee"), (Shipment) model.getAttribute("pickupShipment"),
                "reject".equals(open)));
        return "deliveries/approval";
    }

    private String storeDeliveryDetailsRedirect(String storeId, String deliveryId) {
        return String.format("redirect:/dashboard/store/%s/deliveries/details?deliveryId=%s", storeId, deliveryId);
    }

    private String approvalRedirectToScreen(String storeId, String deliveryId) {
        return String.format("redirect:/dashboard/store/%s/deliveries/%s/approval", storeId, deliveryId);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String approvePurchase(@PathVariable("storeId") String storeId,
                                  @PathVariable("deliveryId") String deliveryId,
                                  @RequestParam(value = "deliveryAddressId", required = false) String deliveryAddressId,
                                  @RequestParam Map<String, String> params,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.approve(storeId, deliveryId, deliveryAddressId,
                SupplierOrderChoicesParams.fromRequest(params));
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
            return approvalRedirectToScreen(storeId, deliveryId);
        }
        return storeDeliveryDetailsRedirect(storeId, deliveryId);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String rejectPurchase(@PathVariable("storeId") String storeId,
                                 @PathVariable("deliveryId") String deliveryId,
                                 @RequestParam(value = "reason", required = false) String reason,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.reject(storeId, deliveryId, reason);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
            return approvalRedirectToScreen(storeId, deliveryId);
        }
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("deliveries.approval.rejected.success", null, locale));
        return "redirect:/dashboard/deliveries";
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/refresh-order-id")
    @PreAuthorize("hasRole('ADMIN')")
    public String refreshOrderId(@PathVariable("deliveryId") String deliveryId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        return refreshOrderId(getStoreId(), deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/refresh-order-id")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String refreshOrderIdForSuperAdmin(@PathVariable("storeId") String storeId,
                                              @PathVariable("deliveryId") String deliveryId,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        return refreshOrderId(storeId, deliveryId, redirectAttributes, locale);
    }

    private String refreshOrderId(String storeId, String deliveryId,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        switch (orderIdRefreshService.refreshManually(storeId, deliveryId)) {
            case CONFIRMED -> OrderFlash.saved(redirectAttributes,
                    messageSource.getMessage("deliveries.orderId.refresh.confirmed", null, locale));
            case STILL_PENDING -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.orderId.refresh.stillPending", null, locale));
            case UNAVAILABLE -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.orderId.refresh.unavailable", null, locale));
        }
        return detailsRedirect(storeId, deliveryId);
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/tracking/check")
    @PreAuthorize("hasRole('ADMIN')")
    public String checkTracking(@PathVariable("deliveryId") String deliveryId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        return checkTracking(getStoreId(), deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/tracking/check")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String checkTrackingForSuperAdmin(@PathVariable("storeId") String storeId,
                                             @PathVariable("deliveryId") String deliveryId,
                                             RedirectAttributes redirectAttributes, Locale locale) {
        return checkTracking(storeId, deliveryId, redirectAttributes, locale);
    }

    private String checkTracking(String storeId, String deliveryId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        ManualTrackingOutcome outcome = dropshipTrackingService.checkManually(storeId, deliveryId);
        String key = switch (outcome) {
            case CONFIRMED -> "deliveries.dropship.tracking.result.confirmed";
            case STILL_PROCESSING -> "deliveries.dropship.tracking.result.stillProcessing";
            case CANCELLED -> "deliveries.dropship.tracking.result.cancelled";
            case NO_DATA -> "deliveries.dropship.tracking.result.noData";
            case UNAVAILABLE -> "deliveries.dropship.tracking.result.unavailable";
        };
        redirectAttributes.addFlashAttribute(outcome == ManualTrackingOutcome.CONFIRMED ? "successMessage" : "errorMessage",
                messageSource.getMessage(key, null, locale));
        return detailsRedirect(storeId, deliveryId);
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/purchase/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public String retryPurchase(@PathVariable("deliveryId") String deliveryId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        Optional<String> blocked = blockGlobalDeliveryForStoreAdmin(deliveryId,
                "deliveries.purchase.retry.error.global", redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return handleRetry(getStoreId(), deliveryId,
                "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId, redirectAttributes, locale);
    }

    private Optional<String> blockGlobalDeliveryForStoreAdmin(String deliveryId, String messageKey,
                                                               RedirectAttributes redirectAttributes, Locale locale) {
        Delivery delivery = deliveriesRepository.findById(getStoreId(), deliveryId);
        if (delivery != null && delivery.getConnectionMode() == ConnectionMode.GLOBAL) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(messageKey, null, locale));
            return Optional.of("redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId);
        }
        return Optional.empty();
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/purchase/retry")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String retryPurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                             @PathVariable("deliveryId") String deliveryId,
                                             RedirectAttributes redirectAttributes, Locale locale) {
        return handleRetry(storeId, deliveryId, storeDeliveryDetailsRedirect(storeId, deliveryId), redirectAttributes, locale);
    }

    private String handleRetry(String storeId, String deliveryId, String redirect,
                               RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.retry(storeId, deliveryId);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }
        return redirect;
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/purchase/reconcile")
    @PreAuthorize("hasRole('ADMIN')")
    public String reconcilePurchase(@PathVariable("deliveryId") String deliveryId,
                                    RedirectAttributes redirectAttributes, Locale locale) {
        Optional<String> blocked = blockGlobalDeliveryForStoreAdmin(deliveryId,
                "deliveries.purchase.retry.error.global", redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return handleReconcile(getStoreId(), deliveryId,
                "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/purchase/reconcile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String reconcilePurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                                 @PathVariable("deliveryId") String deliveryId,
                                                 RedirectAttributes redirectAttributes, Locale locale) {
        return handleReconcile(storeId, deliveryId, storeDeliveryDetailsRedirect(storeId, deliveryId), redirectAttributes, locale);
    }

    private String handleReconcile(String storeId, String deliveryId, String redirect,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.reconcile(storeId, deliveryId);
        if (result.isSuccess()) {
            OrderFlash.saved(redirectAttributes,
                    messageSource.getMessage("deliveries.purchase.reconcile.found", null, locale));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }
        return redirect;
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/purchase/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public String completePurchase(@PathVariable("deliveryId") String deliveryId,
                                   @RequestParam("externalOrderId") String externalOrderId,
                                   @RequestParam("estimatedDeliveryAt")
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimatedDeliveryAt,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        Optional<String> blocked = blockGlobalDeliveryForStoreAdmin(deliveryId,
                "deliveries.purchase.complete.error.global", redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return handleComplete(getStoreId(), deliveryId, externalOrderId, estimatedDeliveryAt,
                "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/purchase/complete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String completePurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                                @PathVariable("deliveryId") String deliveryId,
                                                @RequestParam("externalOrderId") String externalOrderId,
                                                @RequestParam("estimatedDeliveryAt")
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate estimatedDeliveryAt,
                                                RedirectAttributes redirectAttributes, Locale locale) {
        return handleComplete(storeId, deliveryId, externalOrderId, estimatedDeliveryAt,
                storeDeliveryDetailsRedirect(storeId, deliveryId), redirectAttributes, locale);
    }

    private String handleComplete(String storeId, String deliveryId, String externalOrderId,
                                  LocalDate estimatedDeliveryAt, String redirect,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.completeManually(
                storeId, deliveryId, externalOrderId, estimatedDeliveryAt);
        if (result.isSuccess()) {
            OrderFlash.saved(redirectAttributes,
                    messageSource.getMessage("deliveries.purchase.complete.success", null, locale));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }
        return redirect;
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/purchase/force")
    @PreAuthorize("hasRole('ADMIN')")
    public String forcePurchase(@PathVariable("deliveryId") String deliveryId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        Optional<String> blocked = blockGlobalDeliveryForStoreAdmin(deliveryId,
                "deliveries.purchase.retry.error.global", redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        return handleForce(getStoreId(), deliveryId,
                "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/purchase/force")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String forcePurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                             @PathVariable("deliveryId") String deliveryId,
                                             RedirectAttributes redirectAttributes, Locale locale) {
        return handleForce(storeId, deliveryId, storeDeliveryDetailsRedirect(storeId, deliveryId), redirectAttributes, locale);
    }

    private String handleForce(String storeId, String deliveryId, String redirect,
                               RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.forceRetry(storeId, deliveryId);
        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }
        return redirect;
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/approval/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String validatePendingApproval(@PathVariable("storeId") String storeId,
                                          @PathVariable("deliveryId") String deliveryId,
                                          Model model, Locale locale) {
        try {
            model.addAttribute("validation", supplierPurchaseService.validatePending(storeId, deliveryId));
        } catch (Exception e) {
            model.addAttribute("validationError",
                    messageSource.getMessage("deliveries.purchase.confirm.checkFailed", null, locale)
                            + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
        model.addAttribute("validationMode", "approval");
        return "deliveries/create/purchase :: validationResult";
    }

    static final String DETAILS_VIEW = "deliveries/details";

    @GetMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public String showDeliveryDetails(@RequestParam String deliveryId,
                                      @RequestParam(required = false) String returnTo,
                                      @RequestParam(required = false) String open,
                                      @RequestParam(required = false) String mfn,
                                      Model model, RedirectAttributes redirectAttributes, Locale locale) {
        return showDeliveryDetails(getStoreId(), deliveryId, returnTo, open, mfn, Set.of(), model, redirectAttributes, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showDeliveryDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @RequestParam String deliveryId,
                                                   @RequestParam(required = false) String returnTo,
                                                   @RequestParam(required = false) String open,
                                                   @RequestParam(required = false) String mfn,
                                                   Model model, RedirectAttributes redirectAttributes, Locale locale) {
        return showDeliveryDetails(storeId, deliveryId, returnTo, open, mfn, Set.of(), model, redirectAttributes, locale);
    }

    private String showDeliveryDetails(String storeId, String deliveryId, String returnTo, String open, String mfn,
                                       Set<Integer> preselected, Model model, RedirectAttributes redirectAttributes,
                                       Locale locale) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.error.notFound", null, locale));
            return "redirect:/dashboard/deliveries";
        }
        DeliveryViewer viewer = new DeliveryViewer(isSuperAdmin(), isAdmin(), returnTo);
        DeliveryPageModel page = DeliveryPageModelFactory.build(pageData(storeId, delivery, preselected, open, mfn), viewer);
        model.addAttribute("page", page);
        if (!model.containsAttribute("termsDialog")) {
            model.addAttribute("termsDialog", TermsDialog.of(delivery, page.links()));
        }
        return DETAILS_VIEW;
    }

    private DeliveryPageData pageData(String storeId, Delivery delivery, Set<Integer> preselected, String open, String mfn) {
        // a dropship delivery is never moved, so the partition scan for merge targets is skipped for it (side task 11);
        // the merge itself refuses a target in another order status, so the page does not offer one
        List<Delivery> mergeTargets = delivery.isDropship() ? List.of()
                : deliveriesRepository.findPendingDeliveriesByProvider(storeId, delivery.getProvider(), delivery.getDeliveryId())
                .stream().filter(target -> target.getOrderStatus() == delivery.getOrderStatus()).toList();
        Order dropshipOrder = delivery.isDropship() ? resolveDropshipOrder(storeId, delivery) : null;
        Store store = dropshipOrder == null ? null : storesRepository.findById(storeId);
        List<String> carrierOptions = dropshipOrder != null && store != null
                ? shipmentCarrierOptions.forOrder(dropshipOrder, store) : List.of();
        LocalDate suggested = delivery.isOrderFailed() || delivery.isOrderDispatched()
                ? supplierPurchaseService.suggestEstimatedDeliveryAt(delivery) : null;
        String externalId = StringUtils.trimToNull(delivery.getExternalDeliveryId());
        String partnerSiteUrl = externalId == null ? null : supplierRegistry.getPartnerSiteUrl(delivery.getProvider(), externalId);
        return new DeliveryPageData(delivery, supplierLabels.forStoreId(storeId).of(delivery.getProvider()), partnerSiteUrl,
                mergeTargets, dropshipOrder, carrierOptions, suggested, preselected, open, mfn, LocalDateTime.now());
    }

    private Order resolveDropshipOrder(String storeId, Delivery delivery) {
        try {
            return dropshipOrderLocator.locate(delivery.getDeliveryId())
                    .map(orderId -> ordersRepository.findById(storeId, orderId))
                    .orElse(null);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private List<Order> requestOrdersOf(String storeId, Delivery delivery, Order dropshipOrder) {
        List<Order> orders;
        if (delivery.isDropship()) {
            orders = dropshipOrder != null ? List.of(dropshipOrder) : List.of();
        } else {
            orders = delivery.getAllocations().stream()
                    .map(Allocation::getKey)
                    .filter(Objects::nonNull)
                    .map(AllocationKey::getOrderId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(orderId -> ordersRepository.findById(storeId, orderId))
                    .filter(Objects::nonNull)
                    .toList();
        }
        return orders;
    }

    private List<RoutedOrderView> routedOrdersOf(Store store, List<Order> orders) {
        return orders.stream()
                .filter(Order::isBoundToExternalSupplier)
                .map(order -> new RoutedOrderView(
                        order.getShortenedOrderId(),
                        RoutedSupplierView.from(order, store)))
                .toList();
    }

    private void addApprovalAddresses(String storeId, Delivery delivery, Model model) {
        try {
            List<SupplierDeliveryAddress> addresses =
                    supplierPurchaseService.deliveryAddressesForDelivery(storeId, delivery.getDeliveryId());
            model.addAttribute("approvalAddresses", addresses);
        } catch (Exception e) {
            model.addAttribute("approvalAddresses", List.of());
            model.addAttribute("approvalAddressError", e.getMessage());
        }
    }

    private void addSuggestedAddress(Store store, Model model) {
        ShippingDetails storeDefault = store == null ? null : store.getDefaultShippingDetails();
        model.addAttribute("suggestedAddress", storeDefault);

        @SuppressWarnings("unchecked")
        List<SupplierDeliveryAddress> addresses =
                (List<SupplierDeliveryAddress>) model.getAttribute("approvalAddresses");
        model.addAttribute("suggestedAddressId",
                SuggestedDeliveryAddress.match(storeDefault, addresses).orElse(null));
    }

    @PostMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateDelivery(@ModelAttribute DeliveryTermsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 HttpServletRequest request, HttpServletResponse response, Model model,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        return saveTerms(getStoreId(), form, SettingsPaths.isAsync(requestedWith), request, response, model,
                redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String updateDeliveryForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryTermsForm form,
                                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                              HttpServletRequest request, HttpServletResponse response, Model model,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        return saveTerms(storeId, form, SettingsPaths.isAsync(requestedWith), request, response, model,
                redirectAttributes, locale);
    }

    /**
     * The store comes from the session or the path, never from the form (spec §12.1 p. 1). The terms dialog saves the
     * dates and costs and keeps the stored comment; the comment dialog saves the comment and keeps the stored terms, so
     * neither overwrites what the other changed in between.
     */
    private String saveTerms(String storeId, DeliveryTermsForm form, boolean async, HttpServletRequest request,
                             HttpServletResponse response, Model model, RedirectAttributes redirectAttributes, Locale locale) {
        Delivery existing = StringUtils.isBlank(form.getDeliveryId()) ? null
                : deliveriesRepository.findById(storeId, form.getDeliveryId());
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        DeliveryLinks links = DeliveryLinks.of(isSuperAdmin(), storeId, existing.getDeliveryId());
        TermsDialog dialog = TermsDialog.of(existing, links);
        String fragment = "deliveries/details/dialogs :: " + (form.isComment() ? "commentForm" : "termsForm");
        String refusal = existing.hasBeenReceived() ? "deliveries.details.terms.locked.received"
                : existing.isAwaitingApproval() && !isSuperAdmin() ? "deliveries.edit.locked.awaitingApproval" : null;
        if (refusal != null) {
            String text = messageSource.getMessage(refusal, null, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                model.addAttribute("termsDialog", dialog.refused(form, text));
                return fragment;
            }
            redirectAttributes.addFlashAttribute("errorMessage", text);
            return detailsRedirect(storeId, existing.getDeliveryId());
        }
        Delivery toSave;
        if (form.isComment()) {
            // the stored numbers go back as they are: re-reading them from text could change or refuse a value the
            // operator never touched in this dialog
            toSave = withComment(storeId, existing, StringUtils.isBlank(form.getComment()) ? null : form.getComment());
        } else {
            // set before validating, so a page rendered with the refused terms still shows the stored comment in the
            // comment dialog, which reads the same form
            form.setComment(existing.getComment());
            Map<String, String> errors = form.validate(!existing.isDropship());
            if (!errors.isEmpty()) {
                model.addAttribute("termsDialog", dialog.withErrors(form, errors));
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return async ? fragment
                        : showDeliveryDetails(storeId, existing.getDeliveryId(), null, "terms", null, Set.of(), model,
                        redirectAttributes, locale);
            }
            toSave = form.toDelivery(storeId);
        }
        deliveriesManager.updateDelivery(toSave);
        String saved = messageSource.getMessage(form.isComment()
                ? "deliveries.details.comment.saved" : "deliveries.details.terms.saved", null, locale);
        if (async) {
            // the dialog closes and the page reloads; the notice waits for that page
            OrderFlash.forNextPage(request, response, links.details(), new OrderNotice(OrderLabels.OK, saved, null, null));
            model.addAttribute("termsDialog", dialog.saved(DeliveryTermsForm.of(toSave), saved));
            return fragment;
        }
        OrderFlash.saved(redirectAttributes, saved);
        return detailsRedirect(storeId, existing.getDeliveryId());
    }

    private static Delivery withComment(String storeId, Delivery existing, String comment) {
        Delivery delivery = new Delivery();
        delivery.setStoreId(storeId);
        delivery.setDeliveryId(existing.getDeliveryId());
        delivery.setEstimatedDeliveryAt(existing.getEstimatedDeliveryAt());
        delivery.setPaymentTerms(existing.getPaymentTerms());
        delivery.setShippingCost(existing.getShippingCost());
        delivery.setPaymentCost(existing.getPaymentCost());
        delivery.setTax(existing.getTax());
        delivery.setComment(comment);
        return delivery;
    }

    @PostMapping("/dashboard/deliveries/updateItemQty")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateDeliveryItemQty(
            @RequestParam String deliveryId,
            @RequestParam String mfn,
            @RequestParam int qty,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        if (isEditLocked(getStoreId(), deliveryId)) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        if (isOrderPending(getStoreId(), deliveryId)) {
            return redirectOrderingInProgress(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        return updateItemQty(getStoreId(), deliveryId, mfn, qty, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/updateItemQty")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String updateDeliveryItemQtyForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @RequestParam String deliveryId,
            @RequestParam String mfn,
            @RequestParam int qty,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        if (isOrderPending(storeId, deliveryId)) {
            return redirectOrderingInProgress(storeId, deliveryId, redirectAttributes, locale);
        }
        return updateItemQty(storeId, deliveryId, mfn, qty, redirectAttributes, locale);
    }

    private String updateItemQty(String storeId, String deliveryId, String mfn, int qty, RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<Void> result = deliveryOrderedQtyUpdateService.run(storeId, deliveryId, mfn, qty);

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }

        return detailsRedirect(storeId, deliveryId);
    }

    @PostMapping("/dashboard/deliveries/link-invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public String linkInvoices(@RequestParam String deliveryId,
                               @RequestParam(required = false) String linkMode,
                               @RequestParam(required = false) String invoiceId,
                               RedirectAttributes redirectAttributes, Locale locale) {
        if (existingDelivery(getStoreId(), deliveryId).isAwaitingApproval()) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        // one dialog, two ways: the chosen radio decides, so the form works without JavaScript too
        if ("byId".equals(linkMode)) {
            if (StringUtils.isBlank(invoiceId)) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("deliveries.details.invoice.error.id", null, locale));
                return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
            }
            invoiceLinkingService.linkInvoiceById(getStoreId(), deliveryId, invoiceId.trim());
        } else {
            invoiceLinkingService.linkInvoices(getStoreId(), deliveryId);
        }
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/link-invoice-by-id")
    @PreAuthorize("hasRole('ADMIN')")
    public String linkInvoiceById(@RequestParam String deliveryId, @RequestParam String invoiceId,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        if (existingDelivery(getStoreId(), deliveryId).isAwaitingApproval()) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        invoiceLinkingService.linkInvoiceById(getStoreId(), deliveryId, invoiceId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @GetMapping("/dashboard/deliveries/sync/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public String showInvoiceSyncPreview(@RequestParam String deliveryId, @RequestParam String invoiceId, Model model,
                                         RedirectAttributes redirectAttributes, Locale locale) {
        InvoiceSyncPreview preview = invoiceSyncPreviewBuilder.build(getStoreId(), deliveryId, invoiceId);

        if (preview == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.details.invoice.preview.error", null, locale));
            return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
        }

        model.addAttribute("preview", preview);
        return "invoiceSyncPreview";
    }

    @PostMapping("/dashboard/deliveries/syncPaymentStatuses")
    @PreAuthorize("hasRole('ADMIN')")
    public String syncPaymentStatuses(RedirectAttributes redirectAttributes, Locale locale) {
        InvoiceSyncResult result = invoiceSynchronizationService.sync(getStoreId());
        if (!result.configured()) {
            redirectAttributes.addFlashAttribute(PaymentsReturn.ERROR, messageSource.getMessage("payments.sync.notConfigured", null, locale));
            return "redirect:" + PaymentsQuery.PATH;
        }
        String message = result.checked() == 0
                ? messageSource.getMessage("payments.sync.result.none", null, locale)
                : messageSource.getMessage("payments.sync.result",
                        new Object[]{result.checked(), result.paidDeliveries().size(), result.unpaid()}, locale);
        if (!result.paidDeliveries().isEmpty()) {
            message += " " + messageSource.getMessage("payments.sync.result.paid", new Object[]{String.join(", ", result.paidDeliveries())}, locale);
        }
        if (!result.failedInvoices().isEmpty()) {
            redirectAttributes.addFlashAttribute(PaymentsReturn.ERROR,
                    messageSource.getMessage("payments.sync.result.failed", new Object[]{String.join(", ", result.failedInvoices())}, locale));
        }
        redirectAttributes.addFlashAttribute(PaymentsReturn.NOTICE, message);
        return "redirect:" + PaymentsQuery.PATH;
    }

    @PostMapping("/dashboard/deliveries/sync/apply")
    @PreAuthorize("hasRole('ADMIN')")
    public String applyInvoiceSync(@ModelAttribute InvoiceSyncPreview form,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        invoiceSynchronizationService.apply(getStoreId(), form);
        OrderFlash.saved(redirectAttributes, messageSource.getMessage("deliveries.details.invoice.synced", null, locale));
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    private String detailsRedirect(String storeId, String deliveryId) {
        return isSuperAdmin()
                ? storeDeliveryDetailsRedirect(storeId, deliveryId)
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    /** A missing or another store's delivery answers 404 before any write, like the other details routes. */
    private Delivery existingDelivery(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return delivery;
    }

    private boolean isEditLocked(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        return delivery != null && delivery.isAwaitingApproval();
    }

    private String redirectEditLocked(String storeId, String deliveryId,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage("deliveries.edit.locked.awaitingApproval", null, locale));
        return detailsRedirect(storeId, deliveryId);
    }

    private boolean isOrderingInProgress(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        return delivery != null && (delivery.isOrderPending() || delivery.isOrderDispatched());
    }

    private boolean isOrderPending(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        return delivery != null && delivery.isOrderPending();
    }

    // Removing allocations is the operator's way out of a purchase that ended badly, so it is blocked only
    // while the placement is genuinely in flight - not once the supplier left us with an unknown outcome.
    private boolean isPurchaseInFlight(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        return delivery != null
                && (delivery.isOrderPending() || (delivery.isOrderDispatched() && !delivery.isOrderOutcomeUnknown()));
    }

    private String redirectOrderingInProgress(String storeId, String deliveryId,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage("deliveries.edit.locked.orderPending", null, locale));
        return detailsRedirect(storeId, deliveryId);
    }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }

    private boolean isAdmin() { return CustomSecurityContext.hasRole("ADMIN"); }

}
