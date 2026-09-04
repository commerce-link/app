package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.orders.*;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.marketplace.MarketplaceReturnDecisions;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@PreAuthorize("!hasRole('SUPER_ADMIN')")
@Controller
@RequestMapping
public class RMAController {

    @Autowired
    private RMARepository rmaRepository;

    @Autowired
    private MarketplaceReturnDecisions marketplaceReturnDecisions;

    @Autowired
    private RMAItemsRepository rmaItemsRepository;

    @Autowired
    private RMALifecycle rmaLifecycle;

    @Autowired
    private RMAManager rmaManager;

    @Autowired
    private OrdersRepository orderRepository;

    @Autowired
    private OrderItemsRepository orderItemsRepository;

    @Autowired
    private OrdersRMAManager ordersRMAManager;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private OpenRmaCoverage openRmaCoverage;

    @Value("${app.domain}")
    private String appDomain;

    @Value("${s3.bucket.stores}")
    private String bucketName;

    private final int RMA_PAGE_SIZE = 25;

    @GetMapping("/dashboard/rma")
    public String rma(@RequestParam(required = false) String rmaId,
                      @RequestParam(required = false) String orderId,
                      @RequestParam(required = false) String email,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtStart,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdAtEnd,
                      @RequestParam(required = false) List<RMAStatus> statuses,
                      @RequestParam(required = false, defaultValue = "1") int page,
                      Model model) {
        RMAFilter filter = new RMAFilter(rmaId, orderId, email, createdAtStart, createdAtEnd, statuses);
        List<RMA> paginatedRmaEntries = rmaRepository.searchRMAEntries(getStoreId(), filter, page, RMA_PAGE_SIZE);

        HashMap<String, Object> searchParams = new HashMap<>();
        searchParams.put("rmaId", rmaId);
        searchParams.put("orderId", orderId);
        searchParams.put("email", email);
        searchParams.put("createdAtStart", createdAtStart);
        searchParams.put("createdAtEnd", createdAtEnd);
        if (filter.hasStatuses()) {
            searchParams.put("statuses", filter.getStatuses().stream().map(Enum::name).collect(Collectors.joining(",")));
        }

        model.addAttribute("rmaEntries", paginatedRmaEntries.subList(0, Math.min(paginatedRmaEntries.size(), RMA_PAGE_SIZE)));
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", paginatedRmaEntries.size() > RMA_PAGE_SIZE);
        model.addAttribute("searchParams", searchParams);
        model.addAttribute("activeStatuses", Arrays.stream(RMAStatus.values())
                .filter(status -> !status.isClosed())
                .collect(Collectors.toList()));
        model.addAttribute("closedStatusNames", Arrays.stream(RMAStatus.values())
                .filter(RMAStatus::isClosed)
                .map(Enum::name)
                .collect(Collectors.toList()));
        model.addAttribute("selectedStatuses", filter.getStatuses().stream().map(Enum::name).collect(Collectors.toList()));
        model.addAttribute("closedSelected", filter.getStatuses().stream().anyMatch(RMAStatus::isClosed));
        model.addAttribute("countsByStatus", rmaRepository.countActiveByStatus(getStoreId()));

        return "rma";
    }

    @GetMapping("/dashboard/rma/new")
    public String showCreateRma(@RequestParam(value = "orderId", required = false) String orderId,
                                @RequestParam(value = "serialNo", required = false) String serialNo,
                                @RequestParam(value = "byEmail", required = false) String email,
                                Model model, Locale locale) {
        List<OrderItem> orderItems = new ArrayList<>();
        RMA rma = new RMA(getStoreId());
        RmaCreateMode mode = RmaCreateMode.BY_ORDER_LINE;

        if (isNotBlank(orderId)) {
            rma.setOrderId(orderId);
            Order order = orderRepository.findById(getStoreId(), orderId);
            if (order != null) {
                if (!order.isEligibleForRMACreation()) {
                    model.addAttribute("errorMessage", messageSource.getMessage("rma.order.must.be.delivered.completed", null, locale));
                } else {
                    orderItems = orderItemsRepository.findByOrderIdAndStatuses(orderId, Arrays.asList(FulfilmentStatus.Delivered, FulfilmentStatus.Reserved));
                    rma.setEmail(order.getEmail());
                }
            }
        } else if (isNotBlank(serialNo)) {
            mode = RmaCreateMode.BY_SERIAL_NO;
            OrderItem orderItem = orderItemsRepository.findBySerialNoAndStatuses(serialNo, Arrays.asList(FulfilmentStatus.Delivered, FulfilmentStatus.Reserved));
            if (orderItem != null) {
                orderItems = Collections.singletonList(orderItem);

                Order order = orderRepository.findById(getStoreId(), orderItem.getOrderId());
                if (!order.isEligibleForRMACreation()) {
                    model.addAttribute("errorMessage", messageSource.getMessage("rma.order.must.be.delivered.completed", null, locale));
                    orderItems = new ArrayList<>();
                } else {
                    rma.setOrderId(order.getOrderId());
                    rma.setEmail(order.getEmail());
                }
            }
        } else if (isNotBlank(email)) {
            mode = RmaCreateMode.BY_EMAIL;
            List<Order> emailOrders = orderRepository.findByEmail(getStoreId(), email);
            if (!emailOrders.isEmpty()) {
                model.addAttribute("emailOrders", emailOrders);
            }
        }

        model.addAttribute("rma", rma);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("mode", mode.name());
        model.addAttribute("rmaResolutionTypes", RMAResolutionType.values());

        return "rma-new";
    }

