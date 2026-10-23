package pl.commercelink.web.offers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * The offers list (spec docs/active/offers-redesign): the whole page, and its results block for list-page.js, which
 * swaps it in place. Validity is measured with the JVM clock, like Basket.isExpired and the stored dates.
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("!hasRole('SUPER_ADMIN')")
public class OffersListController {

    private final OfferListService service;

    @GetMapping("/dashboard/offers")
    public String offers(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        Optional<String> legacy = OfferListQuery.legacyRedirect(params);
        if (legacy.isPresent()) {
            return "redirect:" + legacy.get();
        }
        addPage(model, params, locale);
        return "offers";
    }

    @GetMapping("/dashboard/offers/list")
    public String offersList(@RequestParam MultiValueMap<String, String> params, Locale locale, Model model) {
        addPage(model, params, locale);
        return "offers :: results";
    }

    private void addPage(Model model, MultiValueMap<String, String> params, Locale locale) {
        model.addAttribute("page", service.page(CustomSecurityContext.getStoreId(), OfferListQuery.parse(params),
                LocalDateTime.now(), locale));
    }
}
