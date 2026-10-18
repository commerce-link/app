package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.orders.*;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.rma.RmaAddressForm;
import pl.commercelink.web.rma.RmaActionNotices;
import pl.commercelink.web.rma.RmaAddressPage;
import pl.commercelink.web.rma.RmaFlash;
import pl.commercelink.web.rma.RmaNotice;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@PreAuthorize("!hasRole('SUPER_ADMIN')")
@Controller
@RequestMapping
@RequiredArgsConstructor
public class RMAController {

    private final RMARepository rmaRepository;

    private final MarketplaceReturnDecisions marketplaceReturnDecisions;

    private final RMAItemsRepository rmaItemsRepository;

    private final RMALifecycle rmaLifecycle;

    private final RMAManager rmaManager;

    private final OrderItemsRepository orderItemsRepository;

    private final OrdersRMAManager ordersRMAManager;

    private final FileStorage fileStorage;

    private final MessageSource messageSource;

    private final OpenRmaCoverage openRmaCoverage;

    private final RmaShipmentsViewFactory rmaShipmentsViewFactory;

    private final OrdersRepository ordersRepository;

    /** Set by "Ship to client" when the RMA's address is incomplete: the address page then shows every missing field. */
    public static final String ADDRESS_INCOMPLETE_FLASH = "rmaAddressIncomplete";

    @Value("${app.domain}")
    private String appDomain;

    @Value("${s3.bucket.stores}")
    private String bucketName;

    /**
     * RMA is bound straight from the form. The marketplace-decision fields (externalReturnId,
     * marketplaceDecisions, ...) feed a real refund through the resend path and must never come from the
     * request; storeId always comes from the session.
     */
    @InitBinder
    void restrictBindableRmaFields(WebDataBinder binder) {
        if (binder.getTarget() instanceof RMA) {
            binder.setAllowedFields("rmaId", "orderId", "email", "status", "rejectionReason",
                    "emailNotificationsEnabled", "shippingInsurance", "draftRmaItems*", "shippingDetails*", "shipments*");
        }
    }

    @GetMapping("/dashboard/rma/{rmaId}")
    public String showRmaDetail(@PathVariable String rmaId, Model model, Locale locale) {
        RmaFlash.adoptLegacy(model);
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
        model.addAttribute("isClosed", isClosed(rma));
        model.addAttribute("rmaShipments", rmaShipmentsViewFactory.build(rma, isClosed(rma), locale));
        model.addAttribute("shipmentTypes", ShipmentType.values());
        model.addAttribute("remainingOrderItems", remainingOrderItems);
        model.addAttribute("refundDeliveryDefault",
                rma.isMarketplaceReturn() && marketplaceReturnDecisions.coversEveryReturnableItem(rma, rmaItems));

        return "rma-detail";
    }

    /** The address "Ship to client" sends the goods back to; ?fill=order puts the order's address in the form, unsaved. */
    @GetMapping("/dashboard/rma/{rmaId}/shipping")
    public String editRmaShipping(@PathVariable String rmaId, @RequestParam(required = false) String fill, Model model,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        ShippingDetails orderAddress = orderAddressOf(rma);
        boolean filled = "order".equals(fill) && orderAddress != null;
        RmaAddressForm form = filled ? RmaAddressForm.of(orderAddress).withCountryCode() : RmaAddressForm.of(rma.getShippingDetails());
        boolean incomplete = Boolean.TRUE.equals(model.getAttribute(ADDRESS_INCOMPLETE_FLASH));
        if (incomplete && RmaAddressForm.hasAnyValue(rma.getShippingDetails())) {
            // against the saved address only the missing fields are reported; passing null would also flag the format of values nobody touched
            form = form.withErrors(form.validate(rma.getShippingDetails()));
        }
        // an RMA with no address at all gets one notice instead of an error on every field nobody has touched yet
        return addressPage(rma, form, orderAddress, filled, incomplete, incomplete, model);
    }

