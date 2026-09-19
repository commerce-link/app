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
import pl.commercelink.orders.rma.RMAShippingService;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.PackageTemplateForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;
import java.util.Map;

/**
 * Settings › Shipping › Package templates: the parcels an order (or a customer's return) is sent in. The store comes
 * from the session (ADMIN) or the path (SUPER_ADMIN), the template from the path; the old pages were ADMIN-only and
 * took the store from a hidden form field.
 */
@Controller
@RequiredArgsConstructor
public class StorePackageTemplateController {

    private static final String VIEW = "store-shipping-template";
    private static final String FORM_FRAGMENT = VIEW + " :: templateForm";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/shipping/templates/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newTemplate(Model model, Locale locale) {
        return showNew(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/templates/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewTemplate(@PathVariable String storeId, Model model, Locale locale) {
        return showNew(storeId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/templates/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createTemplate(@ModelAttribute PackageTemplateForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request, HttpServletResponse response) {
        return create(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/templates/new")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminCreateTemplate(@PathVariable String storeId, @ModelAttribute PackageTemplateForm form,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletRequest request, HttpServletResponse response) {
        return create(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request, response);
    }

    @GetMapping("/dashboard/store/shipping/templates/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editTemplate(@PathVariable String templateId, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), templateId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/templates/{templateId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditTemplate(@PathVariable String storeId, @PathVariable String templateId, Model model, Locale locale) {
        return showEdit(storeId, templateId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/templates/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateTemplate(@PathVariable String templateId, @ModelAttribute PackageTemplateForm form,
                                 @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                 Model model, Locale locale, RedirectAttributes redirectAttributes,
                                 HttpServletRequest request, HttpServletResponse response) {
        return update(CustomSecurityContext.getStoreId(), templateId, form, SettingsPaths.isAsync(requestedWith), model,
                locale, redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/templates/{templateId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateTemplate(@PathVariable String storeId, @PathVariable String templateId,
                                           @ModelAttribute PackageTemplateForm form,
                                           @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                           Model model, Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletRequest request, HttpServletResponse response) {
        return update(storeId, templateId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes,
                request, response);
    }

    @PostMapping("/dashboard/store/shipping/templates/{templateId}/default")
    @PreAuthorize("hasRole('ADMIN')")
    public String makeDefault(@PathVariable String templateId, Locale locale, RedirectAttributes redirectAttributes) {
        return makeDefault(CustomSecurityContext.getStoreId(), templateId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/templates/{templateId}/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminMakeDefault(@PathVariable String storeId, @PathVariable String templateId, Locale locale,
                                        RedirectAttributes redirectAttributes) {
        return makeDefault(storeId, templateId, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store/shipping/templates/{templateId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmDelete(@PathVariable String templateId, Model model, Locale locale) {
        return confirmDelete(CustomSecurityContext.getStoreId(), templateId, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/shipping/templates/{templateId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmDelete(@PathVariable String storeId, @PathVariable String templateId, Model model, Locale locale) {
        return confirmDelete(storeId, templateId, model, locale);
    }

    @PostMapping("/dashboard/store/shipping/templates/{templateId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteTemplate(@PathVariable String templateId, Locale locale, RedirectAttributes redirectAttributes) {
        return delete(CustomSecurityContext.getStoreId(), templateId, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/shipping/templates/{templateId}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminDeleteTemplate(@PathVariable String storeId, @PathVariable String templateId, Locale locale,
                                           RedirectAttributes redirectAttributes) {
        return delete(storeId, templateId, locale, redirectAttributes);
    }

    private String showNew(String storeId, Model model, Locale locale) {
        Store store = requireStore(storeId);
        return render(storeId, null, PackageTemplateForm.empty(), Map.of(), firstTemplate(store), model, locale);
    }

    private String showEdit(String storeId, String templateId, Model model, Locale locale) {
        PackageTemplate template = requireTemplate(requireStore(storeId), templateId);
        return render(storeId, template, PackageTemplateForm.from(template), Map.of(), false, model, locale);
    }

    private String create(String storeId, PackageTemplateForm form, boolean async, Model model, Locale locale,
                          RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(render(storeId, null, form, errors, firstTemplate(store), model, locale), async, response);
        }
        StoreShippingSettingsController.configurationOf(store).addPackageTemplate(form.toNewTemplate(), form.isMakeDefault());
        storesRepository.save(store);
        return saved(storeId, "store.shipping.template.added", async, model, locale, redirectAttributes, request, response,
                () -> render(storeId, null, form, Map.of(), false, model, locale));
    }

    private String update(String storeId, String templateId, PackageTemplateForm form, boolean async, Model model,
                          Locale locale, RedirectAttributes redirectAttributes, HttpServletRequest request,
                          HttpServletResponse response) {
        Store store = requireStore(storeId);
        PackageTemplate template = requireTemplate(store, templateId);
        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            return rejected(render(storeId, template, form, errors, false, model, locale), async, response);
        }
        form.applyTo(template);
        // Unticking the box on the default template is ignored: the store always has a default while it has templates.
        if (form.isMakeDefault()) {
            StoreShippingSettingsController.configurationOf(store).makeDefaultPackageTemplate(templateId);
        }
        storesRepository.save(store);
        return saved(storeId, "store.shipping.template.updated", async, model, locale, redirectAttributes, request, response,
                () -> render(storeId, template, form, Map.of(), false, model, locale));
    }

    private String makeDefault(String storeId, String templateId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        requireTemplate(store, templateId);
        StoreShippingSettingsController.configurationOf(store).makeDefaultPackageTemplate(templateId);
        storesRepository.save(store);
        SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.shipping.template.madeDefault", null, locale));
        return "redirect:" + shippingPath(storeId);
    }

    private String confirmDelete(String storeId, String templateId, Model model, Locale locale) {
        PackageTemplate template = requireTemplate(requireStore(storeId), templateId);
        boolean forReturns = template.getName() != null && template.getName().startsWith(RMAShippingService.RETURN_TEMPLATE_PREFIX);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.shipping.template.delete.title", new Object[]{template.getName()}, locale),
                messageSource.getMessage(forReturns
                        ? "store.shipping.template.delete.message.returns" : "store.shipping.template.delete.message", null, locale),
                messageSource.getMessage("store.shipping.template.delete.action", null, locale),
                SettingsPaths.store(storeId, "/shipping/templates/" + templateId + "/delete"),
                shippingPath(storeId)));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return "settings-confirm";
    }

    private String delete(String storeId, String templateId, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = requireStore(storeId);
        if (StoreShippingSettingsController.configurationOf(store).removePackageTemplate(templateId)) {
            storesRepository.save(store);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.shipping.template.deleted", null, locale));
        }
        return "redirect:" + shippingPath(storeId);
    }

    private String rejected(String view, boolean async, HttpServletResponse response) {
        if (async) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            return FORM_FRAGMENT;
        }
        return view;
    }

    private String saved(String storeId, String messageKey, boolean async, Model model, Locale locale,
                         RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response,
                         Runnable render) {
        String shippingPath = shippingPath(storeId);
        String message = messageSource.getMessage(messageKey, null, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, shippingPath, message);
            render.run();
            model.addAttribute("redirectTo", shippingPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + shippingPath;
    }

    /**
     * @param first the store has no template yet, so this one becomes the default without asking
     */
    private String render(String storeId, PackageTemplate existing, PackageTemplateForm form, Map<String, String> errors,
                          boolean first, Model model, Locale locale) {
        String basePath = SettingsPaths.store(storeId, "/shipping/templates");
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", form.errorLabels((number, field) -> messageSource.getMessage(
                "store.shipping.template.parcel.field", new Object[]{number,
                        messageSource.getMessage("store.shipping.template.parcel." + field, null, locale)}, locale)));
        model.addAttribute("formAction", existing == null ? basePath + "/new" : basePath + "/" + existing.getId());
        model.addAttribute("pageTitle", messageSource.getMessage(existing == null
                ? "store.shipping.template.new.title" : "store.shipping.template.edit.title", null, locale));
        model.addAttribute("alreadyDefault", existing != null && existing.isDefault());
        model.addAttribute("firstTemplate", first);
        model.addAttribute("returnPrefix", RMAShippingService.RETURN_TEMPLATE_PREFIX);
        model.addAttribute("shippingHref", shippingPath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("store.shipping", null, locale));
        return VIEW;
    }

    private static boolean firstTemplate(Store store) {
        return StoreShippingSettingsController.configurationOf(store).getPackageTemplates().isEmpty();
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

    private PackageTemplate requireTemplate(Store store, String templateId) {
        return StoreShippingSettingsController.configurationOf(store).findPackageTemplate(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