    @GetMapping("/dashboard/rma/{rmaId}")
    public String showRmaDetail(@PathVariable String rmaId, Model model) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        List<RMAItem> rmaItems = rmaItemsRepository.findByRmaId(rmaId);
        RMAItemsForm rmaItemsForm = new RMAItemsForm(rmaItems);
        List<OrderItem> remainingOrderItems = orderItemsRepository.findByOrderId(rma.getOrderId())
                .stream()
                .filter(oi -> rmaItems.stream().noneMatch(ri -> ri.getItemId().equals(oi.getItemId())))
                .filter(oi -> !oi.hasOneOfTheStatuses( FulfilmentStatus.Returned, FulfilmentStatus.Replaced))
                .filter(oi -> !(rma.isMarketplaceReturn() && oi.isService()))
                .collect(Collectors.toList());

        model.addAttribute("rma", rma);
        model.addAttribute("itemConditions", ItemCondition.values());
        model.addAttribute("rmaStatusTypes", RMAStatus.values());
        model.addAttribute("rmaResolutionsTypes", RMAResolutionType.values());
        model.addAttribute("rmaItemsForm", rmaItemsForm);
        model.addAttribute("backofficeDomain", appDomain);
        model.addAttribute("isClosed", rma.getStatus() == RMAStatus.Rejected || rma.getStatus() == RMAStatus.Completed);
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("remainingOrderItems", remainingOrderItems);
        model.addAttribute("refundDeliveryDefault",
                rma.isMarketplaceReturn() && marketplaceReturnDecisions.coversWholeOrder(rma, rmaItems));

