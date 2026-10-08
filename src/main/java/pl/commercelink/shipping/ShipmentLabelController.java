package pl.commercelink.shipping;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.shipping.api.Label;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * "Pobierz etykietę": the label file through the store's own integration account, or back with the reason. A package
 * of another integration than the store's is refused: its label lives on an account the store has no access to.
 */
@Slf4j
@Controller
@PreAuthorize("!hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ShipmentLabelController {

    private static final String UNAVAILABLE = "shipping.label.unavailable";
    private static final String EMPTY = "shipping.label.empty";

    private final StoresRepository storesRepository;
    private final ShippingService shippingService;
    private final MessageSource messageSource;
    private final ShippingIntegrationNames shippingIntegrationNames;

    @GetMapping("/dashboard/shipping/labels/{provider}/{externalId}")
    public Object label(@PathVariable String provider, @PathVariable String externalId,
                        @RequestParam(required = false) String back, RedirectAttributes redirectAttributes,
                        Locale locale) {
        String safeBack = ShipmentPickupController.safeBack(back);
        Store store = storesRepository.findById(storeId());
        if (!shippingService.supportsLabels(store, provider)) {
            return backWith(messageSource.getMessage(UNAVAILABLE, null, locale), safeBack, redirectAttributes);
        }
        try {
            ShippingProvider shippingProvider = shippingService.providerNamed(store, provider)
                    .orElseThrow(() -> new ShippingUnavailableException(store.getStoreId()));
            Label label = shippingProvider.getLabel(externalId);
            if (label.content() == null || label.content().length == 0) {
                // an empty download looks like a broken printer to the operator; the provider has no label yet
                log.warn("Label of package {} in store {} came back empty", externalId, storeId());
                return backWith(empty(provider, store, locale), safeBack, redirectAttributes);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(label.fileName(), StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType(label.contentType()))
                    .body(label.content());
        } catch (RuntimeException e) {
            log.warn("Label of package {} in store {} could not be downloaded", externalId, storeId(), e);
            // the provider's own answer is shown as it is; the adapter's words (no label yet, no answer) are not
            String message = ProviderErrors.isProviderAnswer(e) ? ProviderErrors.describe(e)
                    : empty(provider, store, locale);
            return backWith(message, safeBack, redirectAttributes);
        }
    }

    private String empty(String provider, Store store, Locale locale) {
        return messageSource.getMessage(EMPTY, new Object[]{shippingIntegrationNames.of(provider, store, locale)}, locale);
    }

    private static String backWith(String message, String back, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + back;
    }

    String storeId() {
        return CustomSecurityContext.getStoreId();
    }
}
