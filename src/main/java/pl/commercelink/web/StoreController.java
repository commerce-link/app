package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.*;
import pl.commercelink.web.settings.StoreSettingsOverviewFactory;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.web.dtos.BrandingForm;
import pl.commercelink.web.dtos.CompanyDetailsForm;
import pl.commercelink.web.dtos.CountryOptions;

import java.util.*;

@Controller
public class StoreController {

    private static final String ASYNC_FORM_HEADER = "X-Requested-With";
    private static final String ASYNC_FORM_HEADER_VALUE = "fetch";
    private static final String COMPANY_DETAILS_FORM_FRAGMENT = "store-company-details :: companyDetailsForm";
    private static final String BRANDING_FORM_FRAGMENT = "store-branding :: brandingForm";

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private StoreSettingsOverviewFactory storeSettingsOverviewFactory;

    @GetMapping("/dashboard/store")
    @PreAuthorize("hasRole('ADMIN')")
    public String store(Model model) {
        Store store = storesRepository.findById(getStoreId());

        StoreForm form = new StoreForm(store);
        model.addAttribute("form", form);
        model.addAttribute("isSuperAdmin", false);
        model.addAttribute("overview", storeSettingsOverviewFactory.build(store, UserRole.ADMIN));
        return "store";
    }

    @GetMapping("/dashboard/store/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeBranding(Model model) {
        return renderStoreBranding(getStoreId(), null, Map.of(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreBranding(@PathVariable String storeId, Model model) {
        return renderStoreBranding(storeId, null, Map.of(), model);
    }

    private String renderStoreBranding(String storeId, BrandingForm submitted, Map<String, String> errors, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        model.addAttribute("form", submitted != null ? submitted : BrandingForm.from(store));
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", brandingPath(storeId));
        Branding branding = store.getBranding();
        model.addAttribute("hasLogo", branding != null && branding.getLogo() != null);
        model.addAttribute("logoUrl", "/StoreLogo/" + storeId
                + (branding != null && branding.getLogoVersion() != null ? "?v=" + branding.getLogoVersion() : ""));
        model.addAttribute("logoMaxBytes", BrandingForm.LOGO_MAX_BYTES);
        return "store-branding";
    }

    @GetMapping("/dashboard/store/company-details")
    @PreAuthorize("hasRole('ADMIN')")
    public String storeCompanyDetails(Model model, Locale locale) {
        return renderStoreCompanyDetails(getStoreId(), null, Map.of(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/company-details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminStoreCompanyDetails(@PathVariable String storeId, Model model, Locale locale) {
        return renderStoreCompanyDetails(storeId, null, Map.of(), model, locale);
    }

    private String renderStoreCompanyDetails(String storeId, CompanyDetailsForm submitted, Map<String, String> errors,
                                             Model model, Locale locale) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            model.addAttribute("error", "Store not found");
            return "error";
        }

        CompanyDetailsForm form = submitted != null ? submitted : CompanyDetailsForm.from(store.getBillingDetails());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("countries", CountryOptions.forPicker(form.getCountry(), locale));
        model.addAttribute("formAction", companyDetailsPath(storeId));
        return "store-company-details";
    }

    // The store is taken from the session (ADMIN) or the path (SUPER_ADMIN), never from the submitted form.
    @PostMapping("/dashboard/store/branding")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStoreBranding(@ModelAttribute BrandingForm form,
                                      @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                      Model model, Locale locale, RedirectAttributes redirectAttributes,
                                      HttpServletResponse response) {
        return saveStoreBranding(getStoreId(), form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/branding")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateStoreBranding(@PathVariable String storeId, @ModelAttribute BrandingForm form,
                                                @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                                Model model, Locale locale, RedirectAttributes redirectAttributes,
                                                HttpServletResponse response) {
        return saveStoreBranding(storeId, form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    private String saveStoreBranding(String storeId, BrandingForm form, boolean async, Model model, Locale locale,
                                     RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        Store store = storesRepository.findById(storeId);
        if (store == null || !errors.isEmpty()) {
            String view = renderStoreBranding(storeId, form, errors, model);
            if (async && store != null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return BRANDING_FORM_FRAGMENT;
            }
            return view;
        }

        form.applyTo(store);
        // Only after the whole form is valid: storing a logo replaces the previous file straight away.
        Branding branding = store.getBranding();
        form.logoUpload().ifPresentOrElse(
                upload -> {
                    branding.setLogo(storesRepository.storeLogo(storeId, "logo." + upload.type().extension(), upload.content()));
                    branding.setLogoVersion(System.currentTimeMillis());
                },
                () -> {
                    if (form.isRemoveLogo() && branding.getLogo() != null) {
                        storesRepository.removeLogo(storeId);
                        branding.setLogo(null);
                        branding.setLogoVersion(null);
                    }
                });
        storesRepository.save(store);

        String successMessage = messageSource.getMessage("store.branding.update.success", null, locale);
        if (async) {
            renderStoreBranding(storeId, BrandingForm.from(store), Map.of(), model);
            model.addAttribute("savedMessage", successMessage);
            return BRANDING_FORM_FRAGMENT;
        }
        redirectAttributes.addFlashAttribute("successMessage", successMessage);
        return "redirect:" + brandingPath(storeId);
    }

    private String brandingPath(String storeId) {
        return isSuperAdmin()
                ? String.format("/dashboard/store/%s/branding", storeId)
                : "/dashboard/store/branding";
    }

    // The store is taken from the session (ADMIN) or the path (SUPER_ADMIN), never from the submitted form.
    @PostMapping("/dashboard/store/company-details")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateStoreCompanyDetails(@ModelAttribute CompanyDetailsForm form,
                                            @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                            Model model, Locale locale, RedirectAttributes redirectAttributes,
                                            HttpServletResponse response) {
        return saveStoreCompanyDetails(getStoreId(), form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/company-details")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminUpdateStoreCompanyDetails(@PathVariable String storeId, @ModelAttribute CompanyDetailsForm form,
                                                      @RequestHeader(value = ASYNC_FORM_HEADER, required = false) String requestedWith,
                                                      Model model, Locale locale, RedirectAttributes redirectAttributes,
                                                      HttpServletResponse response) {
        return saveStoreCompanyDetails(storeId, form, ASYNC_FORM_HEADER_VALUE.equals(requestedWith), model, locale,
                redirectAttributes, response);
    }

    // A form sent by static/js/async-form.js gets only the re-rendered form back (422 with errors, 200 once saved) and
    // swaps it in place; a plain submit without JavaScript keeps the full page render and the redirect after saving.
    private String saveStoreCompanyDetails(String storeId, CompanyDetailsForm form, boolean async, Model model,
                                           Locale locale, RedirectAttributes redirectAttributes,
                                           HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        Store store = storesRepository.findById(storeId);
        if (store == null || !errors.isEmpty()) {
            String view = renderStoreCompanyDetails(storeId, form, errors, model, locale);
            if (async && store != null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return COMPANY_DETAILS_FORM_FRAGMENT;
            }
            return view;
        }

        store.setBillingDetails(form.applyTo(store.getBillingDetails()));
        storesRepository.save(store);
        String successMessage = messageSource.getMessage("store.company.details.update.success", null, locale);
        if (async) {
            renderStoreCompanyDetails(storeId, CompanyDetailsForm.from(store.getBillingDetails()), Map.of(), model, locale);
            model.addAttribute("savedMessage", successMessage);
            return COMPANY_DETAILS_FORM_FRAGMENT;
        }
        redirectAttributes.addFlashAttribute("successMessage", successMessage);
        return "redirect:" + companyDetailsPath(storeId);
    }

    private String companyDetailsPath(String storeId) {
        return isSuperAdmin()
                ? String.format("/dashboard/store/%s/company-details", storeId)
                : "/dashboard/store/company-details";
    }

    private String getStoreId() { return CustomSecurityContext.getStoreId(); }

    private boolean isSuperAdmin() { return CustomSecurityContext.hasRole("SUPER_ADMIN"); }
}
