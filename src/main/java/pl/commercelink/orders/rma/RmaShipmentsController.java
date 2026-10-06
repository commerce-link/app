package pl.commercelink.orders.rma;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.shipping.ShipmentCreationStart;
import pl.commercelink.shipping.ShipmentsState;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shipments of an RMA created through an integration: the state its page polls, dropping a failed creation and
 * booking a customer's failed return again.
 */
@Slf4j
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RmaShipmentsController {

    private final RMARepository rmaRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final MessageSource messageSource;
    private final RMAShippingService rmaShippingService;
    private final StoresRepository storesRepository;

    /** Whether a shipment of the RMA still waits for the provider; rma-detail.html polls it (shipment-cancellation.js). */
    @GetMapping("/dashboard/rma/{rmaId}/shipments/state")
    @ResponseBody
    public ResponseEntity<ShipmentsState> shipmentsState(@PathVariable String rmaId) {
        RMA rma = rmaRepository.findById(storeId(), rmaId);
        if (rma == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean inProgress = rma.getShipments() != null && rma.getShipments().stream().anyMatch(s -> s.awaitsProviderAnswer(now));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ShipmentsState(inProgress));
    }

    /**
     * Drops the record of a creation that failed; the shipment form keeps such rows (it cannot show them), so this is
     * their only way out. A package the provider may still hold is settled in its panel, as on the order.
     */
    @PostMapping("/dashboard/rma/{rmaId}/shipments/creations/{commandId}/remove")
    public String removeFailedCreation(@PathVariable String rmaId, @PathVariable String commandId,
                                       RedirectAttributes redirectAttributes, Locale locale) {
        AtomicBoolean removed = new AtomicBoolean();
        optimisticLockingExecutor.modifyAndSave(
                () -> rmaRepository.findById(storeId(), rmaId),
                fresh -> removed.set(fresh != null && fresh.getShipments() != null
                        && (fresh.getStatus() == null || !fresh.getStatus().isClosed())
                        && fresh.getShipments().removeIf(s -> s.creationFailed() && s.getCreation().hasCommand(commandId))),
                fresh -> {
                    if (removed.get()) {
                        rmaRepository.save(fresh);
                    }
                });
        redirectAttributes.addFlashAttribute(removed.get() ? "successMessage" : "errorMessage",
                messageSource.getMessage(removed.get() ? "rma.shipments.removed" : "rma.shipments.remove.gone", null, locale));
        return "redirect:/dashboard/rma/" + rmaId;
    }

    /**
     * "Spróbuj ponownie" of a customer's return that failed to be created: booked again, as a new command, with the
     * address, package template and insurance the customer's submission left on the RMA. The new placeholder replaces
     * the failed row (RmaShipmentOwner#markCreating); a refusal leaves a failed row again, so it can be retried.
     */
    @PostMapping("/dashboard/rma/{rmaId}/return-shipment/retry")
    public String retryReturnShipment(@PathVariable String rmaId, RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(storeId(), rmaId);
        if (rma == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String back = "redirect:/dashboard/rma/" + rmaId;
        if (!CustomerReturnRetry.possible(rma)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.shipments.return.retry.unavailable", null, locale));
            return back;
        }
        RMAShipmentRequest request = new RMAShipmentRequest(rmaId, rma.getReturnPackageTemplateId(),
                rma.getShippingDetails(), rma.getShippingInsurance());
        ShipmentCreationStart start;
        try {
            start = rmaShippingService.startReturnShipment(request, storesRepository.findById(storeId()));
        } catch (RuntimeException e) {
            // no command was sent (no carrier or template in the settings any more, no integration): the row stays
            log.warn("Return of RMA {} in store {} could not be booked again", rmaId, storeId(), e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return back;
        }
        switch (start.outcome()) {
            case STARTED -> redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("shipping.create.started", null, locale));
            case REFUSED -> redirectAttributes.addFlashAttribute("errorMessage", start.error());
            case GONE -> redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage("rma.shipments.return.retry.gone", null, locale));
        }
        return back;
    }

    String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
