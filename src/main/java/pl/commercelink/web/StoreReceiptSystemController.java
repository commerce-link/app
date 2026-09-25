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
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.receipts.api.ReceiptProviderDescriptor;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.IntegrationStatus;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings › E-receipts › E-receipt system: the system is chosen and its access details entered once, rarely changed
 * and partly secret, so they live on their own page instead of next to the everyday receipt settings. The store comes
 * from the session (ADMIN) or the path (SUPER_ADMIN), like every other settings subpage.
 */
@Controller
@RequiredArgsConstructor
public class StoreReceiptSystemController {

    private static final String VIEW = "store-receipt-system";
    private static final String FORM_FRAGMENT = VIEW + " :: systemForm";

    private final StoresRepository storesRepository;
    private final ReceiptSystems receiptSystems;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/receipts/system")
    @PreAuthorize("hasRole('ADMIN')")
    public String system(Model model, Locale locale) {
        return show(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/receipts/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSystem(@PathVariable String storeId, Model model, Locale locale) {
        return show(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/receipts/system")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveSystem(@ModelAttribute IntegrationSettingsForm form,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                             HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/receipts/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveSystem(@PathVariable String storeId, @ModelAttribute IntegrationSettingsForm form,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes,
                                       HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/receipts/system/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/receipts/system/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, Model model, Locale locale) {
        return confirmDisconnect(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/receipts/system/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/receipts/system/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(storeId, locale, redirectAttributes);
    }

    private String show(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        String providerName = receiptSystems.current(store);
        IntegrationSettingsForm form = IntegrationSettingsForm.from(providerName, receiptSystems.storedSettings(store),
                receiptSystems.fieldsOf(providerName));
        return render(store, form, Map.of(), model, locale);
    }

    private String save(String storeId, IntegrationSettingsForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        List<ProviderField> fields = receiptSystems.fieldsOf(form.getProviderName());
        Map<String, String> errors = form.validate(fields, receiptSystems.storedSecretKeys(store, form.getProviderName()));
        if (!errors.isEmpty()) {
            String view = render(store, form, errors, model, locale);
            model.addAttribute("errorLabels", form.errorLabels(fields));
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }
        // Validated above, so the adapter settings are complete.
        try {
            receiptSystems.save(store, form.getProviderName(), form.toConfiguration(fields));
        } catch (ReceiptSystemBusyException e) {
            // Switching away from a provider that still has live attempts would strand them without their secret.
            Map<String, String> switchErrors = Map.of("providerName", "store.receipts.system.switch.live");
            String view = render(store, form, switchErrors, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }
        storesRepository.save(store);

        String receiptsPath = SettingsPaths.store(storeId, "/receipts");
        String message = messageSource.getMessage("store.receipts.system.saved", null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, receiptsPath, message);
            render(store, form, Map.of(), model, locale);
            model.addAttribute("redirectTo", receiptsPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + receiptsPath;
    }

    private String confirmDisconnect(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        IntegrationStatus status = receiptSystems.status(store);
        if (!status.chosen()) {
            return "redirect:" + SettingsPaths.store(storeId, "/receipts");
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.receipts.system.disconnect.title", new Object[]{status.displayName()}, locale),
                messageSource.getMessage("store.receipts.system.disconnect.message", null, locale),
                messageSource.getMessage("store.receipts.system.disconnect.action", null, locale),
                SettingsPaths.store(storeId, "/receipts/system/disconnect"),
                SettingsPaths.store(storeId, "/receipts")));
        model.addAttribute("backLabel", messageSource.getMessage("store.receipts", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (receiptSystems.hasLiveAttempts(store)) {
            SettingsFlash.errorOnRedirect(redirectAttributes,
                    messageSource.getMessage("store.receipts.system.disconnect.live", null, locale));
            return "redirect:" + SettingsPaths.store(storeId, "/receipts");
        }
        String providerName = receiptSystems.current(store);
        if (providerName != null) {
            receiptSystems.disconnect(store, providerName);
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes,
                    messageSource.getMessage("store.receipts.system.disconnected", null, locale));
        }
        return "redirect:" + SettingsPaths.store(storeId, "/receipts");
    }

    private String render(Store store, IntegrationSettingsForm form, Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        List<ReceiptProviderDescriptor> providers = receiptSystems.installed();
        IntegrationStatus status = receiptSystems.status(store);
        Map<String, String> webhooks = new HashMap<>();
        providers.forEach(provider -> {
            String url = receiptSystems.webhookUrl(storeId, provider);
            if (url != null) {
                webhooks.put(provider.name(), url);
            }
        });
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", Map.of());
        model.addAttribute("providers", providers);
        model.addAttribute("providerKnown", providers.stream().anyMatch(provider -> provider.name().equals(form.getProviderName())));
        model.addAttribute("storedSecretIds", receiptSystems.storedSecretKeys(store, form.getProviderName()).stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("webhooks", webhooks);
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/receipts/system"));
        model.addAttribute("receiptsHref", SettingsPaths.store(storeId, "/receipts"));
        model.addAttribute("backLabel", messageSource.getMessage("store.receipts", null, locale));
        model.addAttribute("pageTitle", messageSource.getMessage(status.chosen()
                ? "store.receipts.system.change.title" : "store.receipts.system.choose.title", null, locale));
        return VIEW;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
