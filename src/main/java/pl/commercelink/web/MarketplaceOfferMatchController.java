package pl.commercelink.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.marketplace.links.MarketplaceOfferMatchPublisher;
import pl.commercelink.marketplace.links.OfferMatchResultStore;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.OfferMatchingView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;

/**
 * Settings › Marketplaces › "Dopasuj oferty": links the offers another system published on the marketplace to the
 * store's products by EAN. The run is ordered, not done in the request (thousands of offers over many API pages), and
 * nothing tracks it: the page shows the latest saved result, which a refresh picks up once the worker is done. Only for a
 * connected marketplace whose adapter can list the store's offers.
 */
@Controller
@RequiredArgsConstructor
public class MarketplaceOfferMatchController {

    private static final String VIEW = "store-marketplace-offer-matching";
    private static final String MARKETPLACE_PATH = "/{marketplace:[A-Za-z0-9_.-]+}/offer-matching";

    private final StoresRepository storesRepository;
    private final MarketplaceConnections marketplaces;
    private final MarketplaceOfferMatchPublisher publisher;
    private final OfferMatchResultStore resultStore;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/marketplaces" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String offerMatching(@PathVariable String marketplace, Model model, Locale locale) {
        return render(CustomSecurityContext.getStoreId(), marketplace, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminOfferMatching(@PathVariable String storeId, @PathVariable String marketplace, Model model,
                                          Locale locale) {
        return render(storeId, marketplace, model, locale);
    }

    @PostMapping("/dashboard/store/marketplaces" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String orderOfferMatching(@PathVariable String marketplace, Locale locale,
                                     RedirectAttributes redirectAttributes) {
        return order(CustomSecurityContext.getStoreId(), marketplace, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + MARKETPLACE_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminOrderOfferMatching(@PathVariable String storeId, @PathVariable String marketplace,
                                               Locale locale, RedirectAttributes redirectAttributes) {
        return order(storeId, marketplace, locale, redirectAttributes);
    }

    private String render(String storeId, String marketplace, Model model, Locale locale) {
        requireOfferListing(storeId, marketplace);
        String displayName = marketplaces.displayName(marketplace);
        model.addAttribute("matching", resultStore.latest(storeId, marketplace).map(OfferMatchingView::of).orElse(null));
        model.addAttribute("displayName", displayName);
        model.addAttribute("formAction", pagePath(storeId, marketplace));
        model.addAttribute("marketplacesHref", SettingsPaths.store(storeId, "/marketplaces"));
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("store.marketplaces.offer.matching.title",
                new Object[]{displayName}, locale));
        model.addAttribute("storeId", storeId);
        return VIEW;
    }

    private String order(String storeId, String marketplace, Locale locale, RedirectAttributes redirectAttributes) {
        requireOfferListing(storeId, marketplace);
        publisher.order(storeId, marketplace);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.marketplaces.offer.matching.ordered",
                new Object[]{marketplaces.displayName(marketplace)}, locale));
        return "redirect:" + pagePath(storeId, marketplace);
    }

    private void requireOfferListing(String storeId, String marketplace) {
        Store store = storesRepository.findById(storeId);
        MarketplaceProviderDescriptor descriptor = marketplaces.descriptor(marketplace);
        if (store == null || store.getMarketplaceIntegration(marketplace) == null
                || descriptor == null || !descriptor.supportsOfferListing()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static String pagePath(String storeId, String marketplace) {
        return SettingsPaths.store(storeId, "/marketplaces/" + marketplace + "/offer-matching");
    }
}
