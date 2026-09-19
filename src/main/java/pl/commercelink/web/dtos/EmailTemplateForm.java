package pl.commercelink.web.dtos;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.templates.EmailAttachment;
import pl.commercelink.templates.EmailTemplate;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * One customer email: whether the store sends it, and its content. The subject and the body are Mustache templates
 * rendered when the email goes out ({@code EmailClient}), so a broken tag is caught here instead of failing that send.
 * Content is required only while the email is sent: switching an email off never asks for text. Hidden copies are one
 * comma-separated field; an attachment row is both a name and an address, or skipped when both are empty.
 */
@Getter
@Setter
public class EmailTemplateForm {

    private static final Pattern HTTP_URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private boolean enabled;
    private String subject;
    private String textBody;
    private String bccAddresses;
    private List<AttachmentRow> attachments = new ArrayList<>();

    /** The form of a type the store has no content for: nothing to show, sent only once the box is ticked. */
    public static EmailTemplateForm empty(boolean enabled) {
        EmailTemplateForm form = new EmailTemplateForm();
        form.enabled = enabled;
        return form;
    }

    public static EmailTemplateForm from(EmailTemplate template, boolean enabled) {
        EmailTemplateForm form = empty(enabled);
        if (template != null) {
            form.subject = template.getSubject();
            form.textBody = template.getTextBody();
            form.bccAddresses = String.join(", ", template.getBccAddresses() == null ? List.of() : template.getBccAddresses());
            if (template.getAttachments() != null) {
                template.getAttachments().forEach(attachment -> form.attachments.add(AttachmentRow.of(attachment)));
            }
        }
        return form;
    }

    public static String attachmentFieldId(int index, String field) {
        return "attachment-" + index + "-" + field;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (enabled) {
            FormRules.requireText(errors, "subject", subject, "store.emailTemplate.subject.required");
        }
        requireValidTemplate(errors, "subject", subject);
        if (enabled) {
            FormRules.requireText(errors, "textBody", textBody, "store.emailTemplate.body.required");
        }
        requireValidTemplate(errors, "textBody", textBody);
        if (bccList().stream().anyMatch(address -> !FormRules.isEmail(address))) {
            errors.put("bccAddresses", "store.emailTemplate.bcc.invalid");
        }
        for (int i = 0; i < attachments.size(); i++) {
            AttachmentRow row = attachments.get(i);
            if (row.blank()) {
                continue;
            }
            FormRules.requireText(errors, attachmentFieldId(i, "name"), row.name, "store.emailTemplate.attachment.name.required");
            if (FormRules.requireText(errors, attachmentFieldId(i, "url"), row.url, "store.emailTemplate.attachment.url.required")
                    && !HTTP_URL.matcher(row.url.trim()).matches()) {
                errors.put(attachmentFieldId(i, "url"), "store.emailTemplate.attachment.url.invalid");
            }
        }
        return errors;
    }

    /** Labels for the error summary: every attachment field is named with its attachment's number. */
    public Map<String, String> errorLabels(BiFunction<Integer, String, String> label) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < attachments.size(); i++) {
            for (String field : List.of("name", "url")) {
                labels.put(attachmentFieldId(i, field), label.apply(i + 1, field));
            }
        }
        return labels;
    }

    /** Whether the form carries any content; a type switched off with nothing written keeps no template. */
    public boolean hasContent() {
        return StringUtils.isNotBlank(subject) || StringUtils.isNotBlank(textBody) || !bccList().isEmpty()
                || attachments.stream().anyMatch(row -> !row.blank());
    }

    /** Whether hidden copies or attachments are set, which the page folds away otherwise. */
    public boolean hasExtras() {
        return !bccList().isEmpty() || attachments.stream().anyMatch(row -> !row.blank());
    }

    /** Whether the content equals the template's, so saving it would only copy that template into the store. */
    public boolean sameContentAs(EmailTemplate template) {
        if (template == null) {
            return false;
        }
        EmailTemplate candidate = new EmailTemplate();
        applyTo(candidate);
        return Objects.equals(candidate.getSubject(), StringUtils.trimToNull(template.getSubject()))
                && Objects.equals(candidate.getTextBody(), template.getTextBody())
                && candidate.getBccAddresses().equals(template.getBccAddresses() == null ? List.of() : template.getBccAddresses())
                && attachmentsOf(candidate).equals(attachmentsOf(template));
    }

    /** Writes the content into the template; its store, name and type stay as they were. */
    public void applyTo(EmailTemplate template) {
        template.setSubject(StringUtils.trimToNull(subject));
        template.setTextBody(StringUtils.isBlank(textBody) ? null : textBody);
        template.setBccAddresses(new ArrayList<>(bccList()));
        List<EmailAttachment> saved = new ArrayList<>();
        for (AttachmentRow row : attachments) {
            if (!row.blank()) {
                saved.add(new EmailAttachment(row.name.trim(), row.url.trim()));
            }
        }
        template.setAttachments(saved);
    }

    private List<String> bccList() {
        if (StringUtils.isBlank(bccAddresses)) {
            return List.of();
        }
        return Arrays.stream(bccAddresses.split("[,;\\s]+")).filter(StringUtils::isNotBlank).toList();
    }

    private static List<List<String>> attachmentsOf(EmailTemplate template) {
        return template.getAttachments() == null ? List.of() : template.getAttachments().stream()
                .map(attachment -> List.of(StringUtils.defaultString(attachment.getName()), StringUtils.defaultString(attachment.getUrl())))
                .toList();
    }

    private static void requireValidTemplate(Map<String, String> errors, String field, String value) {
        if (errors.containsKey(field) || StringUtils.isBlank(value)) {
            return;
        }
        try {
            new DefaultMustacheFactory().compile(new StringReader(value), field);
        } catch (MustacheException exception) {
            errors.put(field, "store.emailTemplate.syntax.invalid");
        }
    }

    @Getter
    @Setter
    public static class AttachmentRow {
        private String name;
        private String url;

        static AttachmentRow of(EmailAttachment attachment) {
            AttachmentRow row = new AttachmentRow();
            row.name = attachment.getName();
            row.url = attachment.getUrl();
            return row;
        }

        boolean blank() {
            return StringUtils.isAllBlank(name, url);
        }
    }
}
