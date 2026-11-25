package pl.commercelink.web.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderShipmentForm;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * The deliveries list (spec docs/active/dostawy-redesign): the whole page, and its results block for the list script,
 * which swaps it in place. A store user sees their store; the super admin sees the GLOBAL deliveries of every store.
 */
@Controller
@RequiredArgsConstructor
public class DeliveriesListController {

    private final DeliveryListService service;
    private final StoresRepository stores;

    @GetMapping("/dashboard/deliveries")
    public String deliveries(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = DeliveryListQuery.legacyRedirect(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        addPage(model, params, locale);
        if (isStoreAdmin()) {
            model.addAttribute("canAutoSyncInvoices", true);
            model.addAttribute("invoicingConnected", invoicingConnected());
        }
        return "deliveries";
    }

    @GetMapping("/dashboard/deliveries/list")
    public String deliveriesList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, params, locale);
        return "deliveries :: results";
    }

    /** The super admin's list spans stores and has no backlog of its own to sync. */
    private static boolean isStoreAdmin() {
        return CustomSecurityContext.hasRole("ADMIN") && !CustomSecurityContext.hasRole("SUPER_ADMIN");
    }

    private boolean invoicingConnected() {
        Store store = stores.findById(CustomSecurityContext.getStoreId());
        return store != null && store.hasIntegration(IntegrationType.INVOICING_PROVIDER);
    }

    private void addPage(Model model, MultiValueMap<String, String> params, Locale locale) {
        boolean superAdmin = CustomSecurityContext.hasRole("SUPER_ADMIN");
        DeliveryListService.ListActor actor = new DeliveryListService.ListActor(
                superAdmin ? null : CustomSecurityContext.getStoreId(), superAdmin, CustomSecurityContext.hasRole("ADMIN"));
        model.addAttribute("page", service.page(actor, DeliveryListQuery.parse(params), LocalDate.now(OrderShipmentForm.OPERATOR_ZONE), locale));
    }
}
