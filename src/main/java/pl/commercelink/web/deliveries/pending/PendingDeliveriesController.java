package pl.commercelink.web.deliveries.pending;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.orders.OrderShipmentForm;

import java.time.LocalDate;
import java.util.Locale;

/** The pending deliveries page (spec §4): the store's own at /dashboard, any store's for the super admin. */
@Controller
@RequiredArgsConstructor
public class PendingDeliveriesController {

    private static final String VIEW = "deliveries/pending";
    private static final String FRAGMENT = "deliveries/pending :: results";

    private final PendingDeliveriesService service;

    @GetMapping("/dashboard/deliveries/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public String pending(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, CustomSecurityContext.getStoreId(), false, params, locale);
        return VIEW;
    }

    @GetMapping("/dashboard/deliveries/preview/fragment")
    @PreAuthorize("hasRole('ADMIN')")
    public String pendingFragment(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, CustomSecurityContext.getStoreId(), false, params, locale);
        return FRAGMENT;
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/preview")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String pendingForSuperAdmin(@PathVariable("storeId") String storeId,
                                       @RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, storeId, true, params, locale);
        return VIEW;
    }

    @GetMapping("/dashboard/store/{storeId}/deliveries/preview/fragment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String pendingFragmentForSuperAdmin(@PathVariable("storeId") String storeId,
                                               @RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, storeId, true, params, locale);
        return FRAGMENT;
    }

    private void addPage(Model model, String storeId, boolean superAdmin, MultiValueMap<String, String> params, Locale locale) {
        model.addAttribute("page", service.page(storeId, superAdmin, PendingDeliveriesQuery.parse(params),
                LocalDate.now(OrderShipmentForm.OPERATOR_ZONE), locale));
    }
}
