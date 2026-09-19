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
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;
import pl.commercelink.web.dtos.EmailTemplateForm;
import pl.commercelink.web.settings.ConfirmAction;
import pl.commercelink.web.settings.EmailTemplateParameter;
import pl.commercelink.web.settings.EmailTemplateView;
import pl.commercelink.web.settings.SettingsFlash;
import pl.commercelink.web.settings.SettingsPaths;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Settings › Email templates: the emails a store sends to its customers. The page lists every type; each is edited on
 * its own page, whose save writes that one template only. A store without its own copy of a template sends the shared
 * default one ({@link EmailTemplatesRepository#DEFAULT_STORE}); saving content equal to it keeps it that way, and
 * "restore" deletes the store's copy. The store comes from the session (ADMIN) or the path (SUPER_ADMIN).
 */
@Controller
@RequiredArgsConstructor
public class EmailTemplateController {

    private static final String LIST_VIEW = "store-email-templates";
    private static final String VIEW = "store-email-template";
    private static final String FORM_FRAGMENT = VIEW + " :: templateForm";

    private final StoresRepository storesRepository;
    private final EmailTemplatesRepository emailTemplatesRepository;
    private final MessageSource messageSource;

    @GetMapping("/dashboard/store/email-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public String templates(@RequestParam(value = "selectedType", required = false) String selectedType, Model model) {
        return list(CustomSecurityContext.getStoreId(), selectedType, model);
    }

    @GetMapping("/dashboard/store/{storeId}/email-templates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminTemplates(@PathVariable String storeId,
                                      @RequestParam(value = "selectedType", required = false) String selectedType, Model model) {
        return list(storeId, selectedType, model);
    }

    @GetMapping("/dashboard/store/email-templates/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editTemplate(@PathVariable String type, Model model, Locale locale) {
        return showEdit(CustomSecurityContext.getStoreId(), type, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/email-templates/{type}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminEditTemplate(@PathVariable String storeId, @PathVariable String type, Model model, Locale locale) {
        return showEdit(storeId, type, model, locale);
    }

    @PostMapping("/dashboard/store/email-templates/{type}")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveTemplate(@PathVariable String type, @ModelAttribute EmailTemplateForm form,
                               @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                               Model model, Locale locale, RedirectAttributes redirectAttributes,
                               HttpServletRequest request, HttpServletResponse response) {
        return save(CustomSecurityContext.getStoreId(), type, form, SettingsPaths.isAsync(requestedWith), model, locale,
                redirectAttributes, request, response);
    }

    @PostMapping("/dashboard/store/{storeId}/email-templates/{type}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminSaveTemplate(@PathVariable String storeId, @PathVariable String type,
                                         @ModelAttribute EmailTemplateForm form,
                                         @RequestHeader(value = SettingsPaths.ASYNC_HEADER, required = false) String requestedWith,
                                         Model model, Locale locale, RedirectAttributes redirectAttributes,
                                         HttpServletRequest request, HttpServletResponse response) {
        return save(storeId, type, form, SettingsPaths.isAsync(requestedWith), model, locale, redirectAttributes, request,
                response);
    }

    @GetMapping("/dashboard/store/email-templates/{type}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public String confirmRestore(@PathVariable String type, Model model, Locale locale) {
        return confirmRestore(CustomSecurityContext.getStoreId(), type, model, locale);
    }

    @GetMapping("/dashboard/store/{storeId}/email-templates/{type}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminConfirmRestore(@PathVariable String storeId, @PathVariable String type, Model model, Locale locale) {
        return confirmRestore(storeId, type, model, locale);
    }

    @PostMapping("/dashboard/store/email-templates/{type}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public String restoreDefault(@PathVariable String type, Locale locale, RedirectAttributes redirectAttributes) {
        return restore(CustomSecurityContext.getStoreId(), type, locale, redirectAttributes);
    }

    @PostMapping("/dashboard/store/{storeId}/email-templates/{type}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String superAdminRestoreDefault(@PathVariable String storeId, @PathVariable String type, Locale locale,
                                           RedirectAttributes redirectAttributes) {
        return restore(storeId, type, locale, redirectAttributes);
    }

    private String list(String storeId, String selectedType, Model model) {
        Store store = requireStore(storeId);
        // Links from before the list had one page per type.
        EmailNotificationType selected = typeOrNull(selectedType);
        if (selected != null) {
            return "redirect:" + typePath(storeId, selected);
        }
        ClientNotificationsConfiguration configuration = configurationOf(store);
        Map<String, EmailTemplate> own = byName(emailTemplatesRepository.findAllOfStore(storeId));
        Map<String, EmailTemplate> defaults = byName(emailTemplatesRepository.findAllOfStore(EmailTemplatesRepository.DEFAULT_STORE));
        List<EmailTemplateView.Group> groups = EmailTemplateView.groups(configuration::supports,
                type -> templateName(configuration, type), own, defaults, basePath(storeId));
        List<EmailTemplateView> all = groups.stream().flatMap(group -> group.items().stream()).toList();
        model.addAttribute("groups", groups);
        model.addAttribute("enabledCount", all.stream().filter(EmailTemplateView::enabled).count());
        model.addAttribute("totalCount", all.size());
        model.addAttribute("anyBroken", all.stream().anyMatch(EmailTemplateView::broken));
        return LIST_VIEW;
    }

    private String showEdit(String storeId, String typeName, Model model, Locale locale) {
        Store store = requireStore(storeId);
        EmailNotificationType type = requireType(typeName);
        ClientNotificationsConfiguration configuration = configurationOf(store);
        String name = templateName(configuration, type);
        EmailTemplate own = emailTemplatesRepository.findByTemplateName(storeId, name);
        EmailTemplate fallback = emailTemplatesRepository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, name);
        EmailTemplateForm form = EmailTemplateForm.from(own != null ? own : fallback, configuration.supports(type));
        return render(storeId, type, form, Map.of(), own, fallback, model, locale);
    }

    private String save(String storeId, String typeName, EmailTemplateForm form, boolean async, Model model, Locale locale,
                        RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        Store store = requireStore(storeId);
        EmailNotificationType type = requireType(typeName);
        ClientNotificationsConfiguration configuration = configurationOf(store);
        String name = templateName(configuration, type);
        EmailTemplate own = emailTemplatesRepository.findByTemplateName(storeId, name);
        EmailTemplate fallback = emailTemplatesRepository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, name);

        Map<String, String> errors = form.validate();
        if (!errors.isEmpty()) {
            String view = render(storeId, type, form, errors, own, fallback, model, locale);
            if (async) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                return FORM_FRAGMENT;
            }
            return view;
        }

        // Content equal to the default one is not copied into the store, so it keeps getting the default's updates.
        boolean keepsDefault = own == null && (form.sameContentAs(fallback) || !form.hasContent());
        if (!keepsDefault) {
            EmailTemplate template = own != null ? own : newTemplate(storeId, name, type);
            form.applyTo(template);
            emailTemplatesRepository.save(template);
            own = template;
        }
        if (form.isEnabled()) {
            configuration.enableNotification(type, name);
        } else {
            configuration.disableNotification(type);
        }
        storesRepository.save(store);

        String listPath = basePath(storeId);
        String message = messageSource.getMessage(form.isEnabled() ? "store.emailTemplate.saved" : "store.emailTemplate.saved.off",
                new Object[]{label(type, locale)}, locale);
        if (async) {
            SettingsFlash.forNextPage(request, response, listPath, message);
            render(storeId, type, form, Map.of(), own, fallback, model, locale);
            model.addAttribute("redirectTo", listPath);
            return FORM_FRAGMENT;
        }
        SettingsFlash.onRedirect(redirectAttributes, message);
        return "redirect:" + listPath;
    }

    private String confirmRestore(String storeId, String typeName, Model model, Locale locale) {
        EmailNotificationType type = requireType(typeName);
        requireStore(storeId);
        model.addAttribute("confirm", new ConfirmAction(
                messageSource.getMessage("store.emailTemplate.restore.title", new Object[]{label(type, locale)}, locale),
                messageSource.getMessage("store.emailTemplate.restore.message", null, locale),
                messageSource.getMessage("store.emailTemplate.restore.action", null, locale),
                typePath(storeId, type) + "/restore",
                typePath(storeId, type)));
        model.addAttribute("backLabel", label(type, locale));
        return "settings-confirm";
    }

    private String restore(String storeId, String typeName, Locale locale, RedirectAttributes redirectAttributes) {
        EmailNotificationType type = requireType(typeName);
        String name = templateName(configurationOf(requireStore(storeId)), type);
        EmailTemplate own = emailTemplatesRepository.findByTemplateName(storeId, name);
        // Without a default there would be nothing left to send, so the store's copy stays.
        if (own != null && emailTemplatesRepository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, name) != null) {
            emailTemplatesRepository.delete(own);
            SettingsFlash.onRedirect(redirectAttributes, messageSource.getMessage("store.emailTemplate.restored",
                    new Object[]{label(type, locale)}, locale));
        }
        return "redirect:" + typePath(storeId, type);
    }

    private String render(String storeId, EmailNotificationType type, EmailTemplateForm form, Map<String, String> errors,
                          EmailTemplate own, EmailTemplate fallback, Model model, Locale locale) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("errorLabels", form.errorLabels((number, field) -> messageSource.getMessage(
                "store.emailTemplate.attachment.field", new Object[]{number,
                        messageSource.getMessage("store.emailTemplate.attachment." + field, null, locale)}, locale)));
        model.addAttribute("formAction", typePath(storeId, type));
        model.addAttribute("pageTitle", label(type, locale));
        model.addAttribute("source", own != null ? EmailTemplateView.Source.OWN
                : fallback != null ? EmailTemplateView.Source.DEFAULT : EmailTemplateView.Source.NONE);
        model.addAttribute("restorable", own != null && fallback != null);
        model.addAttribute("restoreHref", typePath(storeId, type) + "/restore");
        model.addAttribute("parameters", EmailTemplateParameter.of(type));
        model.addAttribute("extrasOpen", form.hasExtras()
                || errors.keySet().stream().anyMatch(field -> field.equals("bccAddresses") || field.startsWith("attachment-")));
        model.addAttribute("listHref", basePath(storeId));
        model.addAttribute("backLabel", messageSource.getMessage("nav.emailTemplates", null, locale));
        return VIEW;
    }

    private static EmailTemplate newTemplate(String storeId, String name, EmailNotificationType type) {
        EmailTemplate template = new EmailTemplate();
        template.setStoreId(storeId);
        template.setTemplateName(name);
        template.setType(type);
        return template;
    }

    private static ClientNotificationsConfiguration configurationOf(Store store) {
        if (store.getClientNotificationsConfiguration() == null) {
            store.setClientNotificationsConfiguration(new ClientNotificationsConfiguration());
        }
        return store.getClientNotificationsConfiguration();
    }

    /** The template a store sends a type with: the one it points at, or the type's own name while it is switched off. */
    private static String templateName(ClientNotificationsConfiguration configuration, EmailNotificationType type) {
        String name = configuration.getTemplateName(type);
        return name != null ? name : type.getTemplateName();
    }

    private static Map<String, EmailTemplate> byName(List<EmailTemplate> templates) {
        return templates.stream().collect(Collectors.toMap(EmailTemplate::getTemplateName, Function.identity(), (a, b) -> a));
    }

    private String label(EmailNotificationType type, Locale locale) {
        return messageSource.getMessage(EmailTemplateView.labelKey(type), null, type.name(), locale);
    }

    private static String basePath(String storeId) {
        return SettingsPaths.store(storeId, "/email-templates");
    }

    private static String typePath(String storeId, EmailNotificationType type) {
        return basePath(storeId) + "/" + type.name();
    }

    private static EmailNotificationType typeOrNull(String name) {
        return Arrays.stream(EmailNotificationType.values()).filter(type -> type.name().equals(name)).findFirst().orElse(null);
    }

    private static EmailNotificationType requireType(String name) {
        EmailNotificationType type = typeOrNull(name);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return type;
    }

    private Store requireStore(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return store;
    }
}
