package pl.commercelink.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.marketplace.offermatching.MarketplaceOfferMatchPublisher;
import pl.commercelink.marketplace.offermatching.OfferMatchResult;
import pl.commercelink.marketplace.offermatching.OfferMatchResultStore;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.OfferMatchingCsv;
import pl.commercelink.web.settings.OfferMatchingRunView;
import pl.commercelink.web.settings.OfferMatchingView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

/**
 * Settings › Marketplaces › "Dopasuj oferty": links the offers another system published on the marketplace to the
 * store's products by EAN. The run is ordered, not done in the request (thousands of offers over many API pages, which a
 * marketplace may hand out only a few a minute): the page shows the latest saved result and, while a run goes on, how far
 * it got, or why the last one failed; a refresh picks up the new state. A second run is refused while one is going on.
 * Only for a connected marketplace whose adapter can list the store's offers. A run the worker would skip (expired
 * connection, inactive store) is not ordered at all, so the page never promises a result that cannot come.
 */
@Controller
public class MarketplaceOfferMatchController {

    private static final String VIEW = "store-marketplace-offer-matching";
    private static final String BLOCKED_STORE = "store.marketplaces.offer.matching.blocked.store";
    private static final String BLOCKED_INTEGRATION = "store.marketplaces.offer.matching.blocked.integration";
    private static final String RUNNING = "store.marketplaces.offer.matching.running.refused";
    private static final String MARKETPLACE_PATH = "/{marketplace:[A-Za-z0-9_.-]+}/offer-matching";
    private static final String GROUP_CSV_PATH = "/{group:linked|toReview}.csv";

    private final StoresRepository storesRepository;
    private final MarketplaceConnections marketplaces;
    private final MarketplaceOfferMatchPublisher publisher;
    private final OfferMatchResultStore resultStore;
    private final StoreActivity storeActivity;
    private final MessageSource messageSource;
    private final Clock clock;

    @Autowired
    public MarketplaceOfferMatchController(StoresRepository storesRepository, MarketplaceConnections marketplaces,
                                           MarketplaceOfferMatchPublisher publisher, OfferMatchResultStore resultStore,
                                           StoreActivity storeActivity, MessageSource messageSource) {
        this(storesRepository, marketplaces, publisher, resultStore, storeActivity, messageSource, Clock.systemUTC());
    }

    MarketplaceOfferMatchController(StoresRepository storesRepository, MarketplaceConnections marketplaces,
                                    MarketplaceOfferMatchPublisher publisher, OfferMatchResultStore resultStore,
                                    StoreActivity storeActivity, MessageSource messageSource, Clock clock) {
        this.storesRepository = storesRepository;
        this.marketplaces = marketplaces;
        this.publisher = publisher;
        this.resultStore = resultStore;
        this.storeActivity = storeActivity;
        this.messageSource = messageSource;
        this.clock = clock;
    }

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

    @GetMapping("/dashboard/store/marketplaces" + MARKETPLACE_PATH + GROUP_CSV_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ByteArrayResource> groupCsv(@PathVariable String marketplace, @PathVariable String group,
                                                      Locale locale) {
        return csv(CustomSecurityContext.getStoreId(), marketplace, group, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces" + MARKETPLACE_PATH + GROUP_CSV_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ByteArrayResource> superAdminGroupCsv(@PathVariable String storeId,
                                                                @PathVariable String marketplace,
                                                                @PathVariable String group, Locale locale) {
        return csv(storeId, marketplace, group, locale);
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
        Store store = requireOfferListing(storeId, marketplace);
        String displayName = marketplaces.displayName(marketplace);
        String blockedKey = runBlockedKey(store, marketplace);
        if (blockedKey != null) {
            model.addAttribute("runBlockedMessage", messageSource.getMessage(blockedKey, new Object[]{displayName}, locale));
            model.addAttribute("runBlockedHref", BLOCKED_INTEGRATION.equals(blockedKey)
                    ? SettingsPaths.store(storeId, "/marketplaces/" + marketplace) : null);
        }
        model.addAttribute("matching", resultStore.latest(storeId, marketplace).map(OfferMatchingView::of).orElse(null));
        model.addAttribute("run", resultStore.progress(storeId, marketplace)
                .map(progress -> OfferMatchingRunView.of(progress, clock.instant())).orElse(null));
        model.addAttribute("displayName", displayName);
        model.addAttribute("formAction", pagePath(storeId, marketplace));
        model.addAttribute("csvHrefBase", pagePath(storeId, marketplace));
        model.addAttribute("marketplacesHref", SettingsPaths.store(storeId, "/marketplaces"));
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("store.marketplaces.offer.matching.title",
                new Object[]{displayName}, locale));
        model.addAttribute("storeId", storeId);
        return VIEW;
    }

    private ResponseEntity<ByteArrayResource> csv(String storeId, String marketplace, String group, Locale locale) {
        requireOfferListing(storeId, marketplace);
        OfferMatchResult result = resultStore.latest(storeId, marketplace)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] csv = OfferMatchingCsv.of(result, group, messageSource, locale)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String filename = "offer-matching-" + marketplace + "-" + group + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(csv));
    }

    private String order(String storeId, String marketplace, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireOfferListing(storeId, marketplace);
        String blockedKey = runBlockedKey(store, marketplace);
        if (blockedKey != null) {
            SettingsFlash.errorOnRedirect(redirectAttributes, messageSource.getMessage(blockedKey,
                    new Object[]{marketplaces.displayName(marketplace)}, locale));
            return "redirect:" + pagePath(storeId, marketplace);
        }
        if (resultStore.progress(storeId, marketplace).filter(progress -> progress.isRunning(clock.instant())).isPresent()) {
            SettingsFlash.errorOnRedirect(redirectAttributes, messageSource.getMessage(RUNNING,
                    new Object[]{marketplaces.displayName(marketplace)}, locale));
            return "redirect:" + pagePath(storeId, marketplace);
        }
        publisher.order(storeId, marketplace);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.marketplaces.offer.matching.ordered",
                new Object[]{marketplaces.displayName(marketplace)}, locale));
        return "redirect:" + pagePath(storeId, marketplace);
    }

    /** The same checks as MarketplaceOfferMatchListener, which skips such a run without a trace. */
    private String runBlockedKey(Store store, String marketplace) {
        if (!storeActivity.isActive(store)) {
            return BLOCKED_STORE;
        }
        return store.hasActiveMarketplaceIntegration(marketplace) ? null : BLOCKED_INTEGRATION;
    }

    private Store requireOfferListing(String storeId, String marketplace) {
        Store store = storesRepository.findById(storeId);
        MarketplaceProviderDescriptor descriptor = marketplaces.descriptor(marketplace);
        if (store == null || store.getMarketplaceIntegration(marketplace) == null
                || descriptor == null || !descriptor.supportsOfferListing()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private static String pagePath(String storeId, String marketplace) {
        return SettingsPaths.store(storeId, "/marketplaces/" + marketplace + "/offer-matching");
    }
}
