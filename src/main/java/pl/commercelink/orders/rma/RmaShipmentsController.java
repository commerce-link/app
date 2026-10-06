package pl.commercelink.orders.rma;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
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
import pl.commercelink.shipping.ShipmentsState;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** The shipments of an RMA created through an integration: the state its page polls and dropping a failed creation. */
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RmaShipmentsController {

    private final RMARepository rmaRepository;
    private final OptimisticLockingExecutor optimisticLockingExecutor;
    private final MessageSource messageSource;

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

    String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
