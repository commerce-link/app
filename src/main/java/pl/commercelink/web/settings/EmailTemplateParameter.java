package pl.commercelink.web.settings;

import pl.commercelink.orders.notifications.EmailNotificationType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A value an email template can use, as the text to paste into it. The type lists a list of items as
 * {@code "products (category, name, quantity, price)"}; in Mustache that is a section repeated for every item.
 */
public record EmailTemplateParameter(String name, String snippet) {

    private static final Pattern WITH_FIELDS = Pattern.compile("(\\w+)\\s*\\(([^)]*)\\)");

    public static List<EmailTemplateParameter> of(EmailNotificationType type) {
        return type.getParameters().stream().map(EmailTemplateParameter::parse).toList();
    }

    static EmailTemplateParameter parse(String parameter) {
        Matcher matcher = WITH_FIELDS.matcher(parameter.trim());
        if (!matcher.matches()) {
            return new EmailTemplateParameter(parameter.trim(), "{{" + parameter.trim() + "}}");
        }
        String name = matcher.group(1);
        StringBuilder fields = new StringBuilder();
        Arrays.stream(matcher.group(2).split(",")).map(String::trim).filter(field -> !field.isEmpty())
                .forEach(field -> fields.append(fields.isEmpty() ? "" : " ").append("{{").append(field).append("}}"));
        return new EmailTemplateParameter(name, "{{#" + name + "}}" + fields + "{{/" + name + "}}");
    }
}
