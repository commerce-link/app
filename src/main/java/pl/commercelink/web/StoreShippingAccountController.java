package pl.commercelink.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShippingProviderDescriptor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.IntegrationStatus;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Settings › Shipping › Courier account and Carriers. The account (provider and its access details) is chosen once and
 * partly secret, so it has its own page, as the invoicing system does. The carriers page asks the provider for the
 * carriers of the account on every visit: the old page needed a separate "fetch" post and failed with an error page
 * when the call did. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), never from the form.
 */
@Controller
@RequiredArgsConstructor
public class StoreShippingAccountController {

    private static final String ACCOUNT_VIEW = "store-shipping-account";
    private static final String ACCOUNT_FRAGMENT = ACCOUNT_VIEW + " :: accountForm";
    private static final String CARRIERS_VIEW = "store-shipping-carriers";
    private static final String CARRIERS_FRAGMENT = CARRIERS_VIEW + " :: carriersForm";

    private final StoresRepository storesRepository;
    private final ShippingAccounts shippingAccounts;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/shipping/account")
    @PreAuthorize("hasRole('ADMIN')")
    public String account(Model model, Locale locale) {
        return showAccount(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/account")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminAccount(@PathVariable String storeId, Model model, Locale locale) {
        return showAccount(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/account")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveAccount(@ModelAttribute IntegrationSettingsForm form,
                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                              HttpServletRequest request, HttpServletResponse response) {
        return saveAccount(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/account")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveAccount(@PathVariable String storeId, @ModelAttribute IntegrationSettingsForm form,
                                        @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                        Model model, Locale locale, RedirectAttributes redirectAttributes,
                                        HttpServletRequest request, HttpServletResponse response) {
        return saveAccount(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/shipping/account/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/account/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, Model model, Locale locale) {
        return confirmDisconnect(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/account/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/account/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(storeId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/shipping/carriers")
    @PreAuthorize("hasRole('ADMIN')")
    public String carriers(Model model, Locale locale) {
        return showCarriers(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/carriers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCarriers(@PathVariable String storeId, Model model, Locale locale) {
        return showCarriers(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/carriers")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveCarriers(@RequestParam(name = "carrierIds", required = false) List<String> carrierIds,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletRequest request, HttpServletResponse response) {
        return saveCarriers(CustomSecurityContext.getStoreId(), carrierIds, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/carriers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveCarriers(@PathVariable String storeId,
                                         @RequestParam(name = "carrierIds", required = false) List<String> carrierIds,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletRequest request, HttpServletResponse response) {
        return saveCarriers(storeId, carrierIds, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    private String showAccount(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        String providerName = shippingAccounts.current(store);
        IntegrationSettingsForm form = IntegrationSettingsForm.from(providerName, shippingAccounts.storedSettings(store),
                shippingAccounts.fieldsOf(providerName));
        shippingAccounts.fillDefaults(form);
        return renderAccount(store, form, Map.of(), model, locale);
    }

    private String saveAccount(String storeId, IntegrationSettingsForm form, boolean async, Model model, Locale locale,
                               RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        List<ProviderField> fields = shippingAccounts.fieldsOf(form.getProviderName());
        shippingAccounts.fillDefaults(form);
        Map<String, String> errors = form.validate(fields, shippingAccounts.storedSecretKeys(store, form.getProviderName()));
        if (!errors.isEmpty()) {
            String view = renderAccount(store, form, errors, model, locale);
            model.addAttribute("errorLabels", form.errorLabels(fields));
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return ACCOUNT_FRAGMENT;
            }
            return view;
        }
        // Validated above: the old panel switched the store to a provider with missing settings and reported success.
        shippingAccounts.save(store, form.getProviderName(), form.toConfiguration(fields));
        storesRepository.save(store);
        return saved(storeId, "store.shipping.account.saved", async, model, locale, redirectAttributes, request, response,
                () -> renderAccount(store, form, Map.of(), model, locale), ACCOUNT_FRAGMENT);
    }

    private String confirmDisconnect(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        IntegrationStatus status = shippingAccounts.status(store);
        if (!status.chosen()) {
            return "redirect:" + shippingPath(storeId);
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.shipping.account.disconnect.title", new Object[]{status.displayName()}, locale),
                messageSource.getMessage("store.shipping.account.disconnect.message", null, locale),
                messageSource.getMessage("store.shipping.account.disconnect.action", null, locale),
                SettingsPaths.store(storeId, "/shipping/account/disconnect"),
                shippingPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        String providerName = shippingAccounts.current(store);
        if (providerName != null) {
            shippingAccounts.disconnect(store, providerName);
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes,
                    messageSource.getMessage("store.shipping.account.disconnected", null, locale));
        }
        return "redirect:" + shippingPath(storeId);
    }

    private String renderAccount(Store store, IntegrationSettingsForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        List<ShippingProviderDescriptor> providers = shippingAccounts.installed();
        IntegrationStatus status = shippingAccounts.status(store);
        Map<String, String> webhooks = new HashMap<>();
        providers.forEach(provider -> {
            String url = shippingAccounts.webhookUrl(storeId, provider.name());
            if (url != null) {
                webhooks.put(provider.name(), url);
            }
        });
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", Map.of());
        model.addAttribute("providers", providers);
        model.addAttribute("providerKnown", providers.stream().anyMatch(provider -> provider.name().equals(form.getProviderName())));
        model.addAttribute("storedSecretIds", shippingAccounts.storedSecretKeys(store, form.getProviderName()).stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("webhooks", webhooks);
        model.addAttribute("webhookTokenKey", ShippingAccounts.WEBHOOK_TOKEN);
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/shipping/account"));
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage(status.chosen()
                ? "store.shipping.account.change.title" : "store.shipping.account.choose.title", null, locale));
        return ACCOUNT_VIEW;
    }

    private String showCarriers(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        List<String> saved = StoreShippingSettingsController.configurationOf(store).getAuthorizedCarriers().stream()
                .map(AuthorizedCarrier::getId)
                .toList();
        return renderCarriers(store, shippingAccounts.carriers(store), Set.copyOf(saved), model, locale);
    }

    private String saveCarriers(String storeId, List<String> carrierIds, boolean async, Model model, Locale locale,
                                RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Set<String> chosen = carrierIds == null ? Set.of() : Set.copyOf(carrierIds);
        ShippingAccounts.CarrierLookup lookup = shippingAccounts.carriers(store);
        if (lookup.failed() || !shippingAccounts.status(store).configured()) {
            String view = renderCarriers(store, lookup, chosen, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return CARRIERS_FRAGMENT;
            }
            return view;
        }
        ShippingConfiguration configuration = StoreShippingSettingsController.configurationOf(store);
        List<AuthorizedCarrier> authorized = options(lookup.carriers(), configuration.getAuthorizedCarriers(), chosen).stream()
                .filter(CarrierOption::selected)
                .map(option -> new AuthorizedCarrier(option.id(), option.name(), option.displayName()))
                .toList();
        configuration.setAuthorizedCarriers(new ArrayList<>(authorized));
        storesRepository.save(store);
        return saved(storeId, "store.shipping.carriers.saved", async, model, locale, redirectAttributes, request, response,
                () -> renderCarriers(store, lookup, chosen, model, locale), CARRIERS_FRAGMENT);
    }

    private String renderCarriers(Store store, ShippingAccounts.CarrierLookup lookup, Set<String> selected, Model model, Locale locale) {
        String storeId = store.getStoreId();
        IntegrationStatus status = shippingAccounts.status(store);
        List<AuthorizedCarrier> saved = StoreShippingSettingsController.configurationOf(store).getAuthorizedCarriers();
        model.addAttribute("account", status);
        model.addAttribute("lookupError", lookup.error());
        model.addAttribute("options", status.configured() && !lookup.failed() ? options(lookup.carriers(), saved, selected) : List.of());
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/shipping/carriers"));
        model.addAttribute("accountHref", SettingsPaths.store(storeId, "/shipping/account"));
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage("store.shipping.carriers.page.title", null, locale));
        return CARRIERS_VIEW;
    }

    /**
     * The carriers the account offers, then the saved ones it no longer returns: those stay listed (and chosen while
     * ticked) instead of silently disappearing on the next save.
     */
    static List<CarrierOption> options(List<Carrier> available, List<AuthorizedCarrier> saved, Set<String> selected) {
        Map<String, CarrierOption> options = new LinkedHashMap<>();
        available.forEach(carrier -> options.put(carrier.id(),
                new CarrierOption(carrier.id(), carrier.name(), carrier.displayName(), selected.contains(carrier.id()), true)));
        saved.stream()
                .filter(carrier -> !options.containsKey(carrier.getId()))
                .forEach(carrier -> options.put(carrier.getId(), new CarrierOption(carrier.getId(), carrier.getName(),
                        carrier.getDisplayName(), selected.contains(carrier.getId()), false)));
        return List.copyOf(options.values());
    }

    private String saved(String storeId, String messageKey, boolean async, Model model, Locale locale,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response,
                         Runnable render, String fragment) {
        String shippingPath = shippingPath(storeId);
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, shippingPath, message);
            render.run();
            model.addAttribute("redirectTo", shippingPath);
            return fragment;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + shippingPath;
    }

    private static String shippingPath(String storeId) {
        return SettingsPaths.store(storeId, "/shipping");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    /** @param available false for a saved carrier the account no longer offers */
    public record CarrierOption(String id, String name, String displayName, boolean selected, boolean available) {
    }
}