    @PostMapping("/dashboard/rma/{rmaId}")
    public String updateRma(@PathVariable String rmaId, @ModelAttribute RMA rma,
                            @RequestParam(required = false, name = "existingMedia") List<String> existingMedia,
                            @RequestParam MultiValueMap<String, MultipartFile> rmaMedia,
                            RedirectAttributes redirectAttributes, Locale locale) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(existingRma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        if (existingRma.requiresRejectionReason(rma.getStatus(), rma.getRejectionReason())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.rejection.reason.required", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }
        if (existingRma.blocksRejectionAfterRefund(rma.getStatus())) {
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
        if (rejectionPending && !marketplaceReturnDecisions.publishRejection(existingRma)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.marketplace.decision.not.sent", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        redirectAttributes.addFlashAttribute("successMessage", messageSource.getMessage("rma.update.success", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/resend-marketplace-decision")
    public String resendMarketplaceDecision(@PathVariable String rmaId, RedirectAttributes redirectAttributes,
                                            Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        if (rma == null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.not.found", null, locale));
            return "redirect:/dashboard/rma";
        }
        boolean resent = marketplaceReturnDecisions.resendDecisions(rma);
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
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
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
        if (openRmaCoverage.coveredByAnotherOpenRma(storeId, orderItemId, rmaId)) {
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

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsReceived")
    public String markItemsAsReceived(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form,
                                      RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        List<RMAItem> selected = selectedItems(rmaId, form);
        RMAManager.OperationResult op = rmaManager.markItemsAsReceived(getStoreId(), rmaId, form.getSelectedRMAItemIds());
        RmaFlash.items(redirectAttributes, done("rma.detail.done.received", selected, op, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/replaceItems")
    public String replaceItems(@PathVariable String rmaId, @RequestParam boolean broken,
                               @RequestParam(required = false) ItemCondition condition,
                               @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        if (condition == null) {
            return redirectWithMissingCondition(rmaId, redirectAttributes, locale);
        }

        RMA current = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(current, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<RMAItem> selected = selectedItems(rmaId, form);
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
        } else {
            RmaFlash.items(redirectAttributes, done("rma.detail.done.replaced", selected, op, locale));
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

        RMA current = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(current, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<RMAItem> selected = selectedItems(rmaId, form);
        RMAManager.OperationResult op = rmaManager.returnSelectedItems(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.move.to.warehouse.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        // Coverage must be evaluated BEFORE the warehouse splits order items (pinned by
        // RMAControllerTest.acceptReturnEvaluatesWholeOrderCoverageBeforeMutatingOrderItems).
        boolean deliveryCovered = refundDelivery
                && marketplaceReturnDecisions.coversEveryReturnableItem(op.getRma(), op.getRmaItems());

        OperationResult<?> result = ordersRMAManager.acceptReturn(
                getStoreId(),
                op.getRma(),
                op.getRmaItems(),
                condition
        );

        if (!result.isSuccess()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.warehouse.document.generation.failed", null, locale));
        } else if (!marketplaceReturnDecisions.publishAcceptance(op.getRma(), op.getRmaItems(), deliveryCovered)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.marketplace.decision.not.sent", null, locale));
        } else {
            RmaNotice notice = done("rma.detail.done.accepted", selected, op, locale);
            if (op.getRma().isMarketplaceReturn() && refundDelivery) {
                notice = notice.withLine(messageSource.getMessage(
                        deliveryCovered ? "rma.detail.delivery.refunded" : "rma.detail.delivery.notRefunded", null, locale));
            }
            RmaFlash.items(redirectAttributes, notice);
        }

        return "redirect:/dashboard/rma/" + rmaId;
    }

    /** The items the operator ticked, read before the action changes their statuses so that skipped ones can be explained. */
    private List<RMAItem> selectedItems(String rmaId, RMAItemsForm form) {
        List<String> ids = form.getSelectedRMAItemIds();
        return rmaItemsRepository.findByRmaId(rmaId).stream()
                .filter(item -> ids.contains(item.getRmaItemId()))
                .toList();
    }

    private RmaNotice done(String doneKey, List<RMAItem> selected, RMAManager.OperationResult op, Locale locale) {
        List<String> processed = op.isSuccess()
                ? op.getRmaItems().stream().map(RMAItem::getRmaItemId).toList()
                : List.of();
        return new RmaActionNotices(messageSource).done(doneKey, selected, processed, locale);
    }

    private String redirectWithMissingCondition(String rmaId, RedirectAttributes redirectAttributes, Locale locale) {
        redirectAttributes.addFlashAttribute("errorMessage",
                messageSource.getMessage("rma.item.condition.required", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    private Optional<String> rejectIfClosedOrMissing(RMA rma, String rmaId, RedirectAttributes redirectAttributes, Locale locale) {
        return closedOrMissing(rma, rmaId, redirectAttributes, messageSource, locale);
    }

    /** The single refusal for an RMA that does not exist or is closed; shared with the shipping controller. */
    static Optional<String> closedOrMissing(RMA rma, String rmaId, RedirectAttributes redirectAttributes, MessageSource messageSource, Locale locale) {
        if (rma == null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.not.found", null, locale));
            return Optional.of("redirect:/dashboard/rma");
        }
        if (isClosed(rma)) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.already.closed", null, locale));
            return Optional.of("redirect:/dashboard/rma/" + rmaId);
        }
        return Optional.empty();
    }

    private static boolean isClosed(RMA rma) {
        return rma.getStatus() != null && rma.getStatus().isClosed();
    }

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsReturnedToClient")
    public String markItemsAsReturnedToClient(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<RMAItem> selected = selectedItems(rmaId, form);
        RMAManager.OperationResult op = rmaManager.markItemsAsReturnedToClient(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.mark.as.returned.to.client.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        RmaFlash.items(redirectAttributes, done("rma.detail.done.returnedToClient", selected, op, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/markItemsAsSentToDistributor")
    public String markItemsAsSentToDistributor(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<RMAItem> selected = selectedItems(rmaId, form);
        RMAManager.OperationResult op = rmaManager.markItemsAsSentToDistributor(getStoreId(), rmaId, form.getSelectedRMAItemIds());

        if (op.isFailure()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.cannot.mark.as.sent.to.distributor.invalid.status", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        RmaFlash.items(redirectAttributes, done("rma.detail.done.sentToDistributor", selected, op, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/updateShippingDetails")
    public String updateShippingDetails(@PathVariable String rmaId, @ModelAttribute("rma") RMA updatedRma,
                                        @RequestParam(name = RmaAddressPage.SHIP_TO_CLIENT_PARAM, required = false) boolean shipToClient,
                                        Model model, RedirectAttributes redirectAttributes, Locale locale) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(existingRma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        RmaAddressForm form = RmaAddressForm.of(updatedRma.getShippingDetails());
        Map<String, String> errors = form.validate(existingRma.getShippingDetails());
        if (!errors.isEmpty()) {
            return addressPage(existingRma, form.withErrors(errors), orderAddressOf(existingRma), false, false, shipToClient, model);
        }
        existingRma.setShippingDetails(form.applyTo(existingRma.getShippingDetails()));
        rmaRepository.save(existingRma);
        // the item selection was lost on the way here from "Ship to client": the details page is where it is made again
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage(shipToClient ? "rma.address.saved.ship.again" : "rma.address.saved", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    private String addressPage(RMA rma, RmaAddressForm form, ShippingDetails orderAddress, boolean filled,
                               boolean incomplete, boolean shipToClient, Model model) {
        model.addAttribute("page", RmaAddressPage.of(rma, form, orderAddress, filled, incomplete, shipToClient));
        return "rma/address";
    }

    /** The order's shipping address when the order exists and has one, else null. */
    private ShippingDetails orderAddressOf(RMA rma) {
        if (StringUtils.isBlank(rma.getOrderId())) {
            return null;
        }
        Order order = ordersRepository.findById(getStoreId(), rma.getOrderId());
        return order != null && RmaAddressForm.hasAnyValue(order.getShippingDetails()) ? order.getShippingDetails() : null;
    }

    @PostMapping("/dashboard/rma/{rmaId}/updateShipments")
    public String updateShipments(@PathVariable String rmaId, @ModelAttribute("rma") RMA updatedRma,
                                  @RequestParam(name = "shownPackages", required = false) List<String> shownPackages,
                                  RedirectAttributes redirectAttributes, Locale locale) {
        RMA existingRma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(existingRma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        if (updatedRma.getShipments() != null) {
            List<Shipment> previous = existingRma.getShipments();
            // the form carries only what the operator edits and the package id: the courier order, its cancellation
            // and pickup are taken from the shipment of that package, never from the form
            Map<Shipment, String> packages = new IdentityHashMap<>();
            // what the form does not show (tracking subscription, seconds of a date) comes from the stored shipment:
            // the one of the posted package or, for a row without a package, the one at the row's index (the form
            // numbers rows by their position in the stored list, rows being created included)
            Map<Shipment, Shipment> stored = new IdentityHashMap<>();
            List<Shipment> posted = updatedRma.getShipments();
            for (int i = 0; i < posted.size(); i++) {
                Shipment row = posted.get(i);
                Shipment match = shipmentOfPackage(previous, row.getExternalId());
                if (match == null && row.getExternalId() == null && i < previous.size()
                        && previous.get(i).getExternalId() == null && previous.get(i).getCreation() == null) {
                    match = previous.get(i);
                }
                stored.put(row, match);
                packages.put(row, row.getExternalId());
                row.setExternalId(null);
            }
            List<Shipment> shipments = updatedRma.getShipments().stream()
                    .filter(s -> s.hasShippingData() || s.hasCollectionData())
                    .collect(Collectors.toList());
            shipments.forEach(s -> s.inheritCourierOrderFrom(shipmentOfPackage(previous, packages.get(s))));
            shipments.forEach(s -> s.inheritTrackingFrom(stored.get(s)));
            // a shipment being created has no data the form could show; dropping it would lose its label
            List<Shipment> creations = previous.stream().filter(s -> s.getCreation() != null).toList();
            // nor could the form show a package created after it was opened: only packages it showed may be removed
            Set<String> shown = new HashSet<>(packages.values().stream().filter(Objects::nonNull).toList());
            if (shownPackages != null) {
                shown.addAll(shownPackages);
            }
            List<Shipment> createdMeanwhile = previous.stream()
                    .filter(s -> s.getCreation() == null && s.getExternalId() != null && !shown.contains(s.getExternalId()))
                    .toList();

            if (shipments.isEmpty() && creations.isEmpty() && createdMeanwhile.isEmpty()) {
                shipments.add(updatedRma.getShipments().get(0));
            }
            shipments.addAll(createdMeanwhile);
            shipments.addAll(creations);

            existingRma.setShipments(shipments);
        }
        rmaRepository.save(existingRma);
        return "redirect:/dashboard/rma/" + rmaId;
    }

    private static Shipment shipmentOfPackage(List<Shipment> shipments, String externalId) {
        return externalId == null ? null
                : shipments.stream().filter(s -> externalId.equals(s.getExternalId())).findFirst().orElse(null);
    }

    @PostMapping("/dashboard/rma/{rmaId}/items/{rmaItemId}/split")
    public String splitRmaItem(@PathVariable("rmaId") String rmaId,
                               @PathVariable("rmaItemId") String rmaItemId,
                               @RequestParam("qty1") int qty1,
                               @RequestParam("qty2") int qty2,
                               RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }
        RMAItem original = rmaItemsRepository.findById(rmaId, rmaItemId);

        if (original == null) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.detail.error.item.not.found", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        int originalQty = original.getQty();

        if (qty1 < 1 || qty2 < 1 || qty1 + qty2 != originalQty) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.detail.error.split.quantities", null, locale));
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
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<String> selectedRMAItemIds = form.getSelectedRMAItemIds();

        if (selectedRMAItemIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.check.serial.no.items.selected", null, locale));
            return "redirect:/dashboard/rma/" + rmaId;
        }

        // the form never carries serial numbers, so the check reads the stored items
        List<String> problems = new ArrayList<>();

        for (String rmaItemId : selectedRMAItemIds) {
            RMAItem rmaItem = rmaItemsRepository.findById(rmaId, rmaItemId);
            if (rmaItem == null || SerialNumbers.parse(rmaItem.getSerialNo()).isEmpty()) {
                continue;
            }

            OrderItem orderItem = rma.getOrderId() == null ? null
                    : orderItemsRepository.findById(rma.getOrderId(), rmaItem.getItemId());
            if (orderItem == null) {
                problems.add(messageSource.getMessage("rma.check.serial.order.item.not.found",
                        new Object[]{rmaItem.getName()}, locale));
                continue;
            }

            // same rule as the item page hint: an order item without numbers leaves nothing to compare against
            List<String> inOrder = SerialNumbers.parse(orderItem.getSerialNo());
            List<String> outside = inOrder.isEmpty() ? List.of()
                    : SerialNumbers.parse(rmaItem.getSerialNo()).stream().filter(sn -> !inOrder.contains(sn)).toList();
            if (!outside.isEmpty()) {
                problems.add(rmaItem.getName() + " \u2014 " + String.join(", ", outside));
            }
        }

        RmaNotice notice = problems.isEmpty()
                ? new RmaNotice(RmaNotice.OK, messageSource.getMessage("rma.detail.serial.ok", null, locale),
                        List.of(), RmaNotice.Scope.ITEMS)
                : new RmaNotice(RmaNotice.BAD, messageSource.getMessage("rma.detail.serial.mismatch.title", null, locale),
                        List.copyOf(problems), RmaNotice.Scope.ITEMS);
        RmaFlash.items(redirectAttributes, notice);

        return "redirect:/dashboard/rma/" + rmaId;
    }

    @PostMapping("/dashboard/rma/{rmaId}/deleteItems")
    public String deleteRMAItems(@PathVariable String rmaId, @ModelAttribute RMAItemsForm form, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(getStoreId(), rmaId);
        Optional<String> blocked = rejectIfClosedOrMissing(rma, rmaId, redirectAttributes, locale);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        List<RMAItem> selected = selectedItems(rmaId, form);
        List<RMAItem> rmaItemsToDelete = form.getSelectedRMAItemIds().stream()
                .map(rmaItemId -> rmaItemsRepository.findById(rmaId, rmaItemId))
                .filter(i -> i.hasOneOfTheStatuses(RMAItemStatus.New, RMAItemStatus.Received))
                .collect(Collectors.toList());

        if  (rmaItemsToDelete.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("rma.detail.error.delete.status", null, locale));
            return  "redirect:/dashboard/rma/" + rmaId;
        }

        rmaItemsRepository.delete(rmaItemsToDelete);

        double totalAmount = rmaItemsToDelete.stream().mapToDouble(RMAItem::getTotalPrice).sum();

        rma.decreaseShippingInsurance(totalAmount);
        rmaRepository.save(rma);

        RmaFlash.items(redirectAttributes, new RmaActionNotices(messageSource).done("rma.detail.done.deleted", selected,
                rmaItemsToDelete.stream().map(RMAItem::getRmaItemId).toList(), locale));
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
