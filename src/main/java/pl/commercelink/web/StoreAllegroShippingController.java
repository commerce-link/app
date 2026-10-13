package pl.commercelink.web;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.shipping.ShippingProviders;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.List;
import java.util.Locale;

/**
 * Settings › Shipping › Wysyłam z Allegro. No login of its own: the page asks Allegro, through the marketplace
 * connection it borrows, whether the store may ship, and keeps only the label format. The store comes from the
 * session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
public class StoreAllegroShippingController {

    private static final String VIEW = "store-shipping-allegro";

    private final StoresRepository storesRepository;
    private final AllegroShippingSettings allegroShippingSettings;
    private final MessageSource messageSource;

    public StoreAllegroShippingController(StoresRepository storesRepository, AllegroShippingSettings allegroShippingSettings,
                                          MessageSource messageSource) {
        this.storesRepository = storesRepository;
        this.allegroShippingSettings = allegroShippingSettings;
        this.messageSource = messageSource;
    }

    @GetMapping("/dashboard/store/shipping/allegro")
    @PreAuthorize("hasRole('ADMIN')")
    public String page(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/allegro")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminPage(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/allegro")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@RequestParam(name = "labelFormat", required = false) String labelFormat, Model model,
                       Locale locale, RedirectAttributes redirectAttributes) {
        return save(CustomSecurityContext.getStoreId(), labelFormat, model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/allegro")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSave(@PathVariable String storeId,
                                 @RequestParam(name = "labelFormat", required = false) String labelFormat, Model model,
                                 Locale locale, RedirectAttributes redirectAttributes) {
        return save(storeId, labelFormat, model, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/shipping/allegro/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/allegro/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, Model model, Locale locale) {
        return confirmDisconnect(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/allegro/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/allegro/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(storeId, locale, redirectAttributes);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        return render(store, allegroShippingSettings.status(store), allegroShippingSettings.labelFormat(store), null,
                model, locale);
    }

    private String save(String storeId, String labelFormat, Model model, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        AllegroShippingLabelFormat format = AllegroShippingLabelFormat.of(labelFormat);
        boolean wasEnabled = store.hasShippingIntegration(ShippingProviders.ALLEGRO);
        try {
            allegroShippingSettings.enable(store, format);
        } catch (IllegalStateException e) {
            // the status said why on the page; it is asked again so the page shows the current answer
            return render(store, allegroShippingSettings.status(store), format,
                    messageSource.getMessage("shipping.allegro.enable.refused", null, locale), model, locale);
        }
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage(
                wasEnabled ? "shipping.allegro.saved" : "shipping.allegro.enabled", null, locale));
        return "redirect:" + shippingPath(storeId);
    }

    private String confirmDisconnect(String storeId, Model model, Locale locale) {
        requireStore(storeId);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("shipping.allegro.disconnect.title", null, locale),
                messageSource.getMessage("shipping.allegro.disconnect.message", null, locale),
                messageSource.getMessage("shipping.allegro.disconnect.action", null, locale),
                SettingsPaths.store(storeId, "/shipping/allegro/disconnect"),
                shippingPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        allegroShippingSettings.disable(store);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("shipping.allegro.disconnected", null, locale));
        return "redirect:" + shippingPath(storeId);
    }

    private String render(Store store, AllegroShippingStatus status, AllegroShippingLabelFormat labelFormat,
                          String errorMessage, Model model, Locale locale) {
        String storeId = store.getStoreId();
        model.addAttribute("status", status);
        model.addAttribute("labelFormat", labelFormat);
        model.addAttribute("labelFormats", List.of(AllegroShippingLabelFormat.values()));
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/shipping/allegro"));
        model.addAttribute("pageHref", SettingsPaths.store(storeId, "/shipping/allegro"));
        model.addAttribute("reconnectHref", SettingsPaths.store(storeId, "/marketplaces/Allegro/authorize"));
        model.addAttribute("marketplacesHref", SettingsPaths.store(storeId, "/marketplaces"));
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("shipping.allegro.name", null, locale));
        return VIEW;
    }

    private Store requireStore(String storeId) {
        if (!allegroShippingSettings.installed()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private static String shippingPath(String storeId) {
        return SettingsPaths.store(storeId, "/shipping");
    }
}
