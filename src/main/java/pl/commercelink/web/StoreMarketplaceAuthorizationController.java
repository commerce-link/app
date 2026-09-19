package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.rest.client.OAuth2DeviceTokenResult;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.MarketplaceAuthorization.AuthorizationException;
import pl.commercelink.web.MarketplaceAuthorization.Pending;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Settings › Marketplaces › connect the marketplace account (Allegro): a page instead of the old popup opened from the
 * list. "Connect" asks the marketplace for a code; the page shows it with a link to the marketplace's confirmation page
 * and its script asks every few seconds whether the operator confirmed it (without JavaScript: a "Check" button).
 * The code waits in the HTTP session. The old endpoints were ADMIN only and took the store from the session, so a super
 * admin could not connect the account of the store they were looking at.
 */
@Controller
@RequiredArgsConstructor
public class StoreMarketplaceAuthorizationController {

    static final String PENDING_ATTRIBUTE = "marketplaceAuthorization";
    private static final String NAME = "/{name:[A-Za-z0-9_.-]+}";
    private static final String ERROR = "authorizeError";
    private static final String INFO = "authorizeInfo";
    private static final String AUTHORIZED = "AUTHORIZED";
    private static final String PENDING = "PENDING";
    private static final String EXPIRED = "EXPIRED";
    private static final DateTimeFormatter EXPIRES_AT = DateTimeFormatter.ofPattern("HH:mm");

