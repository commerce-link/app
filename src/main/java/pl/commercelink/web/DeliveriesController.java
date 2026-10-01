package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShipmentCarrierOptions;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.AmountEditor;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.dtos.RoutedOrderView;
import pl.commercelink.web.dtos.RoutedSupplierView;
import pl.commercelink.web.dtos.SupplierOrderChoicesParams;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;

import java.time.LocalDate;
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

    @PostMapping("/dashboard/deliveries/{deliveryId}/addPayment")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String addPayment(@PathVariable String deliveryId,
                             @ModelAttribute AddPaymentForm form,
                             @RequestParam(required = false, defaultValue = "false") boolean redirectToPayments,
                             RedirectAttributes redirectAttributes,
                             Locale locale) {
        Delivery delivery = deliveriesRepository.findById(getStoreId(), deliveryId);

        String redirectTarget = redirectToPayments
                ? "redirect:/dashboard/payments"
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;

        if (delivery != null && delivery.isAwaitingApproval()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.edit.locked.awaitingApproval", null, locale));
            return redirectTarget;
        }

        // a delivery keeps its sign as typed: a payout to the supplier is stored positive
        String invalid = form.validate();
        if (invalid != null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage(invalid, null, locale));
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
        return redirectTarget;
    }

    /**
     * The payments edit modal (fragments/payments-section.html) posts its amounts as text: read them like every other
     * payment amount (AmountParser), whatever the browser's language, instead of Double.valueOf, which refused "149,99".
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
        Delivery existingDelivery = deliveriesRepository.findById(getStoreId(), deliveryId);
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
        Delivery delivery = deliveriesRepository.findById(form.getStoreId(), form.getDeliveryId());
        if (delivery != null && delivery.isAwaitingApproval()) {
            return redirectEditLocked(form.getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        if (delivery != null && delivery.isDropship()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.receive.error.dropship", null, locale));
            return detailsRedirect(form.getStoreId(), form.getDeliveryId());
        }
        OperationResult<Document> result = deliveryReceptionService.receive(
                form.getStoreId(),
                form.getProvider(),
                form.getDeliveryId(),
                form.getSelectedOrderAllocations(),
                form.getSelectedWarehouseAllocations(),
                form.getRemainingAllocations()
        );

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
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
        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage(successKey, null, locale));
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
            redirectAttributes.addFlashAttribute("errorMessage", "Target delivery ID cannot be empty for merge operation.");
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

        deliveriesManager.reassignAllocations(
                storeId,
                form.getDeliveryId(),
                form.getTargetDeliveryId(),
                form.getSelectedOrderAllocations(),
                form.getSelectedWarehouseAllocations()
        );
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
            redirectAttributes.addFlashAttribute("errorMessage", "Target external delivery ID cannot be empty for split operation.");
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
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return detailsRedirect(storeId, form.getDeliveryId());
    }

    @PostMapping("/dashboard/deliveries/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDelivery(@RequestParam String deliveryId,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        return deleteDelivery(getStoreId(), deliveryId, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteDeliveryForSuperAdmin(@PathVariable("storeId") String storeId, @RequestParam String deliveryId,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        return deleteDelivery(storeId, deliveryId, redirectAttributes, locale);
    }

    private String deleteDelivery(String storeId, String deliveryId,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery != null && delivery.isAwaitingApproval()) {
            return redirectEditLocked(storeId, deliveryId, redirectAttributes, locale);
        }
        if (delivery != null && (delivery.isOrderPending() || delivery.isOrderDispatched())) {
            return redirectOrderingInProgress(storeId, deliveryId, redirectAttributes, locale);
        }
        deliveriesRepository.delete(delivery);
        return "redirect:/dashboard/deliveries";
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/approval")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showApprovalScreen(@PathVariable("storeId") String storeId,
                                     @PathVariable("deliveryId") String deliveryId,
                                     Model model, RedirectAttributes redirectAttributes) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null || !delivery.isAwaitingApproval()) {
            if (model.containsAttribute("errorMessage")) {
                redirectAttributes.addFlashAttribute("errorMessage", model.getAttribute("errorMessage"));
            }
            return storeDeliveryDetailsRedirect(storeId, deliveryId);
        }
        model.addAttribute("delivery", delivery);
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
        } else {
            addApprovalAddresses(storeId, delivery, model);
            addSuggestedAddress(storeId, model);
            optionsContext = SupplierOrderOptionsContext.warehouse();
        }
        model.addAttribute("routedOrders", routedOrdersOf(storeId, delivery, dropshipOrder));
        OrderOptionsModel.addOrderOptions(supplierPurchaseService, storeId, delivery.getProvider(),
                optionsContext, delivery.getSupplierOrderChoices(), model);
        model.addAttribute("supplierLabels", supplierLabels.forStoreId(storeId));
        return "deliveryApproval";
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
            case CONFIRMED -> redirectAttributes.addFlashAttribute("successMessage",
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
            redirectAttributes.addFlashAttribute("successMessage",
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
            redirectAttributes.addFlashAttribute("successMessage",
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
        return "fragments/approval-validation :: validationResult";
    }

    @GetMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public String showDeliveryDetails(@RequestParam String deliveryId, Model model,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        return showDeliveryDetails(getStoreId(), deliveryId, model, redirectAttributes, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showDeliveryDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @RequestParam String deliveryId,
                                                   Model model, RedirectAttributes redirectAttributes, Locale locale) {
        return showDeliveryDetails(storeId, deliveryId, model, redirectAttributes, locale);
    }

    private String showDeliveryDetails(String storeId, String deliveryId, Model model,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        var delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("deliveries.error.notFound", null, locale));
            return "redirect:/dashboard/deliveries";
        }
        var mergeTargetDeliveries = deliveriesRepository.findPendingDeliveriesByProvider(
                        storeId, delivery.getProvider(), deliveryId).stream()
                .filter(target -> target.getOrderStatus() == delivery.getOrderStatus())
                .toList();

        model.addAttribute("delivery", delivery);
        model.addAttribute("allocationsForm", new DeliveryAllocationsForm(
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(), delivery.getAllocations()));
        model.addAttribute("mergeTargetDeliveries", mergeTargetDeliveries);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());
        model.addAttribute("supplierRegistry", supplierRegistry);
        model.addAttribute("paymentSources", OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource));
        model.addAttribute("pendingPayment", delivery.getPendingPayment());
        if (delivery.isDropship()) {
            var dropshipOrder = resolveDropshipOrder(storeId, delivery);
            Store store = storesRepository.findById(storeId);
            model.addAttribute("dropshipContact", dropshipOrder != null ? dropshipOrder.getShippingDetails() : null);
            model.addAttribute("dropshipShipment", dropshipOrder != null
                    ? dropshipOrder.firstShipment().orElse(null)
                    : null);
            model.addAttribute("shipmentTypes", List.of(ShipmentType.Courier, ShipmentType.PickupPoint));
            model.addAttribute("carrierOptions", dropshipOrder != null && store != null
                    ? shipmentCarrierOptions.forOrder(dropshipOrder, store)
                    : List.<String>of());
        }
        if (delivery.isOrderFailed() || delivery.isOrderDispatched()) {
            model.addAttribute("suggestedEstimatedDeliveryAt", supplierPurchaseService.suggestEstimatedDeliveryAt(delivery));
        }
        model.addAttribute("supplierLabels", supplierLabels.forStoreId(storeId));
        return "deliveryDetails";
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

    private List<RoutedOrderView> routedOrdersOf(String storeId, Delivery delivery, Order dropshipOrder) {
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
        Store store = storesRepository.findById(storeId);
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
            model.addAttribute("approvalAddressOptions", addresses.stream()
                    .map(address -> new PickerOption(address.id(), address.label()))
                    .toList());
        } catch (Exception e) {
            model.addAttribute("approvalAddresses", List.of());
            model.addAttribute("approvalAddressOptions", List.of());
            model.addAttribute("approvalAddressError", e.getMessage());
        }
    }

    private void addSuggestedAddress(String storeId, Model model) {
        Store store = storesRepository.findById(storeId);
        ShippingDetails storeDefault = store == null ? null : store.getDefaultShippingDetails();
        model.addAttribute("suggestedAddress", storeDefault);

        @SuppressWarnings("unchecked")
        List<SupplierDeliveryAddress> addresses =
                (List<SupplierDeliveryAddress>) model.getAttribute("approvalAddresses");
        model.addAttribute("suggestedAddressId",
                SuggestedDeliveryAddress.match(storeDefault, addresses).orElse(null));
    }

    @PostMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public String updateDelivery(@ModelAttribute Delivery updatedDelivery,
                                 RedirectAttributes redirectAttributes, Locale locale) {
        if (!isSuperAdmin() && isEditLocked(updatedDelivery.getStoreId(), updatedDelivery.getDeliveryId())) {
            return redirectEditLocked(updatedDelivery.getStoreId(), updatedDelivery.getDeliveryId(), redirectAttributes, locale);
        }
        deliveriesManager.updateDelivery(updatedDelivery);
        return detailsRedirect(updatedDelivery.getStoreId(), updatedDelivery.getDeliveryId());
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
                               RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), deliveryId)) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        invoiceLinkingService.linkInvoices(getStoreId(), deliveryId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/link-invoice-by-id")
    @PreAuthorize("hasRole('ADMIN')")
    public String linkInvoiceById(@RequestParam String deliveryId, @RequestParam String invoiceId,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), deliveryId)) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        invoiceLinkingService.linkInvoiceById(getStoreId(), deliveryId, invoiceId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/unlink-invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public String unlinkInvoice(@RequestParam String deliveryId, @RequestParam String invoiceId,
                                RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), deliveryId)) {
            return redirectEditLocked(getStoreId(), deliveryId, redirectAttributes, locale);
        }
        invoiceLinkingService.unlinkInvoice(getStoreId(), deliveryId, invoiceId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @GetMapping("/dashboard/deliveries/sync/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public String showInvoiceSyncPreview(@RequestParam String deliveryId, @RequestParam String invoiceId, Model model, RedirectAttributes redirectAttributes) {
        InvoiceSyncPreview preview = invoiceSyncPreviewBuilder.build(getStoreId(), deliveryId, invoiceId);

        if (preview == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nie udalo sie pobrac danych faktury.");
            return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
        }

        model.addAttribute("preview", preview);
        return "invoiceSyncPreview";
    }

    @PostMapping("/dashboard/deliveries/syncPaymentStatuses")
    @PreAuthorize("hasRole('ADMIN')")
    public String syncPaymentStatuses() {
        invoiceSynchronizationService.sync(getStoreId());
        return "redirect:/dashboard/payments";
    }

    @PostMapping("/dashboard/deliveries/sync/apply")
    @PreAuthorize("hasRole('ADMIN')")
    public String applyInvoiceSync(@ModelAttribute InvoiceSyncPreview form,
                                   RedirectAttributes redirectAttributes, Locale locale) {
        if (isEditLocked(getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
        }
        invoiceSynchronizationService.apply(getStoreId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "Synchronizacja zakonczona pomyslnie.");
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    private String detailsRedirect(String storeId, String deliveryId) {
        return isSuperAdmin()
                ? storeDeliveryDetailsRedirect(storeId, deliveryId)
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
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
