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
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.PaymentIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.IntegrationSettingsForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings › Payments › a payment gateway: added once, its access details entered on its own page together with the
 * webhook address the gateway's panel must call, rarely changed. A store may use several gateways (the customer picks
 * one in an offer); the first becomes the default. The store comes from the session (ADMIN) or the path (SUPER_ADMIN),
 * the gateway from the path.
 */
@Controller
@RequiredArgsConstructor
public class StorePaymentGatewayController {

    private static final String VIEW = "store-payment-gateway";
    private static final String FORM_FRAGMENT = VIEW + " :: gatewayForm";

    private final StoresRepository storesRepository;
    private final PaymentGateways paymentGateways;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/payments/gateways/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newGateway(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/gateways/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewGateway(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/gateways/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String addGateway(@ModelAttribute IntegrationSettingsForm form, @RequestParam(defaultValue = "false") boolean makeDefault,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                             HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), null, form, makeDefault, SettingsPaths.isAsync(requestedWith),
                model, locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/gateways/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminAddGateway(@PathVariable String storeId, @ModelAttribute IntegrationSettingsForm form,
                                       @RequestParam(defaultValue = "false") boolean makeDefault,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes,
                                       HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, null, form, makeDefault, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/payments/gateways/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editGateway(@PathVariable String name, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), name, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/gateways/{name}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditGateway(@PathVariable String storeId, @PathVariable String name, Model model, Locale locale) {
        return showEdit(storeId, name, model, locale);
    }

    @PostMapping("/dashboard/store/payments/gateways/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateGateway(@PathVariable String name, @ModelAttribute IntegrationSettingsForm form,
                                @RequestParam(defaultValue = "false") boolean makeDefault,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), name, form, makeDefault, SettingsPaths.isAsync(requestedWith),
                model, locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/gateways/{name}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateGateway(@PathVariable String storeId, @PathVariable String name,
                                          @ModelAttribute IntegrationSettingsForm form,
                                          @RequestParam(defaultValue = "false") boolean makeDefault,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, name, form, makeDefault, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/payments/gateways/{name}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDisconnect(@PathVariable String name, Model model, Locale locale) {
        return confirmDisconnect(CustomSecurityContext.getStoreId(), name, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/gateways/{name}/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDisconnect(@PathVariable String storeId, @PathVariable String name, Model model,
                                              Locale locale) {
        return confirmDisconnect(storeId, name, model, locale);
    }

    @PostMapping("/dashboard/store/payments/gateways/{name}/disconnect")
    @PreAuthorize("hasRole('ADMIN')")
    public String disconnect(@PathVariable String name, Locale locale, RedirectAttributes redirectAttributes) {
        return disconnect(CustomSecurityContext.getStoreId(), name, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/gateways/{name}/disconnect")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDisconnect(@PathVariable String storeId, @PathVariable String name, Locale locale,
                                       RedirectAttributes redirectAttributes) {
        return disconnect(storeId, name, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        List<PaymentProviderDescriptor> addable = paymentGateways.addable(store);
        if (addable.isEmpty()) {
            return "redirect:" + paymentsPath(storeId);
        }
        IntegrationSettingsForm form = new IntegrationSettingsForm();
        // With a single choice there is nothing to pick: preselect it so its settings show at once.
        form.setProviderName(addable.size() == 1 ? addable.get(0).name() : null);
        return render(store, null, form, store.getPayments().isEmpty(), Map.of(), model, locale);
    }

    private String showEdit(String storeId, String name, Model model, Locale locale) {
        Store store = requireStore(storeId);
        PaymentIntegration existing = requireGateway(store, name);
        if (paymentGateways.descriptor(name) == null) {
            // The adapter is gone: nothing to edit, the list offers only disconnecting it.
            return "redirect:" + paymentsPath(storeId);
        }
        IntegrationSettingsForm form = IntegrationSettingsForm.from(name, paymentGateways.storedSettings(store, name),
                paymentGateways.fieldsOf(name));
        return render(store, existing, form, existing.is_default(), Map.of(), model, locale);
    }

    private String save(String storeId, String existingName, IntegrationSettingsForm form, boolean makeDefault,
                        boolean async, Model model, Locale locale, RedirectAttributes redirectAttributes,
                        HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        PaymentIntegration existing = existingName == null ? null : requireGateway(store, existingName);
        if (existing != null && paymentGateways.descriptor(existingName) == null) {
            // The adapter is gone, as on the edit page: the list offers only disconnecting it.
            return "redirect:" + paymentsPath(storeId);
        }
        if (existing != null) {
            // The gateway of an edit is the one in the address, whatever the form says.
            form.setProviderName(existingName);
        }
        List<ProviderField> fields = paymentGateways.fieldsOf(form.getProviderName());
        Map<String, String> errors = new LinkedHashMap<>();
        if (existing == null && paymentGateways.addable(store).stream().noneMatch(d -> d.name().equals(form.getProviderName()))) {
            // Not installed, not chosen, or already used by the store (it would be edited, not added twice)
            errors.put("providerName", "store.payments.gateway.provider.required");
        } else {
            errors.putAll(form.validate(fields, paymentGateways.storedSecretKeys(store, form.getProviderName())));
        }
        if (!errors.isEmpty()) {
            String view = render(store, existing, form, makeDefault, errors, model, locale);
            model.addAttribute("errorLabels", form.errorLabels(fields));
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }
        paymentGateways.save(store, form.getProviderName(), form.toConfiguration(fields), makeDefault);
        storesRepository.save(store);

        String message = messageSource.getMessage(existing == null ? "store.payments.gateway.added" : "store.payments.gateway.updated",
                null, locale);
        String paymentsPath = paymentsPath(storeId);
        if (async) {
            SettingsFlash.forNextPage(request, response, paymentsPath, message);
            render(store, existing, form, makeDefault, Map.of(), model, locale);
            model.addAttribute("redirectTo", paymentsPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + paymentsPath;
    }

    private String confirmDisconnect(String storeId, String name, Model model, Locale locale) {
        Store store = requireStore(storeId);
        requireGateway(store, name);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.payments.gateway.disconnect.title", new Object[]{displayName(name)}, locale),
                paymentGateways.disconnectMessage(store, name, locale),
                messageSource.getMessage("store.payments.gateway.disconnect.action", null, locale),
                SettingsPaths.store(storeId, "/payments/gateways/" + name + "/disconnect"),
                paymentsPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return "settings-confirm";
    }

    private String disconnect(String storeId, String name, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (store.getPaymentIntegration(name) != null) {
            paymentGateways.disconnect(store, name);
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes,
                    messageSource.getMessage("store.payments.gateway.disconnected", new Object[]{displayName(name)}, locale));
        }
        return "redirect:" + paymentsPath(storeId);
    }

    private String render(Store store, PaymentIntegration existing, IntegrationSettingsForm form, boolean makeDefault,
                          Map<String, String> errors, Model model, Locale locale) {
        String storeId = store.getStoreId();
        List<PaymentProviderDescriptor> providers = existing == null
                ? paymentGateways.addable(store)
                : List.of(paymentGateways.descriptor(existing.getName()));
        Map<String, String> webhooks = new HashMap<>();
        providers.forEach(provider -> {
            String url = paymentGateways.webhookUrl(storeId, provider);
            if (url != null) {
                webhooks.put(provider.name(), url);
            }
        });
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", Map.of());
        model.addAttribute("providers", providers);
        model.addAttribute("webhooks", webhooks);
        model.addAttribute("editing", existing != null);
        model.addAttribute("providerKnown", providers.stream().anyMatch(provider -> provider.name().equals(form.getProviderName())));
        model.addAttribute("storedSecretIds", paymentGateways.storedSecretKeys(store, form.getProviderName()).stream()
                .map(key -> "setting-" + form.getProviderName() + "-" + key)
                .collect(Collectors.toSet()));
        model.addAttribute("alreadyDefault", existing != null && existing.is_default());
        // The first gateway becomes the default anyway; the box is shown ticked so the page says so.
        model.addAttribute("firstGateway", existing == null && store.getPayments().isEmpty());
        model.addAttribute("makeDefault", makeDefault);
        model.addAttribute("formAction", SettingsPaths.store(storeId, existing == null
                ? "/payments/gateways/new" : "/payments/gateways/" + existing.getName()));
        model.addAttribute("paymentsHref", paymentsPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        model.addAttribute("pageTitle", existing == null
                ? messageSource.getMessage("store.payments.gateway.new.title", null, locale)
                : messageSource.getMessage("store.payments.gateway.edit.title", new Object[]{displayName(existing.getName())}, locale));
        return VIEW;
    }

    private String displayName(String name) {
        return paymentGateways.displayName(name);
    }

    private static String paymentsPath(String storeId) {
        return SettingsPaths.store(storeId, "/payments");
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private static PaymentIntegration requireGateway(Store store, String name) {
        PaymentIntegration gateway = store.getPaymentIntegration(name);
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return gateway;
    }
}
