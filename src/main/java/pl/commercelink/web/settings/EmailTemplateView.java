package pl.commercelink.web.settings;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.templates.EmailTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * One customer email on the templates page: whether it is sent and where its content comes from. A store without its
 * own copy gets the shared default template when the email goes out; with neither, nothing is sent
 * ({@code EmailClient} logs and skips it), which the list says instead. A copy without a subject or body counts as no
 * content.
 */
public record EmailTemplateView(EmailNotificationType type, String labelKey, boolean enabled, Source source,
                                String subject, boolean complete, String editHref) {

    public enum Source { OWN, DEFAULT, NONE }

    public record Group(String labelKey, List<EmailTemplateView> items) {
    }

    public static EmailTemplateView of(EmailNotificationType type, boolean enabled, EmailTemplate own,
                                       EmailTemplate fallback, String basePath) {
        EmailTemplate content = own != null ? own : fallback;
        Source source = own != null ? Source.OWN : fallback != null ? Source.DEFAULT : Source.NONE;
        boolean complete = content != null && isNotBlank(content.getSubject()) && isNotBlank(content.getTextBody());
        return new EmailTemplateView(type, labelKey(type), enabled, source, content == null ? null : content.getSubject(),
                complete, basePath + "/" + type.name());
    }

    /**
     * The groups of one store, from its own templates and the shared default ones (one query each). Shared by the
     * templates list and the notifications summary, so both count the same emails as sent.
     */
    public static List<Group> forStore(ClientNotificationsConfiguration configuration, List<EmailTemplate> own,
                                       List<EmailTemplate> defaults, String basePath) {
        return groups(configuration::supports, type -> templateName(configuration, type), byName(own), byName(defaults),
                basePath);
    }

    /** The template a store sends a type with: the one it points at, or the type's own name while it is switched off. */
    public static String templateName(ClientNotificationsConfiguration configuration, EmailNotificationType type) {
        String name = configuration.getTemplateName(type);
        return name != null ? name : type.getTemplateName();
    }

    private static Map<String, EmailTemplate> byName(List<EmailTemplate> templates) {
        return templates.stream().collect(Collectors.toMap(EmailTemplate::getTemplateName, Function.identity(), (a, b) -> a));
    }

    /**
     * @param templateName the template name the store sends a type with (its own choice, or the type's default name)
     * @param own          the store's templates by name
     * @param defaults     the shared default templates by name
     */
    public static List<Group> groups(Function<EmailNotificationType, Boolean> enabled,
                                     Function<EmailNotificationType, String> templateName,
                                     Map<String, EmailTemplate> own, Map<String, EmailTemplate> defaults, String basePath) {
        List<Group> groups = new ArrayList<>();
        for (NotificationOverview.GroupDefinition definition : NotificationOverview.GROUPS) {
            groups.add(new Group(definition.labelKey(), definition.types().stream()
                    .map(type -> of(type, enabled.apply(type), own.get(templateName.apply(type)),
                            defaults.get(templateName.apply(type)), basePath))
                    .toList()));
        }
        return groups;
    }

    public static String labelKey(EmailNotificationType type) {
        return "email.notification.type." + type.name();
    }

    /** Switched on and has a subject and a body, so it actually goes out ({@code EmailClient} skips anything less). */
    public boolean sent() {
        return enabled && complete;
    }

    /** Sent to customers, yet there is no content to send. */
    public boolean broken() {
        return enabled && !complete;
    }
}
