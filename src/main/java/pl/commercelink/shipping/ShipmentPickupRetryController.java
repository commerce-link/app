package pl.commercelink.shipping;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMARepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Locale;

/**
 * "Zamów odbiór ponownie" of a customer's return: its package is picked up at the customer's address, so it is not on
 * the pickup page with the store's packages; the pickup is ordered again at once, the way it was after the creation.
 */
@Slf4j
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ShipmentPickupRetryController {

    private final RMARepository rmaRepository;
    private final StoresRepository storesRepository;
    private final ShippingService shippingService;
    private final ImmediatePickup immediatePickup;
    private final MessageSource messageSource;

    @PostMapping("/dashboard/rma/{rmaId}/shipments/{externalId}/pickup")
    public String orderAgain(@PathVariable String rmaId, @PathVariable String externalId,
                             RedirectAttributes redirectAttributes, Locale locale) {
        RMA rma = rmaRepository.findById(storeId(), rmaId);
        if (rma == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        String back = "redirect:/dashboard/rma/" + rmaId;
        List<Shipment> parcels = rma.getShipments() == null ? List.of() : rma.getShipments().stream()
                .filter(s -> externalId.equals(s.getExternalId())).toList();
        Store store = storesRepository.findById(storeId());
        String provider = parcels.isEmpty() ? null : parcels.get(0).getProvider();
        // only a return still waiting for its pickup, of the store's own integration (whose account created it); a
        // package of the store's pickup list goes through the pickup page, which groups it with the others
        boolean retryable = !parcels.isEmpty() && (rma.getStatus() == null || !rma.getStatus().isClosed())
                && parcels.stream().allMatch(s -> s.awaitsPickup() && s.getPickUpAddressId() == null)
                && provider != null && provider.equals(shippingService.providerName(store));
        if (!retryable) {
            redirectAttributes.addFlashAttribute("errorMessage", message("shipping.pickup.gone", locale));
            return back;
        }
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder()
                .storeId(storeId())
                .ownerType(ShipmentOwnerType.RMA_RETURN)
                .ownerId(rmaId)
                .provider(provider)
                .externalId(externalId)
                .build();
        try {
            immediatePickup.orderFor(request, parcels);
        } catch (RuntimeException e) {
            // the operator sees the reason on the page and can try again
            log.warn("Pickup of the return of RMA {} in store {} (package {}) could not be ordered again", rmaId,
                    storeId(), externalId, e);
            redirectAttributes.addFlashAttribute("errorMessage", ProviderErrors.describe(e));
            return back;
        }
        redirectAttributes.addFlashAttribute("successMessage", message("shipping.pickup.started", locale));
        return back;
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
