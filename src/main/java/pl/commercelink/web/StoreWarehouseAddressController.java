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
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.dtos.WarehouseAddressForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.Locale;
import java.util.Map;

/**
 * Goods-receiving addresses of the store, added and edited on their own page below Settings › Warehouse. The store
 * comes from the session (ADMIN) or the path (SUPER_ADMIN), the address from the path.
 */
@Controller
@RequiredArgsConstructor
public class StoreWarehouseAddressController {

    private static final String VIEW = "store-warehouse-address";
    private static final String FORM_FRAGMENT = VIEW + " :: addressForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/warehouse/addresses/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newAddress(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/addresses/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewAddress(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/addresses/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createAddress(@ModelAttribute WarehouseAddressForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/addresses/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreateAddress(@PathVariable String storeId, @ModelAttribute WarehouseAddressForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/warehouse/addresses/{addressId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editAddress(@PathVariable String addressId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), addressId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/addresses/{addressId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditAddress(@PathVariable String storeId, @PathVariable String addressId, Model model, Locale locale) {
        return showEdit(storeId, addressId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/addresses/{addressId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateAddress(@PathVariable String addressId, @ModelAttribute WarehouseAddressForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return update(CustomSecurityContext.getStoreId(), addressId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/addresses/{addressId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateAddress(@PathVariable String storeId, @PathVariable String addressId,
                                          @ModelAttribute WarehouseAddressForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return update(storeId, addressId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @PostMapping("/dashboard/store/warehouse/addresses/{addressId}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public String makeDefault(@PathVariable String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        return makeDefault(CustomSecurityContext.getStoreId(), addressId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/addresses/{addressId}/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMakeDefault(@PathVariable String storeId, @PathVariable String addressId, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        return makeDefault(storeId, addressId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/warehouse/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String addressId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), addressId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/warehouse/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String addressId, Model model, Locale locale) {
        return confirmDelete(storeId, addressId, model, locale);
    }

    @PostMapping("/dashboard/store/warehouse/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteAddress(@PathVariable String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), addressId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/warehouse/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeleteAddress(@PathVariable String storeId, @PathVariable String addressId, Locale locale,
                                          RedirectAttributes redirectAttributes) {
        return delete(storeId, addressId, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        requireStore(storeId);
        return render(storeId, null, WarehouseAddressForm.empty(), Map.of(), model, locale);
    }

    private String showEdit(String storeId, String addressId, Model model, Locale locale) {
        ShippingDetails details = requireAddress(requireStore(storeId), addressId);
        return render(storeId, details, WarehouseAddressForm.from(details), Map.of(), model, locale);
    }

    private String create(String storeId, WarehouseAddressForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, null, form, errors, async, model, locale, response);
        }
        store.addShippingDetails(form.toNewShippingDetails(), form.isMakeDefault());
        storesRepository.save(store);
        return saved(storeId, "store.warehouse.address.added", async, model, locale, redirectAttributes, request, response);
    }

    private String update(String storeId, String addressId, WarehouseAddressForm form, boolean async, Model model,
                          Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        ShippingDetails details = requireAddress(store, addressId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(storeId, details, form, errors, async, model, locale, response);
        }
        form.applyTo(details);
        // Unticking the box on the default address is ignored: the store always has a default while it has addresses.
        if (form.isMakeDefault()) {
            store.makeDefaultShippingDetails(addressId);
        }
        storesRepository.save(store);
        return saved(storeId, "store.warehouse.address.updated", async, model, locale, redirectAttributes, request, response);
    }

    private String makeDefault(String storeId, String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        requireAddress(store, addressId);
        store.makeDefaultShippingDetails(addressId);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.warehouse.address.madeDefault", null, locale));
        return "redirect:" + SettingsPaths.store(storeId, "/warehouse");
    }

    private String confirmDelete(String storeId, String addressId, Model model, Locale locale) {
        ShippingDetails details = requireAddress(requireStore(storeId), addressId);
        String name = WarehouseAddressView.of(details, locale, "").title();
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.warehouse.address.delete.title", new Object[]{name}, locale),
                messageSource.getMessage("store.warehouse.address.delete.message", null, locale),
                messageSource.getMessage("store.warehouse.address.delete.action", null, locale),
                SettingsPaths.store(storeId, "/warehouse/addresses/" + addressId + "/delete"),
                SettingsPaths.store(storeId, "/warehouse")));
        model.addAttribute("backLabel", messageSource.getMessage("store.warehouse", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (store.removeShippingDetails(addressId)) {
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.warehouse.address.deleted", null, locale));
        }
        return "redirect:" + SettingsPaths.store(storeId, "/warehouse");
    }

    private String rejected(String storeId, ShippingDetails existing, WarehouseAddressForm form, Map<String, String> errors,
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
        String warehousePath = SettingsPaths.store(storeId, "/warehouse");
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, warehousePath, message);
            render(storeId, null, WarehouseAddressForm.empty(), Map.of(), model, locale);
            model.addAttribute("redirectTo", warehousePath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + warehousePath;
    }

    private String render(String storeId, ShippingDetails existing, WarehouseAddressForm form, Map<String, String> errors,
                          Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/warehouse/addresses");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("countries", CountryOptions.forPicker(form.getCountry(), locale));
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("pageTitle", existing == null
                ? messageSource.getMessage("store.warehouse.address.new.title", null, locale)
                : messageSource.getMessage("store.warehouse.address.edit.title", null, locale));
        model.addAttribute("alreadyDefault", existing != null && existing.is_default());
        model.addAttribute("warehouseHref", SettingsPaths.store(storeId, "/warehouse"));
        model.addAttribute("backLabel", messageSource.getMessage("store.warehouse", null, locale));
        return VIEW;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }

    private ShippingDetails requireAddress(Store store, String addressId) {
        return store.findShippingDetails(addressId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
