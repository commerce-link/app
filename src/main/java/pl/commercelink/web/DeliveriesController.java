package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.documents.Document;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.web.dtos.AddPaymentForm;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;
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
    private DeliveriesPlanningService deliveriesPlanningService;

    @Autowired
    private DeliveryCreationService deliveryCreationService;

    @Autowired
    private DeliveryFulfilmentUpdateService deliveryFulfilmentUpdateService;

    @Autowired
    private DeliveryOrderedQtyUpdateService deliveryOrderedQtyUpdateService;

    @Autowired
    private RestockSuggestionService restockSuggestionService;

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
    private DeliveryTaxResolver deliveryTaxResolver;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SupplierPurchaseService supplierPurchaseService;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrderIdRefreshService orderIdRefreshService;

    private static final int DELIVERY_PAGE_SIZE = 25;

    @GetMapping("/dashboard/deliveries")
    public String deliveries(
            @RequestParam(required = false) String deliveryId,
            @RequestParam(required = false) String externalDeliveryId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedAtEnd,
            @RequestParam(required = false, defaultValue = "false") boolean showArchived,
            @RequestParam(required = false, defaultValue = "false") boolean showWithoutInvoice,
            @RequestParam(required = false, defaultValue = "false") boolean showWithoutSync,
            @RequestParam(required = false, defaultValue = "false") boolean showAwaitingApproval,
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model) {
        DeliveryFilter deliveryFilter = new DeliveryFilter(deliveryId, externalDeliveryId, provider,
                orderedAtStart, orderedAtEnd, !showArchived, showWithoutInvoice, showWithoutSync,
                showAwaitingApproval, isSuperAdmin());

        List<Delivery> paginatedDeliveries;
        if (isSuperAdmin()) {
            paginatedDeliveries = deliveriesRepository.searchActiveDeliveries(deliveryFilter, page, DELIVERY_PAGE_SIZE);
        } else {
            paginatedDeliveries = deliveriesRepository.searchActiveDeliveries(getStoreId(), deliveryFilter, page, DELIVERY_PAGE_SIZE);
        }

        HashMap<String, Object> searchParams = new HashMap<>();
        searchParams.put("deliveryId", deliveryId);
        searchParams.put("externalDeliveryId", externalDeliveryId);
        searchParams.put("provider", provider);
        searchParams.put("orderedAtStart", orderedAtStart);
        searchParams.put("orderedAtEnd", orderedAtEnd);
        searchParams.put("showArchived", showArchived);
        searchParams.put("showWithoutInvoice", showWithoutInvoice);
        searchParams.put("showWithoutSync", showWithoutSync);
        searchParams.put("showAwaitingApproval", showAwaitingApproval);

        model.addAttribute("deliveries", paginatedDeliveries.subList(0, Math.min(paginatedDeliveries.size(), DELIVERY_PAGE_SIZE)));
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", paginatedDeliveries.size() > DELIVERY_PAGE_SIZE);
        model.addAttribute("searchParams", searchParams);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());

        return "deliveries";
    }

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

        if (form.getBankAmount() == 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.amount.invalid", null, locale));
            return redirectTarget;
        }

        if (form.getProcessingFee() < 0) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("error.message.payment.fee.invalid", null, locale));
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
        target.setAmount(form.getBankAmount());
        target.setFee(form.getProcessingFee());
        target.setBankTransactionNo(form.getBankTransactionNo());
        target.setBankTransactionDate(form.getBankTransactionDate());

        delivery.recomputePaid();
        deliveriesRepository.save(delivery);
        return redirectTarget;
    }

    @PostMapping("/dashboard/deliveries/{deliveryId}/updatePayments")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updatePayments(@PathVariable String deliveryId, @ModelAttribute("delivery") Delivery updatedDelivery,
                                 RedirectAttributes redirectAttributes, Locale locale) {
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
        if (isEditLocked(form.getStoreId(), form.getDeliveryId())) {
            return redirectEditLocked(form.getStoreId(), form.getDeliveryId(), redirectAttributes, locale);
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
        if (isOrderingInProgress(storeId, form.getDeliveryId())) {
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

    @GetMapping("/dashboard/deliveries/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public String deliveriesPreview(Model model) {
        return showDeliveriesPreview(getStoreId(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/preview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deliveriesPreviewForSuperAdmin(@PathVariable("storeId") String storeId, Model model) {
        return showDeliveriesPreview(storeId, model);
    }

    private String showDeliveriesPreview(String storeId, Model model) {
        var deliveries = deliveriesPlanningService.run(storeId);

        model.addAttribute("deliveries", deliveries);
        model.addAttribute("storeId", storeId);
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        return "deliveriesPreview";
    }

    @GetMapping("/dashboard/deliveries/create/{provider}")
    @PreAuthorize("hasRole('ADMIN')")
    public String createDeliveryForm(@PathVariable("provider") String provider, Model model) {
        return showCreateDeliveryForm(getStoreId(), provider, model);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/create/{provider}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String createDeliveryFormForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @PathVariable("provider") String provider,
            Model model) {
        return showCreateDeliveryForm(storeId, provider, model);
    }

    private String showCreateDeliveryForm(String storeId, String provider, Model model) {
        return showCreateDeliveryForm(storeId, provider, model, null);
    }

    private String backToCreateDeliveryForm(String storeId, String provider, DeliveryCreationForm posted, Model model) {
        return showCreateDeliveryForm(storeId, provider, model, posted);
    }

    private String showCreateDeliveryForm(String storeId, String provider, Model model, DeliveryCreationForm posted) {
        var delivery = deliveriesPlanningService.run(storeId, provider);

        if (delivery == null) {
            return isSuperAdmin()
                    ? "redirect:/dashboard/store/" + storeId + "/deliveries/preview"
                    : "redirect:/dashboard/deliveries/preview";
        }

        DeliveryCreationForm form = buildDeliveryCreationForm(storeId, provider, delivery);
        if (posted != null) {
            form.applyUserSelections(posted);
        }

        model.addAttribute("form", form);
        model.addAttribute("delivery", delivery);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("purchaseAvailable", supplierPurchaseService.isOrderingAvailable(storeId, provider));

        return "deliveryCreate";
    }

    private DeliveryCreationForm buildDeliveryCreationForm(String storeId, String provider, Delivery delivery) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(provider);
        form.setItems(groupAndUnify(delivery.getAllocations()));
        form.setTax(deliveryTaxResolver.resolveFor(provider));

        for (DeliveryItem item : form.getItems()) {
            for (Allocation allocation : item.getAllocations()) {
                allocation.setSelected(true);
            }
        }

        Set<String> existingMfns = delivery.getAllocations().stream()
                .map(Allocation::getMfn)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        form.setSuggestedItems(restockSuggestionService.suggestForDelivery(storeId, provider, existingMfns)
                .stream()
                .map(SuggestedDeliveryItem::from)
                .collect(Collectors.toList()));

        return form;
    }

    @PostMapping("/dashboard/deliveries/create/{provider}/updateFulfilment")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateDeliveryItemFulfilment(
            @PathVariable("provider") String provider,
            @ModelAttribute DeliveryFulfilmentUpdateForm form,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updateFulfilment(getStoreId(), provider, form, redirectAttributes, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create/{provider}/updateFulfilment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String updateDeliveryItemFulfilmentForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @PathVariable("provider") String provider,
            @ModelAttribute DeliveryFulfilmentUpdateForm form,
            RedirectAttributes redirectAttributes,
            Locale locale) {
        return updateFulfilment(storeId, provider, form, redirectAttributes, locale);
    }

    private String updateFulfilment(String storeId, String provider, DeliveryFulfilmentUpdateForm form, RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<Void> result = deliveryFulfilmentUpdateService.run(storeId, provider, form);

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(result.getMessage(), null, locale));
        }

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/deliveries/create/%s", storeId, provider)
                : "redirect:/dashboard/deliveries/create/" + provider;
    }

    @PostMapping("/dashboard/deliveries/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String processDeliveryCreation(@ModelAttribute DeliveryCreationForm form) {
        return processDelivery(getStoreId(), form);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String processDeliveryCreationForSuperAdmin(
            @PathVariable("storeId") String storeId,
            @ModelAttribute DeliveryCreationForm form) {
        return processDelivery(storeId, form);
    }

    private String processDelivery(String storeId, DeliveryCreationForm form) {
        form.setStoreId(storeId);

        String createdDeliveryId = deliveryCreationService.run(storeId, form);

        if (createdDeliveryId != null) {
            return isSuperAdmin()
                    ? storeDeliveryDetailsRedirect(storeId, createdDeliveryId)
                    : "redirect:/dashboard/deliveries/details?deliveryId=" + createdDeliveryId;
        }

        return isSuperAdmin()
                ? "redirect:/dashboard/store/" + storeId + "/deliveries/preview"
                : "redirect:/dashboard/deliveries/preview";
    }

    @PostMapping("/dashboard/deliveries/create/{provider}/purchase/back")
    @PreAuthorize("hasRole('ADMIN')")
    public String backFromPurchaseConfirmation(@PathVariable("provider") String provider,
                                               @ModelAttribute DeliveryCreationForm form, Model model) {
        return backToCreateDeliveryForm(getStoreId(), provider, form, model);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create/{provider}/purchase/back")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String backFromPurchaseConfirmationForSuperAdmin(@PathVariable("storeId") String storeId,
                                                             @PathVariable("provider") String provider,
                                                             @ModelAttribute DeliveryCreationForm form, Model model) {
        return backToCreateDeliveryForm(storeId, provider, form, model);
    }

    @PostMapping("/dashboard/deliveries/create/{provider}/purchase")
    @PreAuthorize("hasRole('ADMIN')")
    public String validatePurchase(@PathVariable("provider") String provider,
                                   @ModelAttribute DeliveryCreationForm form, Model model) {
        return showPurchaseConfirmation(getStoreId(), provider, form, model);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create/{provider}/purchase")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String validatePurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                                @PathVariable("provider") String provider,
                                                @ModelAttribute DeliveryCreationForm form, Model model) {
        return showPurchaseConfirmation(storeId, provider, form, model);
    }

    private String showPurchaseConfirmation(String storeId, String provider,
                                            DeliveryCreationForm form, Model model) {
        if (!supplierPurchaseService.isOrderingAvailable(storeId, provider)) {
            return isSuperAdmin()
                    ? String.format("redirect:/dashboard/store/%s/deliveries/create/%s", storeId, provider)
                    : "redirect:/dashboard/deliveries/create/" + provider;
        }

        form.setStoreId(storeId);
        form.setProvider(provider);
        supplierPurchaseService.mergeSuggestedItems(form);
        model.addAttribute("form", form);
        model.addAttribute("purchaseRef", UUID.randomUUID().toString());
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        addDeliveryAddresses(storeId, provider, form, model);
        return "deliveryPurchaseConfirmation";
    }

    private void addDeliveryAddresses(String storeId, String provider, DeliveryCreationForm form, Model model) {
        if (supplierPurchaseService.requiresApproval(storeId, provider)) {
            model.addAttribute("requiresApproval", true);
            return;
        }
        try {
            List<SupplierDeliveryAddress> addresses = supplierPurchaseService.deliveryAddresses(storeId, provider);
            model.addAttribute("deliveryAddresses", addresses);
            model.addAttribute("deliveryAddressOptions", addresses.stream()
                    .map(address -> new PickerOption(address.id(), address.label()))
                    .toList());
            if (addresses.size() == 1) {
                form.setDeliveryAddressId(addresses.getFirst().id());
            }
            addresses.stream()
                    .filter(address -> address.id().equals(form.getDeliveryAddressId()))
                    .findFirst()
                    .ifPresent(address -> model.addAttribute("deliveryAddressLabel", address.label()));
        } catch (Exception e) {
            model.addAttribute("deliveryAddresses", List.of());
            model.addAttribute("deliveryAddressOptions", List.of());
            model.addAttribute("deliveryAddressError", e.getMessage());
        }
    }

    @PostMapping("/dashboard/deliveries/create/{provider}/purchase/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public String validatePurchaseQuotes(@PathVariable("provider") String provider,
                                         @ModelAttribute DeliveryCreationForm form,
                                         Model model, Locale locale) {
        return renderValidationFragment(getStoreId(), provider, form, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create/{provider}/purchase/validate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String validatePurchaseQuotesForSuperAdmin(@PathVariable("storeId") String storeId,
                                                      @PathVariable("provider") String provider,
                                                      @ModelAttribute DeliveryCreationForm form,
                                                      Model model, Locale locale) {
        return renderValidationFragment(storeId, provider, form, model, locale);
    }

    private String renderValidationFragment(String storeId, String provider,
                                            DeliveryCreationForm form, Model model, Locale locale) {
        form.setStoreId(storeId);
        form.setProvider(provider);
        try {
            if (!supplierPurchaseService.isOrderingAvailable(storeId, provider)) {
                throw new IllegalStateException(provider);
            }
            model.addAttribute("validation", supplierPurchaseService.validate(storeId, form));
        } catch (Exception e) {
            model.addAttribute("validationError",
                    messageSource.getMessage("deliveries.purchase.confirm.checkFailed", null, locale)
                            + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""));
        }
        return "deliveryPurchaseConfirmation :: validationResult";
    }

    @PostMapping("/dashboard/deliveries/create/{provider}/purchase/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmPurchase(@PathVariable("provider") String provider,
                                  @RequestParam("purchaseRef") String purchaseRef,
                                  @ModelAttribute DeliveryCreationForm form,
                                  Model model, Locale locale) {
        return executePurchase(getStoreId(), provider, purchaseRef, form, model, locale);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/create/{provider}/purchase/confirm")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String confirmPurchaseForSuperAdmin(@PathVariable("storeId") String storeId,
                                               @PathVariable("provider") String provider,
                                               @RequestParam("purchaseRef") String purchaseRef,
                                               @ModelAttribute DeliveryCreationForm form,
                                               Model model, Locale locale) {
        return executePurchase(storeId, provider, purchaseRef, form, model, locale);
    }

    private String executePurchase(String storeId, String provider, String purchaseRef,
                                   DeliveryCreationForm form, Model model, Locale locale) {
        if (!supplierPurchaseService.isOrderingAvailable(storeId, provider)) {
            return isSuperAdmin()
                    ? String.format("redirect:/dashboard/store/%s/deliveries/create/%s", storeId, provider)
                    : "redirect:/dashboard/deliveries/create/" + provider;
        }

        form.setStoreId(storeId);
        form.setProvider(provider);
        OperationResult<PurchaseSubmission> result = supplierPurchaseService.submitPurchase(storeId, form, purchaseRef);

        if (!result.isSuccess()) {
            model.addAttribute("form", form);
            model.addAttribute("purchaseRef", purchaseRef);
            model.addAttribute("isSuperAdmin", isSuperAdmin());
            model.addAttribute("errorMessage", messageSource.getMessage(result.getMessage(), null, locale));
            addDeliveryAddresses(storeId, provider, form, model);
            return "deliveryPurchaseConfirmation";
        }

        String deliveryId = result.getPayload().deliveryId();
        return isSuperAdmin()
                ? storeDeliveryDetailsRedirect(storeId, deliveryId)
                : "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/{deliveryId}/approval")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showApprovalScreen(@PathVariable("storeId") String storeId,
                                     @PathVariable("deliveryId") String deliveryId,
                                     Model model, RedirectAttributes redirectAttributes) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isAwaitingApproval()) {
            if (model.containsAttribute("errorMessage")) {
                redirectAttributes.addFlashAttribute("errorMessage", model.getAttribute("errorMessage"));
            }
            return storeDeliveryDetailsRedirect(storeId, deliveryId);
        }
        model.addAttribute("delivery", delivery);
        addApprovalAddresses(storeId, delivery, model);
        addSuggestedAddress(storeId, model);
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
                                  RedirectAttributes redirectAttributes, Locale locale) {
        OperationResult<String> result = supplierPurchaseService.approve(storeId, deliveryId, deliveryAddressId);
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
        return "deliveryPurchaseConfirmation :: validationResult";
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
        model.addAttribute("paymentSources", PaymentSource.values());
        model.addAttribute("pendingPayment", delivery.getPendingPayment());
        if (delivery.isOrderFailed()) {
            model.addAttribute("suggestedEstimatedDeliveryAt", supplierPurchaseService.suggestEstimatedDeliveryAt(delivery));
        }
        return "deliveryDetails";
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

    private String redirectOrderingInProgress(String storeId, String deliveryId,
                                              RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage("deliveries.edit.locked.orderPending", null, locale));
        return detailsRedirect(storeId, deliveryId);
    }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }

    private boolean isAdmin() { return CustomSecurityContext.hasRole("ADMIN"); }

}
