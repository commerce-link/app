package pl.commercelink.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.NotificationSenderForm;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.templates.EmailTemplatesRepository;
import pl.commercelink.web.settings.EmailTemplateView;
import pl.commercelink.web.settings.NotificationOverview;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Settings › Notifications. The store comes from the session (ADMIN) or the path (SUPER_ADMIN), never from the form. */
@Controller
@RequiredArgsConstructor
public class StoreNotificationSettingsController {

    private static final String VIEW = "store-notification";
    private static final String FORM_FRAGMENT = VIEW + " :: senderForm";

    private final StoresRepository storesRepository;
    private final EmailTemplatesRepository emailTemplatesRepository;
    private final MessageSource messageSource;

    @Value("${order.sender.mail:noreplay@commercelink.pl}")
    private String senderEmail;

    @GetMapping("/dashboard/store/notification")
    @PreAuthorize("hasRole('ADMIN')")
    public String notification(Model model) {
        return render(CustomSecurityContext.getStoreId(), null, Map.of(), model);
    }

    @GetMapping("/dashboard/store/{storeId}/notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminNotification(@PathVariable String storeId, Model model) {
        return render(storeId, null, Map.of(), model);
    }

    @PostMapping("/dashboard/store/notification")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveNotification(@ModelAttribute NotificationSenderForm form,
                                   @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                   Model model, Locale locale, RedirectAttributes redirectAttributes,
                                   HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, response);
    }

    @PostMapping("/dashboard/store/{storeId}/notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveNotification(@PathVariable String storeId, @ModelAttribute NotificationSenderForm form,
                                             @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                             Model model, Locale locale, RedirectAttributes redirectAttributes,
                                             HttpServletResponse response) {
        return save(storeId, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, response);
    }

    private String save(String storeId, NotificationSenderForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletResponse response) {
        Map<String, String> errors = form.validate();
        Store store = storesRepository.findById(storeId);
        if (store == null || !errors.isEmpty()) {
            String view = render(storeId, form, errors, model);
            if (async && store != null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        form.applyTo(store);
        storesRepository.save(store);
        String successMessage = messageSource.getMessage("store.notification.update.success", null, locale);
        if (async) {
            render(storeId, NotificationSenderForm.from(store), Map.of(), model);
            model.addAttribute("savedMessage", successMessage);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, successMessage);
        return "redirect:" + SettingsPaths.store(storeId, "/notification");
    }

    private String render(String storeId, NotificationSenderForm submitted, Map<String, String> errors, Model model) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        NotificationSenderForm form = submitted != null ? submitted : NotificationSenderForm.from(store);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("formAction", SettingsPaths.store(storeId, "/notification"));
        model.addAttribute("senderPreviewName", StringUtils.isNotBlank(form.getSenderName()) && !errors.containsKey("senderName")
                ? form.getSenderName().trim()
                : store.getName());
        model.addAttribute("senderEmail", senderEmail);
        String templatesHref = SettingsPaths.store(storeId, "/email-templates");
        model.addAttribute("templatesHref", templatesHref);
        model.addAttribute("fulfilmentHref", SettingsPaths.store(storeId, "/fulfilment") + "#client-order-page");
        ClientNotificationsConfiguration configuration = store.getClientNotificationsConfiguration() != null
                ? store.getClientNotificationsConfiguration() : new ClientNotificationsConfiguration();
        List<EmailTemplateView> emails = EmailTemplateView.forStore(configuration,
                        emailTemplatesRepository.findAllOfStore(storeId),
                        emailTemplatesRepository.findAllOfStore(EmailTemplatesRepository.DEFAULT_STORE), templatesHref)
                .stream().flatMap(group -> group.items().stream()).toList();
        model.addAttribute("overview", NotificationOverview.of(store, emails));
        return VIEW;
    }
}
