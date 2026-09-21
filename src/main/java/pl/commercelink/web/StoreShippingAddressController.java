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
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.dtos.LabelSenderForm;
import pl.commercelink.web.dtos.ShippingAddressForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;
import pl.commercelink.web.settings.WarehouseAddressView;

import java.util.Locale;
import java.util.Map;

/**
 * Settings › Shipping: pickup addresses (where the courier collects parcels; the default one is also the store's
 * collection point and where returns are sent) and the sender printed on labels. The store comes from the session
 * (ADMIN) or the path (SUPER_ADMIN), the address from the path: the old page took the store from a hidden form field.
 */
@Controller
@RequiredArgsConstructor
public class StoreShippingAddressController {

    private static final String ADDRESS_VIEW = "store-shipping-address";
    private static final String ADDRESS_FRAGMENT = ADDRESS_VIEW + " :: addressForm";
    private static final String SENDER_VIEW = "store-shipping-sender";
    private static final String SENDER_FRAGMENT = SENDER_VIEW + " :: senderForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/shipping/addresses/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newAddress(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/addresses/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewAddress(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/addresses/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createAddress(@ModelAttribute ShippingAddressForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/addresses/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreateAddress(@PathVariable String storeId, @ModelAttribute ShippingAddressForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/shipping/addresses/{addressId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editAddress(@PathVariable String addressId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), addressId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/addresses/{addressId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditAddress(@PathVariable String storeId, @PathVariable String addressId, Model model, Locale locale) {
        return showEdit(storeId, addressId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/addresses/{addressId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateAddress(@PathVariable String addressId, @ModelAttribute ShippingAddressForm form,
                                @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                HttpServletRequest request, HttpServletResponse response) {
        return update(CustomSecurityContext.getStoreId(), addressId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/addresses/{addressId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateAddress(@PathVariable String storeId, @PathVariable String addressId,
                                          @ModelAttribute ShippingAddressForm form,
                                          @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                          Model model, Locale locale, RedirectAttributes redirectAttributes,
                                          HttpServletRequest request, HttpServletResponse response) {
        return update(storeId, addressId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @PostMapping("/dashboard/store/shipping/addresses/{addressId}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public String makeDefault(@PathVariable String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        return makeDefault(CustomSecurityContext.getStoreId(), addressId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/addresses/{addressId}/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMakeDefault(@PathVariable String storeId, @PathVariable String addressId, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        return makeDefault(storeId, addressId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/shipping/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String addressId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), addressId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String addressId, Model model, Locale locale) {
        return confirmDelete(storeId, addressId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteAddress(@PathVariable String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), addressId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/addresses/{addressId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeleteAddress(@PathVariable String storeId, @PathVariable String addressId, Locale locale,
                                          RedirectAttributes redirectAttributes) {
        return delete(storeId, addressId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/shipping/sender")
    @PreAuthorize("hasRole('ADMIN')")
    public String sender(Model model, Locale locale) {
        return showSender(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/sender")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSender(@PathVariable String storeId, Model model, Locale locale) {
        return showSender(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/sender")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveSender(@ModelAttribute LabelSenderForm form,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                             HttpServletRequest request, HttpServletResponse response) {
        return saveSender(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/sender")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveSender(@PathVariable String storeId, @ModelAttribute LabelSenderForm form,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes,
                                       HttpServletRequest request, HttpServletResponse response) {
        return saveSender(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        return renderAddress(storeId, null, ShippingAddressForm.empty(), Map.of(), firstAddress(store), model, locale);
    }

    private String showEdit(String storeId, String addressId, Model model, Locale locale) {
        ShippingDetails address = requireAddress(requireStore(storeId), addressId);
        return renderAddress(storeId, address, ShippingAddressForm.from(address), Map.of(), false, model, locale);
    }

    private String create(String storeId, ShippingAddressForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(renderAddress(storeId, null, form, errors, firstAddress(store), model, locale), ADDRESS_FRAGMENT,
                    async, response);
        }
        StoreShippingSettingsController.configurationOf(store).addPickUpAddress(form.toNewShippingDetails(), form.isMakeDefault());
        storesRepository.save(store);
        return saved(storeId, "store.shipping.address.added", async, model, locale, redirectAttributes, request, response,
                () -> renderAddress(storeId, null, ShippingAddressForm.empty(), Map.of(), false, model, locale), ADDRESS_FRAGMENT);
    }

    private String update(String storeId, String addressId, ShippingAddressForm form, boolean async, Model model,
                          Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        ShippingDetails address = requireAddress(store, addressId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(renderAddress(storeId, address, form, errors, false, model, locale), ADDRESS_FRAGMENT, async, response);
        }
        form.applyTo(address);
        // Unticking the box on the default address is ignored: the store always has a default while it has addresses.
        if (form.isMakeDefault()) {
            StoreShippingSettingsController.configurationOf(store).makeDefaultPickUpAddress(addressId);
        }
        storesRepository.save(store);
        return saved(storeId, "store.shipping.address.updated", async, model, locale, redirectAttributes, request, response,
                () -> renderAddress(storeId, address, form, Map.of(), false, model, locale), ADDRESS_FRAGMENT);
    }

    private String makeDefault(String storeId, String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        requireAddress(store, addressId);
        StoreShippingSettingsController.configurationOf(store).makeDefaultPickUpAddress(addressId);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.shipping.address.madeDefault", null, locale));
        return "redirect:" + shippingPath(storeId);
    }

    private String confirmDelete(String storeId, String addressId, Model model, Locale locale) {
        ShippingDetails address = requireAddress(requireStore(storeId), addressId);
        String name = WarehouseAddressView.of(address, locale, "").title();
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.shipping.address.delete.title", new Object[]{name}, locale),
                messageSource.getMessage(address.is_default()
                        ? "store.shipping.address.delete.message.default" : "store.shipping.address.delete.message", null, locale),
                messageSource.getMessage("store.shipping.address.delete.action", null, locale),
                SettingsPaths.store(storeId, "/shipping/addresses/" + addressId + "/delete"),
                shippingPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String addressId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (StoreShippingSettingsController.configurationOf(store).removePickUpAddress(addressId)) {
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.shipping.address.deleted", null, locale));
        }
        return "redirect:" + shippingPath(storeId);
    }

    private String showSender(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        LabelSenderForm form = LabelSenderForm.from(StoreShippingSettingsController.configurationOf(store).getLabelSender());
        return renderSender(storeId, form, Map.of(), model, locale);
    }

    private String saveSender(String storeId, LabelSenderForm form, boolean async, Model model, Locale locale,
                              RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(renderSender(storeId, form, errors, model, locale), SENDER_FRAGMENT, async, response);
        }
        ShippingConfiguration configuration = StoreShippingSettingsController.configurationOf(store);
        configuration.setLabelSender(form.toSender(configuration.getLabelSender()));
        storesRepository.save(store);
        return saved(storeId, "store.shipping.sender.saved", async, model, locale, redirectAttributes, request, response,
                () -> renderSender(storeId, form, Map.of(), model, locale), SENDER_FRAGMENT);
    }

    private String rejected(String view, String fragment, boolean async, HttpServletResponse response) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return fragment;
        }
        return view;
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

    /**
     * @param first the store has no pickup address yet, so this one becomes the default without asking
     */
    private String renderAddress(String storeId, ShippingDetails existing, ShippingAddressForm form, Map<String, String> errors,
                                 boolean first, Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/shipping/addresses");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("countries", CountryOptions.forPicker(form.getCountry(), locale));
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("pageTitle", messageSource.getMessage(existing == null
                ? "store.shipping.address.new.title" : "store.shipping.address.edit.title", null, locale));
        model.addAttribute("alreadyDefault", existing != null && existing.is_default());
        model.addAttribute("firstAddress", first);
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return ADDRESS_VIEW;
    }

    private String renderSender(String storeId, LabelSenderForm form, Map<String, String> errors, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("countries", CountryOptions.forPicker(form.getCountry(), locale));
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/shipping/sender"));
        model.addAttribute("pageTitle", messageSource.getMessage("store.shipping.sender.title", null, locale));
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return SENDER_VIEW;
    }

    private static boolean firstAddress(Store store) {
        return StoreShippingSettingsController.configurationOf(store).getPickUpAddresses().isEmpty();
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

    private ShippingDetails requireAddress(Store store, String addressId) {
        return StoreShippingSettingsController.configurationOf(store).findPickUpAddress(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
