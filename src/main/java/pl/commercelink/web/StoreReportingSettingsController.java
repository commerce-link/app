package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pl.commercelink.financials.GoogleOfflineConversionsExport;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ReportingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ReportingForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Locale;
import java.util.Map;

/** Settings › Reporting. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), never from the form. */
@Controller
@RequiredArgsConstructor
public class StoreReportingSettingsController {

    public record ConversionsAddress(String url, String masked) {
    }

    private static final String VIEW = "store-report";
    private static final String FORM_FRAGMENT = VIEW + " :: reportingForm";
    private static final String NEW_ADDRESS_PATH = "/report/google-ads/new-address";
    private static final String REPORTS_PATH = "/dashboard/reports";
    private static final int VISIBLE_TOKEN_CHARACTERS = 5;
    // Flash attribute: the redirect after the first switch-on (no JavaScript) opens the setup instructions.
    private static final String SETUP_OPEN_FLASH = "googleAdsSetupOpen";

    private final StoresRepository storesRepository;
    private final MessageSource messageSource;

    @Value("${api.domain}")
    private String apiDomain;

    @GetMapping("/dashboard/store/report")
    @PreAuthorize("hasRole('ADMIN')")
    public String report(Model model) {
        return render(CustomSecurityContext.getStoreId(), null, model);
    }

    @GetMapping("/dashboard/store/{storeId}/report")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminReport(@PathVariable String storeId, Model model) {
        return render(storeId, null, model);
    }

    @PostMapping("/dashboard/store/report")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveReport(@ModelAttribute ReportingForm form,
                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/report")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveReport(@PathVariable String storeId, @ModelAttribute ReportingForm form,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes,
                                       HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes);
    }

    @GetMapping("/dashboard/store" + NEW_ADDRESS_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmNewAddress(Model model, Locale locale) {
        return confirmNewAddress(CustomSecurityContext.getStoreId(), model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}" + NEW_ADDRESS_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmNewAddress(@PathVariable String storeId, Model model, Locale locale) {
        return confirmNewAddress(storeId, model, locale);
    }

    @PostMapping("/dashboard/store" + NEW_ADDRESS_PATH)
    @PreAuthorize("hasRole('ADMIN')")
    public String newAddress(@RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                             Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return newAddress(CustomSecurityContext.getStoreId(), SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}" + NEW_ADDRESS_PATH)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNewAddress(@PathVariable String storeId,
                                       @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                       Model model, Locale locale, RedirectAttributes redirectAttributes) {
        return newAddress(storeId, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes);
    }

    private String save(String storeId, ReportingForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return render(storeId, form, model);
        }
        boolean hadAddress = conversionsAddress(store) != null;
        form.applyTo(store);
        storesRepository.save(store);
        // The instructions are needed once: when the switch-on has just created the address to paste into Google Ads.
        boolean setupOpen = !hadAddress && form.isGoogleAdsEnabled();
        String successMessage = messageSource.getMessage(form.isGoogleAdsEnabled()
                ? "store.reporting.googleAds.switchedOn" : "store.reporting.googleAds.switchedOff", null, locale);
        if (async) {
            render(storeId, ReportingForm.from(store), model);
            model.addAttribute("setupOpen", setupOpen);
            model.addAttribute("savedMessage", successMessage);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, successMessage);
        if (setupOpen) {
            redirectAttributes.addFlashAttribute(SETUP_OPEN_FLASH, true);
        }
        return "redirect:" + SettingsPaths.store(storeId, "/report");
    }

    private String confirmNewAddress(String storeId, Model model, Locale locale) {
        if (storesRepository.findById(storeId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.reporting.googleAds.newAddress.confirm.title", null, locale),
                messageSource.getMessage("store.reporting.googleAds.newAddress.confirm.message", null, locale),
                messageSource.getMessage("store.reporting.googleAds.newAddress.confirm.action", null, locale),
                SettingsPaths.store(storeId, NEW_ADDRESS_PATH),
                SettingsPaths.store(storeId, "/report")));
        model.addAttribute("backLabel", messageSource.getMessage("store.reporting", null, locale));
        return "settings-confirm";
    }

    private String newAddress(String storeId, boolean async, Model model, Locale locale, RedirectAttributes redirectAttributes) {
        Store store = storesRepository.findById(storeId);
        ReportingConfiguration configuration = store != null ? store.getReportingConfiguration() : null;
        String successMessage = null;
        if (configuration != null && StringUtils.isNotBlank(configuration.getGoogleAdsToken())) {
            configuration.regenerateGoogleAdsToken();
            storesRepository.save(store);
            successMessage = messageSource.getMessage("store.reporting.googleAds.newAddress.success", null, locale);
        }
        if (async) {
            // Confirmed in the dialog: the card is swapped in place with the new address, like a switch change.
            String view = render(storeId, null, model);
            model.addAttribute("savedMessage", successMessage);
            return VIEW.equals(view) ? FORM_FRAGMENT : view;
        }
        if (successMessage != null) {
            SettingsFlash.onRedirect(redirectAttributes, successMessage);
        }
        return "redirect:" + SettingsPaths.store(storeId, "/report");
    }

    private String render(String storeId, ReportingForm submitted, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ReportingForm form = submitted != null ? submitted : ReportingForm.from(store);
        model.addAttribute("form", form);
        model.addAttribute("errors", Map.of());
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/report"));
        model.addAttribute("conversionsAddress", form.isGoogleAdsEnabled() ? conversionsAddress(store) : null);
        model.addAttribute("setupOpen", Boolean.TRUE.equals(model.getAttribute(SETUP_OPEN_FLASH)));
        model.addAttribute("newAddressHref", SettingsPaths.store(storeId, NEW_ADDRESS_PATH));
        model.addAttribute("conversionName", GoogleOfflineConversionsExport.CONVERSION_NAME);
        // Reports export the store of the signed-in session, so a super admin viewing another store gets no link there.
        if (!CustomSecurityContext.hasRole("SUPER_ADMIN")) {
            model.addAttribute("reportsHref", REPORTS_PATH);
        }
        return VIEW;
    }

    private ConversionsAddress conversionsAddress(Store store) {
        ReportingConfiguration configuration = store.getReportingConfiguration();
        String token = configuration != null ? configuration.getGoogleAdsToken() : null;
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String prefix = StringUtils.removeEnd(apiDomain, "/") + "/Store/" + store.getStoreId() + "/Reporting/Google/Conversions/";
        String visible = token.substring(Math.max(0, token.length() - VISIBLE_TOKEN_CHARACTERS));
        return new ConversionsAddress(prefix + token, prefix + "••••••••" + visible);
    }
}
