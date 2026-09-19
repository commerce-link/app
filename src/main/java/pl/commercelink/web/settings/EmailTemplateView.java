package pl.commercelink.web.settings;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.templates.EmailTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One customer email on the templates page: whether it is sent and where its content comes from. A store without its
 * own copy gets the shared default template when the email goes out; with neither, nothing is sent
 * ({@code EmailClient} gives up quietly), which the list says instead.
 */
public record EmailTemplateView(EmailNotificationType type, String labelKey, boolean enabled, Source source,
                                String subject, String editHref) {

    public enum Source { OWN, DEFAULT, NONE }

    public record Group(String labelKey, List<EmailTemplateView> items) {
    }

    public static EmailTemplateView of(EmailNotificationType type, boolean enabled, EmailTemplate own,
                                       EmailTemplate fallback, String basePath) {
        EmailTemplate content = own != null ? own : fallback;
        Source source = own != null ? Source.OWN : fallback != null ? Source.DEFAULT : Source.NONE;
        return new EmailTemplateView(type, labelKey(type), enabled, source, content == null ? null : content.getSubject(),
                basePath + "/" + type.name());
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

    /** Sent to customers, yet there is no content to send. */
    public boolean broken() {
        return enabled && source == Source.NONE;
    }
}
