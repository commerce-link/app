package pl.commercelink.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.templates.EmailAttachment;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplateForm;
import pl.commercelink.templates.EmailTemplatesRepository;
import pl.commercelink.starter.security.CustomSecurityContext;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class EmailTemplateController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private EmailTemplatesRepository emailTemplatesRepository;

    @GetMapping("/dashboard/store/email-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public String emailTemplatesAdmin(@RequestParam(value = "selectedType", required = false) String selectedType, Model model) {
        return renderEmailTemplates(getStoreId(), selectedType, false, model);
    }

    @GetMapping("/dashboard/store/{storeId}/email-templates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String emailTemplatesSuperAdmin(@PathVariable String storeId, @RequestParam(value = "selectedType", required = false) String selectedType, Model model) {
        return renderEmailTemplates(storeId, selectedType, true, model);
    }

    @PostMapping("/dashboard/store/email-templates/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveTemplatesAdmin(@ModelAttribute EmailTemplateForm form, @RequestParam("selectedType") String selectedType) {
        saveTemplates(getStoreId(), form);
        return "redirect:/dashboard/store/email-templates?selectedType=" + selectedType;
    }

    @PostMapping("/dashboard/store/{storeId}/email-templates/save")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String saveTemplatesSuperAdmin(@PathVariable String storeId, @ModelAttribute EmailTemplateForm form, @RequestParam("selectedType") String selectedType) {
        saveTemplates(storeId, form);
        return String.format("redirect:/dashboard/store/%s/email-templates?selectedType=%s", storeId, selectedType);
    }

    @PostMapping("/dashboard/store/email-templates/enable-notification")
    @PreAuthorize("hasRole('ADMIN')")
    public String enableNotificationAdmin(@RequestParam("type") String type) {
        enableNotification(getStoreId(), type);
        return "redirect:/dashboard/store/email-templates?selectedType=" + type;
    }

    @PostMapping("/dashboard/store/{storeId}/email-templates/enable-notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String enableNotificationSuperAdmin(@PathVariable String storeId, @RequestParam("type") String type) {
        enableNotification(storeId, type);
        return String.format("redirect:/dashboard/store/%s/email-templates?selectedType=%s", storeId, type);
    }

    @PostMapping("/dashboard/store/email-templates/disable-notification")
    @PreAuthorize("hasRole('ADMIN')")
    public String disableNotificationAdmin(@RequestParam("type") String type) {
        disableNotification(getStoreId(), type);
        return "redirect:/dashboard/store/email-templates?selectedType=" + type;
    }

    @PostMapping("/dashboard/store/{storeId}/email-templates/disable-notification")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String disableNotificationSuperAdmin(@PathVariable String storeId, @RequestParam("type") String type) {
        disableNotification(storeId, type);
        return String.format("redirect:/dashboard/store/%s/email-templates?selectedType=%s", storeId, type);
    }

    private String renderEmailTemplates(String storeId, String selectedType, boolean isSuperAdmin, Model model) {
        Store store = storesRepository.findById(storeId);
        List<EmailTemplate> emailTemplates = new LinkedList<>();
        ClientNotificationsConfiguration clientNotificationsConfiguration = store.getClientNotificationsConfiguration();

        if (clientNotificationsConfiguration == null) {
            clientNotificationsConfiguration = new ClientNotificationsConfiguration();
            store.setClientNotificationsConfiguration(clientNotificationsConfiguration);
        }

        for (EmailNotificationType type : EmailNotificationType.values()) {
            String templateName = store.getClientNotificationsConfiguration().getTemplateName(type);
            if (store.getClientNotificationsConfiguration().supports(type)) {
                EmailTemplate emailTemplate = emailTemplatesRepository.findByTemplateName(storeId, templateName);
                // If store template is not found, fallback to default template
                if (emailTemplate == null) {
                    emailTemplate = emailTemplatesRepository.findByTemplateName("default", templateName);
                }

                emailTemplate.setAttachments(ConversionUtil.join(emailTemplate.getAttachments(), Arrays.asList(new EmailAttachment(), new EmailAttachment(), new EmailAttachment())));
                emailTemplate.setBccAddresses(ConversionUtil.join(emailTemplate.getBccAddresses(), Arrays.asList("", "")));
                emailTemplates.add(emailTemplate);
            } else {
                emailTemplates.add(new EmailTemplate());
            }
        }

        model.addAttribute("form", new EmailTemplateForm(emailTemplates));
        model.addAttribute("store", store);
        model.addAttribute("notificationTypes", EmailNotificationType.values());
        model.addAttribute("selectedType", selectedType);
        model.addAttribute("isSuperAdmin", isSuperAdmin);

        return "emailTemplates";
    }

    private void saveTemplates(String storeId, EmailTemplateForm form) {
        Store store = storesRepository.findById(storeId);

        for (EmailTemplate templateInput : form.getEmailTemplates()) {
            if (!templateInput.isComplete()) continue;
            EmailTemplate existingStoreTemplate = emailTemplatesRepository.findByTemplateName(storeId, templateInput.getTemplateName());

            if (existingStoreTemplate == null) {
                // New store template
                EmailNotificationType notificationType = templateInput.getType();

                existingStoreTemplate = new EmailTemplate();
                existingStoreTemplate.setStoreId(storeId);
                existingStoreTemplate.setTemplateName(notificationType.getTemplateName());
                existingStoreTemplate.setType(notificationType);

                // Update store configuration
                store.getClientNotificationsConfiguration().enableNotification(existingStoreTemplate.getType(), notificationType.getTemplateName());
            }

            existingStoreTemplate.setSubject(templateInput.getSubject());
            existingStoreTemplate.setTextBody(templateInput.getTextBody());
            existingStoreTemplate.setAttachments(templateInput.getAttachments().stream()
                    .filter(EmailAttachment::isComplete)
                    .collect(Collectors.toList()));
            existingStoreTemplate.setBccAddresses(templateInput.getBccAddresses().stream()
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList()));

            emailTemplatesRepository.save(existingStoreTemplate);
        }

        storesRepository.save(store);
    }

    private void enableNotification(String storeId, String type) {
        EmailNotificationType notificationType = EmailNotificationType.valueOf(type);
        Store store = storesRepository.findById(storeId);

        if (store.getClientNotificationsConfiguration() == null) {
            store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        }

        // Assign default template if not yet configured
        store.getClientNotificationsConfiguration().enableNotification(notificationType, notificationType.getTemplateName());

        storesRepository.save(store);
    }

    private void disableNotification(String storeId, String type) {
        EmailNotificationType notificationType = EmailNotificationType.valueOf(type);

        Store store = storesRepository.findById(storeId);
        store.getClientNotificationsConfiguration().disableNotification(notificationType);
        storesRepository.save(store);
    }

    private String getStoreId() {
        return CustomSecurityContext.getStoreId();
    }
}