        return "rma-detail";
    }

    @GetMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String getRmaItem(@PathVariable String rmaId, @PathVariable String rmaItemId, Model model) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        RMAItem rmaItem = rmaItemsRepository.findById(rmaId, rmaItemId);

        model.addAttribute("rma", rma);
        model.addAttribute("rmaId", rmaId);
        model.addAttribute("rmaItem", rmaItem);
        model.addAttribute("isClosed", rma.getStatus() == RMAStatus.Rejected || rma.getStatus() == RMAStatus.Completed);
        model.addAttribute("rmaStatusTypes", RMAStatus.values());
        model.addAttribute("rmaResolutionsTypes", RMAResolutionType.values());

        return "rma-item";
    }

    @GetMapping("/dashboard/rma/{rmaId}/shipping")
    public String editRmaShipping(@PathVariable String rmaId, Model model) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        model.addAttribute("rma", rma);
        model.addAttribute("isClosed", rma.getStatus() == RMAStatus.Rejected || rma.getStatus() == RMAStatus.Completed);

        return "rma-shipping";
    }

    @PostMapping("/dashboard/rma/new")
    public String createRma(@ModelAttribute RMA rma, RedirectAttributes redirectAttributes, Locale locale) {
        Order order = orderRepository.findById(getStoreId(), rma.getOrderId());
        if (order == null || !order.isEligibleForRMACreation()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.order.must.be.delivered.completed", null, locale));
            return "redirect:/dashboard/rma/new";
        }

        List<RMAItem> draftRmaItems = rma.getDraftRmaItems().stream()
                .filter(RMAItem::isComplete)
                .map(rmaItem -> {
                    OrderItem orderItem = orderItemsRepository.findById(rma.getOrderId(), rmaItem.getItemId());
                    return new RMAItem(rma.getRmaId(), orderItem, rmaItem);
                })
                .collect(Collectors.toList());

        if (draftRmaItems.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.no.items.selected", null, locale));
            return "redirect:/dashboard/rma/new";
        }

        rma.setCreatedAt(LocalDateTime.now());
        rma.setEmailNotificationsEnabled(true);
        rma.setShippingInsurance(RMAItem.computeTotalPrice(draftRmaItems));
        rmaRepository.save(rma);

        rmaItemsRepository.batchSave(draftRmaItems);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("rma.create.success", null, locale));
        return "redirect:/dashboard/rma/" + rma.getRmaId();
    }

    @PostMapping("/dashboard/rma/{rmaId}")
    public String updateRma(@PathVariable String rmaId, @ModelAttribute RMA rma,
                            @RequestParam(required = false, name = "existingMedia") List<String> existingMedia,
                            @RequestParam MultiValueMap<String, MultipartFile> rmaMedia,
                            RedirectAttributes redirectAttributes, Locale locale) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        // The form disables status/email/shippingInsurance/rejectionReason/media once the RMA is closed
        // (see rma-detail.html th:disabled="${isClosed}"), and a browser never submits disabled fields -
        // so a resubmission of an already-closed RMA (double-click, back-button) would otherwise bind
        // those fields to null/default and silently wipe them below.
        if (existingRma.getStatus() != null && existingRma.getStatus().isClosed()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.already.closed", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        if (marketplaceReturnDecisions.requiresRejectionReason(existingRma, rma.getStatus(), rma.getRejectionReason())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.rejection.reason.required", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        if (marketplaceReturnDecisions.blocksRejectionAfterRefund(existingRma, rma.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.rejection.after.refund", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        existingRma.setStatus(rma.getStatus());
        existingRma.setEmail(rma.getEmail());
        existingRma.setRejectionReason(rma.getRejectionReason());
        existingRma.setEmailNotificationsEnabled(rma.isEmailNotificationsEnabled());
        existingRma.setShippingInsurance(rma.getShippingInsurance());

        List<String> media = new ArrayList<>();
        if (existingMedia != null) media.addAll(existingMedia);
        List<String> newMedia = rmaMedia.values().stream()
                .flatMap(List::stream)
                .filter(file -> !file.isEmpty())
                .map(file -> {
                    String key = getStoreId() + "/rma/" + rmaId + "/" + file.getOriginalFilename();
                    try {
                        fileStorage.put(bucketName, key, file.getBytes());
                        return key;
                    } catch (IOException e) {
                        throw new RuntimeException("Upload failed", e);
                    }
                })
                .collect(Collectors.toList());
        media.addAll(newMedia);
        existingRma.setMedia(media);

        rmaLifecycle.update(existingRma);

        // Gate on the event, not the status transition, so a rejection whose publish previously failed
        // (RMA already Rejected, but RejectionSent never recorded) is retried on the next save.
        boolean rejectionPending = existingRma.getStatus() == RMAStatus.Rejected
                && !existingRma.hasActionEvent(RMA.EVENT_REJECTION_SENT);
        if (rejectionPending) {
            marketplaceReturnDecisions.returnRejected(existingRma);
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("rma.update.success", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/resend-marketplace-decision")
    public String resendMarketplaceDecision(@PathVariable String rmaId, RedirectAttributes redirectAttributes,
                                            Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        boolean resent = marketplaceReturnDecisions.resendLastDecision(rma);
        redirectAttributes.addFlashAttribute(resent ? "successMessage" : "errorMessage",
                messageSource.getMessage(resent ? "rma.marketplace.resend.success" : "rma.marketplace.resend.unavailable",
                        null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/add-item")
    public String addRmaItemFromOrder(
            @PathVariable String rmaId,
            @RequestParam("orderItemId") String orderItemId,
            @RequestParam("quantity") int quantity,
            @RequestParam("desiredResolution") String desiredResolution,
            @RequestParam(value = "reason", required = false) String reason,
            RedirectAttributes redirectAttributes, Locale locale
    ) {
        String storeId = getStoreId();
        RMA rma = rmaRepository.findById(storeId, rmaId);
        OrderItem orderItem = orderItemsRepository.findById(rma.getOrderId(), orderItemId);
        if (orderItem == null || orderItem.hasOneOfTheStatuses(FulfilmentStatus.Returned, FulfilmentStatus.Replaced)
                || quantity <= 0 || quantity > orderItem.getQty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.item.invalid.quantity", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        if (rma.isMarketplaceReturn() && orderItem.isService()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.item.service.not.returnable", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        if (openRmaCoverage.coversOrderItem(storeId, orderItemId, rmaId)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.item.already.in.open.rma", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        RMAItem source = new RMAItem();
        source.setRmaId(rmaId);
        source.setItemId(orderItemId);
        source.setQty(quantity);
        source.setDesiredResolution(RMAResolutionType.valueOf(desiredResolution));
        source.setReason(reason);

        RMAItem newRMAItem = new RMAItem(rmaId, orderItem, source);
        rmaItemsRepository.save(newRMAItem);

        rma.increaseShippingInsurance(newRMAItem.getTotalPrice());
        rmaRepository.save(rma);

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}/save")
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public String updateRmaItem(@PathVariable String rmaId, @PathVariable String rmaItemId,
                                @ModelAttribute("rmaItem") RMAItem formItem,
                                RedirectAttributes redirectAttributes, Locale locale) {
        RMAItem existing = rmaItemsRepository.findById(rmaId, rmaItemId);
        if (existing == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.item.not.found", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        existing.setSerialNo(formItem.getSerialNo());
        existing.setReason(formItem.getReason());
        existing.setComment(formItem.getComment());

        rmaItemsRepository.save(existing);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("rma.item.update.success", null, locale));

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsReceived")
    public String markItemsAsReceived(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form) {
        rmaManager.markItemsAsReceived(getStoreId(), rmaId, form.getSelectedRMAItemIds());
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/replaceItems")
    public String replaceItems(@PathVariable String rmaId, @RequestParam boolean broken,
                               @RequestParam(required = false) ItemCondition condition,
                               @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        if (condition == null) {
            return redirectWithMissingCondition(rmaId, redirectAttributes, locale);
        }

        RMAManager.OperationResult op = rmaManager.replaceSelectedItems(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.replace.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        OperationResult<?> result = ordersRMAManager.createReplacementOrder(
                getStoreId(),
                op.getRma(),
                op.getRmaItems(),
                broken,
                condition
        );

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.warehouse.document.generation.failed", null, locale));
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/acceptReturn")
    public String acceptReturn(@PathVariable String rmaId, @RequestParam(required = false) ItemCondition condition,
                               @RequestParam(required = false, defaultValue = "false") boolean refundDelivery,
                               @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        if (condition == null) {
            return redirectWithMissingCondition(rmaId, redirectAttributes, locale);
        }

        RMAManager.OperationResult op = rmaManager.returnSelectedItems(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.move.to.warehouse.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        // The checkbox default was computed for the whole RMA at render time; the operator may have
        // selected a subset, so the delivery refund must be re-derived from what was actually accepted.
        // This MUST run before ordersRMAManager.acceptReturn: accepting can split an OrderItem (one
        // fragment marked Returned, the remainder left open), and coversWholeOrder's "already
        // Returned/Replaced items don't need coverage" rule assumes it is looking at the pre-acceptance
        // state - evaluated afterwards, the just-split-off fragment is wrongly treated as a prior, separate
        // decision, while the leftover fragment coincidentally absorbs this batch's quantity instead.
        boolean deliveryCovered = refundDelivery
                && marketplaceReturnDecisions.coversWholeOrder(op.getRma(), op.getRmaItems());

        OperationResult<?> result = ordersRMAManager.acceptReturn(
                getStoreId(),
                op.getRma(),
                op.getRmaItems(),
                condition
        );

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.warehouse.document.generation.failed", null, locale));
        } else {
            marketplaceReturnDecisions.returnAccepted(op.getRma(), op.getRmaItems(), deliveryCovered);
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    private String redirectWithMissingCondition(String rmaId, RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage("rma.item.condition.required", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsReturnedToClient")
    public String markItemsAsReturnedToClient(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        RMAManager.OperationResult op = rmaManager.markItemsAsReturnedToClient(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.mark.as.returned.to.client.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsSentToDistributor")
    public String markItemsAsSentToDistributor(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        RMAManager.OperationResult op = rmaManager.markItemsAsSentToDistributor(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.mark.as.sent.to.distributor.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/updateShippingDetails")
    public String updateShippingDetails(@PathVariable String rmaId, @ModelAttribute("rma") RMA updatedRma) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        if (updatedRma.getShippingDetails() != null) {
            existingRma.setShippingDetails(updatedRma.getShippingDetails());
        }

        rmaRepository.save(existingRma);
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/updateShipments")
    public String updateShipments(@PathVariable String rmaId, @ModelAttribute("rma") RMA updatedRma) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        if (updatedRma.getShipments() != null) {
            List<Shipment> shipments = updatedRma.getShipments().stream()
                    .filter(s -> s.hasShippingData() || s.hasCollectionData())
                    .collect(Collectors.toList());

            if (shipments.isEmpty()) {
                shipments.add(updatedRma.getShipments().get(0));
            }

            existingRma.setShipments(shipments);
        }
        rmaRepository.save(existingRma);
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}/split")
    public String splitRmaItem(@PathVariable("rmaId") String rmaId,
                               @PathVariable("rmaItemId") String rmaItemId,
                               @RequestParam("qty1") int qty1,
                               @RequestParam("qty2") int qty2,
                               RedirectAttributes redirectAttributes) {
        RMAItem original = rmaItemsRepository.findById(rmaId, rmaItemId);

        if (original == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "RMA item not found.");
            return "redirect:/dashboard/rma/" + rmaId;
        }

        int originalQty = original.getQty();

        if (qty1 < 1 || qty2 < 1 || qty1 + qty2 != originalQty) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid split quantities.");
            return "redirect:/dashboard/rma/" + rmaId;
        }

        original.setQty(qty1);
        rmaItemsRepository.save(original);

        RMAItem item2 = original.copyWithNewQty(qty2);
        rmaItemsRepository.save(item2);

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/checkSerialNumbers")
    public String checkSerialNumbers(@PathVariable String rmaId,
                                      @ModelAttribute RMAItemsForm form,
                                      RedirectAttributes redirectAttributes,
                                      Locale locale) {
        List<String> selectedRMAItemIds = form.getSelectedRMAItemIds();

        if (selectedRMAItemIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.check.serial.no.items.selected", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        RMA rma = rmaRepository.findById(getStoreId(), rmaId);

        List<String> errorMessages = new ArrayList<>();

        for (String rmaItemId : selectedRMAItemIds) {
            RMAItem rmaItem = form.getRmaItemBy(rmaItemId);
            OrderItem orderItem = orderItemsRepository.findById(rma.getOrderId(), rmaItem.getItemId());

            List<String> invalidSerialNumbers = orderItem.getInvalidSerialNumbers(rmaItem.getSerialNo());

            if (!invalidSerialNumbers.isEmpty()) {
                String invalidSNs = String.join(", ", invalidSerialNumbers);
                errorMessages.add(messageSource.getMessage("rma.check.serial.no.mismatch",
                        new Object[]{rmaItem.getName(), invalidSNs}, locale));
            }
        }

        if (errorMessages.isEmpty()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("rma.check.serial.no.all.valid", null, locale));
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", String.join("<br>", errorMessages));
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/deleteItems")
    public String deleteRMAItems(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        List<RMAItem> rmaItemsToDelete = form.getSelectedRMAItemIds().stream()
                .map(rmaItemId -> rmaItemsRepository.findById(rmaId, rmaItemId))
                .filter(i -> i.hasOneOfTheStatuses(RMAItemStatus.New, RMAItemStatus.Received))
                .collect(Collectors.toList());

        if  (rmaItemsToDelete.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are allowed to delete only New or Received RMA items.");
            return  "redirect:/dashboard/rma/" + rmaId;
        }

        rmaItemsRepository.delete(rmaItemsToDelete);

        double totalAmount = rmaItemsToDelete.stream().mapToDouble(RMAItem::getTotalPrice).sum();

        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        rma.decreaseShippingInsurance(totalAmount);
        rmaRepository.save(rma);

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("rma.items.delete.success", new Object[]{rmaItemsToDelete.size()}, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @GetMapping("/rma/{rmaId}/media/{fileName:.+}")
    public ResponseEntity<?> getRmaMedia(@PathVariable String rmaId, @PathVariable String fileName) {
        try {
            String key = getStoreId() + "/rma/" + rmaId + "/" + fileName;
            byte[] data = fileStorage.getBytes(bucketName, key);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(getContentType(fileName))
                    .contentLength(data.length)
                    .body(new ByteArrayResource(data));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    public enum RmaCreateMode {
        BY_ORDER_LINE,
        BY_SERIAL_NO,
        BY_EMAIL
    }

    private MediaType getContentType(String fileName) {
        String lower = fileName.toLowerCase();

        // Images
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;

        // PDFs
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;

        // Videos
        if (lower.endsWith(".mp4")) return MediaType.valueOf("video/mp4");
        if (lower.endsWith(".mov")) return MediaType.valueOf("video/quicktime");
        if (lower.endsWith(".avi")) return MediaType.valueOf("video/x-msvideo");
        if (lower.endsWith(".mkv")) return MediaType.valueOf("video/x-matroska");

        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }

}
