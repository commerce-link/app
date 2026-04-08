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
import pl.commercelink.orders.PaymentStatus;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.DeliveryAllocationsForm;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.InvoiceSyncPreview;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

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
            @RequestParam(required = false, defaultValue = "1") int page,
            Model model) {
        DeliveryFilter deliveryFilter = new DeliveryFilter(deliveryId, externalDeliveryId, provider, orderedAtStart, orderedAtEnd, !showArchived, showWithoutInvoice, showWithoutSync);

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

        model.addAttribute("deliveries", paginatedDeliveries.subList(0, Math.min(paginatedDeliveries.size(), DELIVERY_PAGE_SIZE)));
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", paginatedDeliveries.size() > DELIVERY_PAGE_SIZE);
        model.addAttribute("searchParams", searchParams);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());

        return "deliveries";
    }

    @PostMapping("/dashboard/deliveries/markAsPaid")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String markDeliveryAsPaid(@RequestParam String deliveryId) {
        var delivery = deliveriesRepository.findById(getStoreId(), deliveryId);
        delivery.markAsPaid();
        deliveriesRepository.save(delivery);
        return "redirect:/dashboard/payments";
    }

    @PostMapping("/dashboard/deliveries/markSelectedAsReceived")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String markSelectedAllocationsAsReceived(@ModelAttribute DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        OperationResult<?> result = deliveryReceptionService.receive(
                form.getStoreId(),
                form.getProvider(),
                form.getDeliveryId(),
                form.getSelectedOrderAllocations(),
                form.getSelectedWarehouseAllocations(),
                form.getRemainingAllocations()
        );

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
        }

        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/deleteSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form) {
        return deleteAllocations(getStoreId(), form);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/deleteSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form) {
        return deleteAllocations(storeId, form);
    }

    private String deleteAllocations(String storeId, DeliveryAllocationsForm form) {
        form.getSelectedAllocations().forEach(allocation ->
                deliveriesManager.deleteAllocation(storeId, allocation)
        );
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/mergeSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String mergeSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        return mergeAllocations(getStoreId(), form, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/mergeSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String mergeSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        return mergeAllocations(storeId, form, redirectAttributes);
    }

    private String mergeAllocations(String storeId, DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        if (StringUtils.isBlank(form.getTargetDeliveryId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Target delivery ID cannot be empty for merge operation.");
            return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
        }

        deliveriesManager.reassignAllocations(
                storeId,
                form.getTargetDeliveryId(),
                form.getSelectedOrderAllocations(),
                form.getSelectedWarehouseAllocations()
        );
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/splitSelectedAllocations")
    @PreAuthorize("hasRole('ADMIN')")
    public String splitSelectedAllocations(@ModelAttribute DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        return splitAllocations(getStoreId(), form, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/splitSelectedAllocations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String splitSelectedAllocationsForSuperAdmin(@PathVariable("storeId") String storeId, @ModelAttribute DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
        return splitAllocations(storeId, form, redirectAttributes);
    }

    private String splitAllocations(String storeId, DeliveryAllocationsForm form, RedirectAttributes redirectAttributes) {
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
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDelivery(@RequestParam String deliveryId) {
        return deleteDelivery(getStoreId(), deliveryId);
    }

    @PostMapping("/dashboard/store/{storeId}/deliveries/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteDeliveryForSuperAdmin(@PathVariable("storeId") String storeId, @RequestParam String deliveryId) {
        return deleteDelivery(storeId, deliveryId);
    }

    private String deleteDelivery(String storeId, String deliveryId) {
        var delivery = deliveriesRepository.findById(storeId, deliveryId);
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
        var delivery = deliveriesPlanningService.run(storeId, provider);

        if (delivery == null) {
            return isSuperAdmin()
                    ? "redirect:/dashboard/store/" + storeId + "/deliveries/preview"
                    : "redirect:/dashboard/deliveries/preview";
        }

        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(provider);
        form.setItems(groupAndUnify(delivery.getAllocations()));
        form.setPaymentStatus(PaymentStatus.New);

        for (DeliveryItem item : form.getItems()) {
            for (Allocation allocation : item.getAllocations()) {
                allocation.setSelected(true);
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("delivery", delivery);
        model.addAttribute("isSuperAdmin", isSuperAdmin());

        return "deliveryCreate";
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
        deliveryCreationService.run(storeId, form, isSuperAdmin());

        return isSuperAdmin()
                ? "redirect:/dashboard/store/" + storeId + "/deliveries/preview"
                : "redirect:/dashboard/deliveries/preview";
    }

    @GetMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public String showDeliveryDetails(@RequestParam String deliveryId, Model model) {
        return showDeliveryDetails(getStoreId(), deliveryId, model);
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String showDeliveryDetailsForSuperAdmin(@PathVariable("storeId") String storeId, @RequestParam String deliveryId, Model model) {
        return showDeliveryDetails(storeId, deliveryId, model);
    }

    private String showDeliveryDetails(String storeId, String deliveryId, Model model) {
        var delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        var mergeTargetDeliveries = deliveriesRepository.findPendingDeliveriesByProvider(
                storeId, delivery.getProvider(), deliveryId);

        model.addAttribute("delivery", delivery);
        model.addAttribute("allocationsForm", new DeliveryAllocationsForm(
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(), delivery.getAllocations()));
        model.addAttribute("mergeTargetDeliveries", mergeTargetDeliveries);
        model.addAttribute("isSuperAdmin", isSuperAdmin());
        model.addAttribute("isAdmin", isAdmin());
        model.addAttribute("supplierRegistry", supplierRegistry);
        return "deliveryDetails";
    }

    @PostMapping("/dashboard/deliveries/details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public String updateDelivery(@ModelAttribute Delivery updatedDelivery) {
        var existingDelivery = deliveriesQueryService.fetchDeliveryWithAllocations(updatedDelivery.getStoreId(), updatedDelivery.getDeliveryId());

        boolean isDeliveryDelayed = updatedDelivery.getEstimatedDeliveryAt() != null &&
                updatedDelivery.getEstimatedDeliveryAt().isAfter(existingDelivery.getEstimatedDeliveryAt());

        existingDelivery.setEstimatedDeliveryAt(updatedDelivery.getEstimatedDeliveryAt());
        existingDelivery.setShippingCost(updatedDelivery.getShippingCost());
        existingDelivery.setPaymentCost(updatedDelivery.getPaymentCost());

        existingDelivery.setPaymentTerms(updatedDelivery.getPaymentTerms());

        existingDelivery.addEvent(new Event(EventType.action, "DELIVERY_UPDATED", LocalDateTime.now()));

        if (isDeliveryDelayed) {
            deliveriesManager.updateAssociatedOrdersForDeliveryChange(updatedDelivery.getStoreId(), existingDelivery);
            existingDelivery.addEvent(new Event(EventType.action, "DELIVERY_DELAYED", LocalDateTime.now()));
        }

        deliveriesRepository.save(existingDelivery);

        return isSuperAdmin()
                ? String.format("redirect:/dashboard/store/%s/deliveries/details?deliveryId=%s", existingDelivery.getStoreId(), existingDelivery.getDeliveryId())
                : "redirect:/dashboard/deliveries/details?deliveryId=" + existingDelivery.getDeliveryId();
    }

    @PostMapping("/dashboard/deliveries/link-invoices")
    @PreAuthorize("hasRole('ADMIN')")
    public String linkInvoices(@RequestParam String deliveryId) {
        invoiceLinkingService.linkInvoices(getStoreId(), deliveryId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/link-invoice-by-id")
    @PreAuthorize("hasRole('ADMIN')")
    public String linkInvoiceById(@RequestParam String deliveryId, @RequestParam String invoiceId) {
        invoiceLinkingService.linkInvoiceById(getStoreId(), deliveryId, invoiceId);
        return "redirect:/dashboard/deliveries/details?deliveryId=" + deliveryId;
    }

    @PostMapping("/dashboard/deliveries/unlink-invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public String unlinkInvoice(@RequestParam String deliveryId, @RequestParam String invoiceId) {
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
    public String applyInvoiceSync(@ModelAttribute InvoiceSyncPreview form, RedirectAttributes redirectAttributes) {
        invoiceSynchronizationService.apply(getStoreId(), form);
        redirectAttributes.addFlashAttribute("successMessage", "Synchronizacja zakonczona pomyslnie.");
        return "redirect:/dashboard/deliveries/details?deliveryId=" + form.getDeliveryId();
    }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }

    private boolean isAdmin() { return CustomSecurityContext.hasRole("ADMIN"); }

}
