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
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryOptionForm;
import pl.commercelink.web.dtos.PickerOption;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Delivery options customers choose in an offer or at the online-store checkout, added and edited on their own page
 * below Settings › Payments. The old page edited all options in one table with two blank rows for new ones and removed
 * an option when its name was cleared; a removed option broke every offer and paid basket that had chosen it (the offer
 * page and the payment webhook looked it up by id), so removing now retires the option instead.
 */
@Controller
@RequiredArgsConstructor
public class StoreDeliveryOptionController {

    private static final String VIEW = "store-delivery-option";
    private static final String FORM_FRAGMENT = VIEW + " :: optionForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/payments/delivery-options/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newOption(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/delivery-options/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewOption(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/delivery-options/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createOption(@ModelAttribute DeliveryOptionForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/delivery-options/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreateOption(@PathVariable String storeId, @ModelAttribute DeliveryOptionForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/payments/delivery-options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editOption(@PathVariable String optionId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), optionId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/delivery-options/{optionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditOption(@PathVariable String storeId, @PathVariable String optionId, Model model, Locale locale) {
        return showEdit(storeId, optionId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/delivery-options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateOption(@PathVariable String optionId, @ModelAttribute DeliveryOptionForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletRequest request, HttpServletResponse response) {
        return update(CustomSecurityContext.getStoreId(), optionId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/delivery-options/{optionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateOption(@PathVariable String storeId, @PathVariable String optionId,
                                         @ModelAttribute DeliveryOptionForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletRequest request, HttpServletResponse response) {
        return update(storeId, optionId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @GetMapping("/dashboard/store/payments/delivery-options/{optionId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String optionId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), optionId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/payments/delivery-options/{optionId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String optionId, Model model,
                                          Locale locale) {
        return confirmDelete(storeId, optionId, model, locale);
    }

    @PostMapping("/dashboard/store/payments/delivery-options/{optionId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOption(@PathVariable String optionId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), optionId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/payments/delivery-options/{optionId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeleteOption(@PathVariable String storeId, @PathVariable String optionId, Locale locale,
                                         RedirectAttributes redirectAttributes) {
        return delete(storeId, optionId, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        requireStore(storeId);
        return render(storeId, null, DeliveryOptionForm.empty(), Map.of(), model, locale);
    }

    private String showEdit(String storeId, String optionId, Model model, Locale locale) {
        DeliveryOption option = requireOption(requireStore(storeId), optionId);
        return render(storeId, option, DeliveryOptionForm.from(option), Map.of(), model, locale);
    }

    private String create(String storeId, DeliveryOptionForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, null, form, errors, async, model, locale, response);
        }
        CheckoutConfiguration configuration = Objects.requireNonNullElseGet(store.getCheckoutConfiguration(), CheckoutConfiguration::new);
        configuration.addDeliveryOption(form.toNewDeliveryOption());
        store.setCheckoutConfiguration(configuration);
        storesRepository.save(store);
        return saved(storeId, "store.payments.delivery.added", async, model, locale, redirectAttributes, request, response);
    }

    private String update(String storeId, String optionId, DeliveryOptionForm form, boolean async, Model model,
                          Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        DeliveryOption option = requireOption(store, optionId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, option, form, errors, async, model, locale, response);
        }
        form.applyTo(option);
        storesRepository.save(store);
        return saved(storeId, "store.payments.delivery.updated", async, model, locale, redirectAttributes, request, response);
    }

    private String confirmDelete(String storeId, String optionId, Model model, Locale locale) {
        DeliveryOption option = requireOption(requireStore(storeId), optionId);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.payments.delivery.delete.title", new Object[]{option.getName()}, locale),
                messageSource.getMessage("store.payments.delivery.delete.message", null, locale),
                messageSource.getMessage("store.payments.delivery.delete.action", null, locale),
                SettingsPaths.store(storeId, "/payments/delivery-options/" + optionId + "/delete"),
                paymentsPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String optionId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        CheckoutConfiguration configuration = store.getCheckoutConfiguration();
        if (configuration != null && configuration.retireDeliveryOption(optionId)) {
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.payments.delivery.deleted", null, locale));
        }
        return "redirect:" + paymentsPath(storeId);
    }

    private String rejected(String storeId, DeliveryOption existing, DeliveryOptionForm form, Map<String, String> errors,
                            boolean async, Model model, Locale locale, HttpServletResponse response) {
        String view = render(storeId, existing, form, errors, model, locale);
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return FORM_FRAGMENT;
        }
        return view;
    }

    private String saved(String storeId, String messageKey, boolean async, Model model, Locale locale,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        String paymentsPath = paymentsPath(storeId);
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, paymentsPath, message);
            render(storeId, null, DeliveryOptionForm.empty(), Map.of(), model, locale);
            model.addAttribute("redirectTo", paymentsPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + paymentsPath;
    }

    private String render(String storeId, DeliveryOption existing, DeliveryOptionForm form, Map<String, String> errors,
                          Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/payments/delivery-options");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("types", typeOptions(locale));
        model.addAttribute("editing", existing != null);
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("pageTitle", messageSource.getMessage(existing == null
                ? "store.payments.delivery.new.title" : "store.payments.delivery.edit.title", null, locale));
        model.addAttribute("paymentsHref", paymentsPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.payments", null, locale));
        return VIEW;
    }

    private List<PickerOption> typeOptions(Locale locale) {
        return Arrays.stream(ShipmentType.values())
                .map(type -> new PickerOption(type.name(), messageSource.getMessage("ShipmentType." + type.name(), null, locale)))
                .toList();
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

    /** Only an active option can be edited or removed; a retired one exists for old offers only. */
    private static DeliveryOption requireOption(Store store, String optionId) {
        return store.getCheckoutConfiguration() == null ? notFound()
                : store.getCheckoutConfiguration().findActiveDeliveryOption(optionId).orElseGet(StoreDeliveryOptionController::notFound);
    }

    private static DeliveryOption notFound() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
