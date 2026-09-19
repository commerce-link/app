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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.marketplace.MarketplaceConnectionService.ConnectionUpdateResult;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.dtos.MarketplaceSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.ProviderFieldText;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings › Marketplaces › a marketplace: its access details and import schedules on its own page, and disconnecting it.
 * Replaces the modal of the old list, whose save the list swapped in by script and whose errors came as browser alerts.
 * A marketplace whose account is connected on the marketplace's page (Allegro) goes on to the authorization page after
 * the first save. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), the marketplace from the path.
 */
@Controller
@RequiredArgsConstructor
public class StoreMarketplaceController {

    private static final String VIEW = "store-marketplace";
    private static final String FORM_FRAGMENT = VIEW + " :: marketplaceForm";
    private static final String NAME = "/{name:[A-Za-z0-9_.-]+}";

    private final StoresRepository storesRepository;
    private final MarketplaceConnections marketplaces;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/marketplaces/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newMarketplace(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewMarketplace(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/marketplaces/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String addMarketplace(@ModelAttribute MarketplaceSettingsForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), null, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminAddMarketplace(@PathVariable String storeId, @ModelAttribute MarketplaceSettingsForm form,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, null, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/dashboard/store/marketplaces" + NAME)
    @PreAuthorize("hasRole('ADMIN')")
    public String editMarketplace(@PathVariable String name, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), name, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces" + NAME)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditMarketplace(@PathVariable String storeId, @PathVariable String name, Model model,
                                            Locale locale) {
        return showEdit(storeId, name, model, locale);
    }

    @PostMapping("/dashboard/store/marketplaces" + NAME)
    @PreAuthorize("hasRole('ADMIN')")
    public String updateMarketplace(@PathVariable String name, @ModelAttribute MarketplaceSettingsForm form,
                                    @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                    Model model, Locale locale, RedirectAttributes redirectAttributes,
                                    HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), name, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + NAME)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateMarketplace(@PathVariable String storeId, @PathVariable String name,
                                              @ModelAttribute MarketplaceSettingsForm form,
                                              @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                              Model model, Locale locale, RedirectAttributes redirectAttributes,
                                              HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, name, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/dashboard/store/marketplaces" + NAME + "/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(@PathVariable String name, Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), name, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, @PathVariable String name, Model model,
                                              Locale locale) {
        return confirmDisconnect(storeId, name, model, locale);
    }

    @PostMapping("/dashboard/store/marketplaces" + NAME + "/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(@PathVariable String name, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), name, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/marketplaces" + NAME + "/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, @PathVariable String name, Locale locale,
                                       RedirectAttributes redirectAttributes) {
        return disconnect(storeId, name, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        List<MarketplaceProviderDescriptor> addable = marketplaces.addable(store);
        if (addable.isEmpty()) {
            return "redirect:" + marketplacesPath(storeId);
        }
        MarketplaceSettingsForm form = new MarketplaceSettingsForm();
        // With a single choice there is nothing to pick: preselect it so its settings show at once.
        form.setProviderName(addable.size() == 1 ? addable.get(0).name() : null);
        return render(store, null, form, Map.of(), null, model, locale);
    }

    private String showEdit(String storeId, String name, Model model, Locale locale) {
        Store store = requireStore(storeId);
        MarketplaceIntegration existing = requireMarketplace(store, name);
        if (marketplaces.descriptor(name) == null) {
            // The adapter is gone: nothing to edit, the list offers only disconnecting it.
            return "redirect:" + marketplacesPath(storeId);
        }
        MarketplaceSettingsForm form = MarketplaceSettingsForm.of(
                IntegrationSettingsForm.from(name, marketplaces.storedSettings(store, name), marketplaces.fieldsOf(name)),
                existing.getOrdersImportSchedule(), existing.getReturnsImportSchedule());
        return render(store, existing, form, Map.of(), null, model, locale);
    }

    private String save(String storeId, String existingName, MarketplaceSettingsForm form, boolean async, Model model,
                        Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                        HttpServletResponse response) {
        Store store = requireStore(storeId);
        MarketplaceIntegration existing = existingName == null ? null : requireMarketplace(store, existingName);
        if (existing != null) {
            // The marketplace of an edit is the one in the address, whatever the form says.
            form.setProviderName(existingName);
        }
        String name = form.getProviderName();
        List<ProviderField> fields = marketplaces.fieldsOf(name);
        Map<String, String> errors = new LinkedHashMap<>();
        if (existing == null && marketplaces.addable(store).stream().noneMatch(d -> d.name().equals(name))) {
            // Not installed, not chosen, or already connected (it would be edited, not added twice)
            errors.put("providerName", "store.marketplace.provider.required");
        } else {
            errors.putAll(form.validate(fields, marketplaces.storedSecretKeys(store, name)));
            errors.putAll(form.validateSchedules(marketplaces.descriptor(name).supportsReturns(),
                    marketplaces.minIntervalMinutes()));
        }
        String failure = null;
        if (errors.isEmpty()) {
            boolean supportsReturns = marketplaces.descriptor(name).supportsReturns();
            ConnectionUpdateResult result = marketplaces.save(store, name, form.toConfiguration(fields),
                    form.ordersSchedule(), supportsReturns ? form.returnsSchedule() : null);
            if (result.hasErrors()) {
                // The service rolled the secret and the schedules back; its own checks match the form's, so this is
                // an AWS failure rather than something the operator can correct in a field.
                failure = result.errors().stream()
                        .map(error -> messageSource.getMessage(error.code(), error.args(), locale))
                        .collect(Collectors.joining(" "));
            }
        }
        if (!errors.isEmpty() || failure != null) {
            String view = render(store, existing, form, errors, failure, model, locale);
            model.addAttribute("errorLabels", errorLabels(form, fields, locale));
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        MarketplaceIntegration integration = store.getMarketplaceIntegration(name);
        boolean connectAccount = marketplaces.authorizedOnMarketplace(name) && integration != null && !integration.isLoggedIn();
        String next = connectAccount ? marketplacesPath(storeId) + "/" + name + "/authorize" : marketplacesPath(storeId);
        String message = messageSource.getMessage(
                connectAccount ? "store.marketplace.saved.connectAccount"
                        : existing == null ? "store.marketplace.added" : "store.marketplace.updated",
                new Object[]{marketplaces.displayName(name)}, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, next, message);
            render(store, existing, form, Map.of(), null, model, locale);
            model.addAttribute("redirectTo", next);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + next;
    }

    private String confirmDisconnect(String storeId, String name, Model model, Locale locale) {
        Store store = requireStore(storeId);
        requireMarketplace(store, name);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.marketplace.disconnect.title", new Object[]{marketplaces.displayName(name)}, locale),
                marketplaces.disconnectMessage(name, locale),
                messageSource.getMessage("store.marketplace.disconnect.action", null, locale),
                marketplacesPath(storeId) + "/" + name + "/disconnect",
                marketplacesPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, String name, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (store.getMarketplaceIntegration(name) != null) {
            ConnectionUpdateResult result = marketplaces.disconnect(store, name);
            if (result.hasErrors()) {
                // The service restored the secret and the schedules; nothing changed, so no success message
                redirectAttributes.addFlashAttribute("errorMessage",
                        messageSource.getMessage("store.marketplaces.error.update.failed", null, locale));
            } else {
                SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.marketplace.disconnected",
                        new Object[]{marketplaces.displayName(name)}, locale));
            }
        }
        return "redirect:" + marketplacesPath(storeId);
    }

    private String render(Store store, MarketplaceIntegration existing, MarketplaceSettingsForm form,
                          Map<String, String> errors, String failure, Model model, Locale locale) {
        String storeId = store.getStoreId();
        List<MarketplaceProviderDescriptor> providers = existing == null
                ? marketplaces.addable(store)
                : List.of(marketplaces.descriptor(existing.getName()));
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", Map.of());
        model.addAttribute("failure", failure);
        model.addAttribute("providers", providers);
        model.addAttribute("editing", existing != null);
        model.addAttribute("providerKnown", providers.stream().anyMatch(provider -> provider.name().equals(form.getProviderName())));
        model.addAttribute("storedSecretIds", marketplaces.storedSecretKeys(store, form.getProviderName()).stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("authorizedOnMarketplace", providers.stream()
                .filter(provider -> marketplaces.authorizedOnMarketplace(provider.name()))
                .map(MarketplaceProviderDescriptor::name)
                .collect(Collectors.toSet()));
        model.addAttribute("scheduleMinIntervalMinutes", marketplaces.minIntervalMinutes());
        model.addAttribute("ordersDefaultText", messageSource.getMessage("store.marketplaces.schedule.summary.default",
                new Object[]{marketplaces.ordersDefaultIntervalMinutes()}, locale));
        model.addAttribute("returnsDefaultText", messageSource.getMessage("store.marketplaces.returns.schedule.summary.default",
                new Object[]{marketplaces.returnsDefaultIntervalMinutes()}, locale));
        model.addAttribute("formAction", marketplacesPath(storeId) + (existing == null ? "/new" : "/" + existing.getName()));
        model.addAttribute("marketplacesHref", marketplacesPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.marketplaces", null, locale));
        model.addAttribute("pageTitle", existing == null
                ? messageSource.getMessage("store.marketplace.new.title", null, locale)
                : marketplaces.displayName(existing.getName()));
        return VIEW;
    }

    private Map<String, String> errorLabels(MarketplaceSettingsForm form, List<ProviderField> fields, Locale locale) {
        Map<String, String> labels = new LinkedHashMap<>();
        String name = form.getProviderName();
        labels.put(MarketplaceSettingsForm.scheduleId(name, MarketplaceSettingsForm.ORDERS),
                messageSource.getMessage("store.marketplace.schedule.orders", null, locale));
        labels.put(MarketplaceSettingsForm.scheduleId(name, MarketplaceSettingsForm.RETURNS),
                messageSource.getMessage("store.marketplace.schedule.returns", null, locale));
        if (fields != null) {
            fields.forEach(field -> labels.put(IntegrationSettingsForm.fieldId(form.getProviderName(), field),
                    ProviderFieldText.label(field)));
        }
        return labels;
    }

    private static String marketplacesPath(String storeId) {
        return SettingsPaths.store(storeId, "/marketplaces");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private static MarketplaceIntegration requireMarketplace(Store store, String name) {
        MarketplaceIntegration integration = store.getMarketplaceIntegration(name);
        if (integration == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return integration;
    }
}