    private final StoresRepository storesRepository;
    private final MarketplaceConnections marketplaces;
    private final MarketplaceAuthorization authorization;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/marketplaces" + NAME + "/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    public String page(@PathVariable String name, HttpSession session, Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), name, session, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/authorize")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminPage(@PathVariable String storeId, @PathVariable String name, HttpSession session,
                                 Model model, Locale locale) {
        return show(storeId, name, session, model, locale);
    }

    @PostMapping("/dashboard/store/marketplaces" + NAME + "/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    public String start(@PathVariable String name, HttpSession session, Locale locale,
                        RedirectAttributes redirectAttributes) {
        return start(CustomSecurityContext.getStoreId(), name, session, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/authorize")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStart(@PathVariable String storeId, @PathVariable String name, HttpSession session,
                                  Locale locale, RedirectAttributes redirectAttributes) {
        return start(storeId, name, session, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/marketplaces" + NAME + "/authorize/check")
    @PreAuthorize("hasRole('ADMIN')")
    public String check(@PathVariable String name, HttpSession session, Locale locale,
                        RedirectAttributes redirectAttributes) {
        return check(CustomSecurityContext.getStoreId(), name, session, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/authorize/check")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCheck(@PathVariable String storeId, @PathVariable String name, HttpSession session,
                                  Locale locale, RedirectAttributes redirectAttributes) {
        return check(storeId, name, session, locale, redirectAttributes);
    }

    /** The same check for the page's script, which asks every few seconds and navigates itself when it is over. */
    @PostMapping("/dashboard/store/marketplaces" + NAME + "/authorize/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String name, HttpSession session, Locale locale,
                                                      HttpServletRequest request, HttpServletResponse response) {
        return status(CustomSecurityContext.getStoreId(), name, session, locale, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/authorize/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> superAdminStatus(@PathVariable String storeId, @PathVariable String name,
                                                                HttpSession session, Locale locale,
                                                                HttpServletRequest request, HttpServletResponse response) {
        return status(storeId, name, session, locale, request, response);
    }

    private String show(String storeId, String name, HttpSession session, Model model, Locale locale) {
        Store store = requireStore(storeId);
        MarketplaceIntegration integration = requireAuthorizable(store, name);
        String displayName = marketplaces.displayName(name);
        String page = authorizePath(storeId, name);
        model.addAttribute("displayName", displayName);
        Pending pending = pending(session, storeId, name);
        model.addAttribute("pending", pending);
        model.addAttribute("expiresAtText", pending == null ? null
                : EXPIRES_AT.format(pending.expiresAt().atZone(ZoneId.of("Europe/Warsaw"))));
        model.addAttribute("connected", integration.isLoggedIn());
        model.addAttribute("startAction", page);
        model.addAttribute("checkAction", page + "/check");
        model.addAttribute("statusAction", page + "/status");
        model.addAttribute("marketplacesHref", marketplacesPath(storeId));
        model.addAttribute("editHref", marketplacesPath(storeId) + "/" + name);
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("store.marketplace.authorize.title",
                new Object[]{displayName}, locale));
        return "store-marketplace-authorize";
    }

    private String start(String storeId, String name, HttpSession session, Locale locale,
                         RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        requireAuthorizable(store, name);
        try {
            session.setAttribute(PENDING_ATTRIBUTE, authorization.start(store, name));
        } catch (AuthorizationException e) {
            session.removeAttribute(PENDING_ATTRIBUTE);
            redirectAttributes.addFlashAttribute(ERROR, messageSource.getMessage(e.messageKey(),
                    new Object[]{marketplaces.displayName(name)}, locale));
        }
        return "redirect:" + authorizePath(storeId, name);
    }

    private String check(String storeId, String name, HttpSession session, Locale locale,
                         RedirectAttributes redirectAttributes) {
        Outcome outcome = checkOnce(storeId, name, session, locale);
        if (outcome.authorized()) {
            SettingsFlash.onRedirect(redirectAttributes, outcome.message());
        } else {
            redirectAttributes.addFlashAttribute(outcome.pending() ? INFO : ERROR, outcome.message());
        }
        return "redirect:" + outcome.next();
    }

    private ResponseEntity<Map<String, Object>> status(String storeId, String name, HttpSession session, Locale locale,
                                                       HttpServletRequest request, HttpServletResponse response) {
        Outcome outcome = checkOnce(storeId, name, session, locale);
        if (outcome.authorized()) {
            SettingsFlash.forNextPage(request, response, outcome.next(), outcome.message());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", outcome.status());
        body.put("message", outcome.message());
        body.put("redirect", outcome.pending() ? null : outcome.next());
        return ResponseEntity.ok(body);
    }

    private Outcome checkOnce(String storeId, String name, HttpSession session, Locale locale) {
        Store store = requireStore(storeId);
        requireAuthorizable(store, name);
        String displayName = marketplaces.displayName(name);
        Pending pending = pending(session, storeId, name);
        String status;
        if (pending == null) {
            status = EXPIRED;
        } else {
            OAuth2DeviceTokenResult.Status result = authorization.check(store, pending);
            status = result == OAuth2DeviceTokenResult.Status.SLOW_DOWN ? PENDING : result.name();
        }
        if (!PENDING.equals(status)) {
            session.removeAttribute(PENDING_ATTRIBUTE);
        }
        String message = switch (status) {
            case AUTHORIZED -> messageSource.getMessage("store.marketplace.authorize.connected", new Object[]{displayName}, locale);
            case PENDING -> messageSource.getMessage("store.marketplace.authorize.pending", new Object[]{displayName}, locale);
            case EXPIRED -> messageSource.getMessage("store.marketplace.authorize.expired", null, locale);
            default -> messageSource.getMessage("store.marketplace.authorize.failed", new Object[]{displayName}, locale);
        };
        String next = AUTHORIZED.equals(status) ? marketplacesPath(storeId) : authorizePath(storeId, name);
        return new Outcome(status, message, next);
    }

    private record Outcome(String status, String message, String next) {

        boolean authorized() {
            return AUTHORIZED.equals(status);
        }

        boolean pending() {
            return PENDING.equals(status);
        }
    }

    private static Pending pending(HttpSession session, String storeId, String name) {
        Object stored = session.getAttribute(PENDING_ATTRIBUTE);
        if (stored instanceof Pending pending && pending.belongsTo(storeId, name) && !pending.expired()) {
            return pending;
        }
        return null;
    }

    private MarketplaceIntegration requireAuthorizable(Store store, String name) {
        MarketplaceIntegration integration = store.getMarketplaceIntegration(name);
        if (integration == null || !marketplaces.authorizedOnMarketplace(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return integration;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private static String marketplacesPath(String storeId) {
        return SettingsPaths.store(storeId, "/marketplaces");
    }

    private static String authorizePath(String storeId, String name) {
        return marketplacesPath(storeId) + "/" + name + "/authorize";
    }
}
