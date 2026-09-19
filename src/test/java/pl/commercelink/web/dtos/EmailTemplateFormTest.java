package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.templates.EmailAttachment;
import pl.commercelink.templates.EmailTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateFormTest {

    private static EmailTemplateForm form(boolean enabled, String subject, String body) {
        EmailTemplateForm form = EmailTemplateForm.empty(enabled);
        form.setSubject(subject);
        form.setTextBody(body);
        return form;
    }

    private static EmailTemplateForm.AttachmentRow attachment(String name, String url) {
        EmailTemplateForm.AttachmentRow row = new EmailTemplateForm.AttachmentRow();
        row.setName(name);
        row.setUrl(url);
        return row;
    }

    @Test
    void aSentEmailNeedsASubjectAndABody() {
        // when / then
        assertThat(form(true, " ", "").validate()).containsOnlyKeys("subject", "textBody");
    }

    @Test
    void anEmailSwitchedOffNeedsNoContent() {
        // when / then
        assertThat(form(false, null, null).validate()).isEmpty();
    }

    @Test
    void anUnclosedSectionIsCaughtBeforeTheEmailGoesOut() {
        // when / then
        assertThat(form(true, "Zamówienie {{orderId}}", "{{#products}}{{name}}").validate())
                .containsEntry("textBody", "store.emailTemplate.syntax.invalid");
    }

    @Test
    void hiddenCopiesAreCommaSeparatedValidAddresses() {
        // given
        EmailTemplateForm valid = form(true, "S", "B");
        valid.setBccAddresses("biuro@sklep.pl, magazyn@sklep.pl");
        EmailTemplateForm invalid = form(true, "S", "B");
        invalid.setBccAddresses("biuro@sklep.pl, magazyn");
        EmailTemplate template = new EmailTemplate();

        // when
        valid.applyTo(template);

        // then
        assertThat(valid.validate()).isEmpty();
        assertThat(template.getBccAddresses()).containsExactly("biuro@sklep.pl", "magazyn@sklep.pl");
        assertThat(invalid.validate()).containsEntry("bccAddresses", "store.emailTemplate.bcc.invalid");
    }

    @Test
    void anAttachmentIsANameAndAnHttpAddressOrABlankRowThatIsSkipped() {
        // given
        EmailTemplateForm form = form(true, "S", "B");
        form.setAttachments(new ArrayList<>(List.of(attachment("", ""), attachment("Regulamin", "ftp://x"),
                attachment(null, "https://sklep.pl/r.pdf"))));

        // when / then
        assertThat(form.validate()).containsExactly(
                java.util.Map.entry("attachment-1-url", "store.emailTemplate.attachment.url.invalid"),
                java.util.Map.entry("attachment-2-name", "store.emailTemplate.attachment.name.required"));
    }

    @Test
    void contentEqualToTheDefaultTemplateIsRecognised() {
        // given
        EmailTemplate defaults = new EmailTemplate();
        defaults.setSubject("Zamówienie {{orderId}}");
        defaults.setTextBody("Dziękujemy");
        defaults.setAttachments(new ArrayList<>(List.of(new EmailAttachment("Regulamin", "https://sklep.pl/r.pdf"))));
        EmailTemplateForm same = EmailTemplateForm.from(defaults, true);
        EmailTemplateForm changed = EmailTemplateForm.from(defaults, true);
        changed.setTextBody("Dziękujemy!");

        // when / then
        assertThat(same.sameContentAs(defaults)).isTrue();
        assertThat(changed.sameContentAs(defaults)).isFalse();
        assertThat(same.sameContentAs(null)).isFalse();
    }

    @Test
    void aBodySentWithWindowsLineEndingsEqualsTheSameDefaultWithUnixOnes() {
        // given
        EmailTemplate defaults = new EmailTemplate();
        defaults.setSubject("Temat");
        defaults.setTextBody("Dzień dobry,\nzamówienie {{orderId}}\nPozdrawiamy");
        EmailTemplateForm form = EmailTemplateForm.empty(true);
        form.setSubject("Temat");
        form.setTextBody("Dzień dobry,\r\nzamówienie {{orderId}}\r\nPozdrawiamy");

        // when / then
        assertThat(form.sameContentAs(defaults)).isTrue();
    }

    @Test
    void savedContentUsesUnixLineEndings() {
        // given
        EmailTemplateForm form = EmailTemplateForm.empty(true);
        form.setSubject("Temat");
        form.setTextBody("a\r\nb\rc");
        EmailTemplate template = new EmailTemplate();

        // when
        form.applyTo(template);

        // then
        assertThat(template.getTextBody()).isEqualTo("a\nb\nc");
    }

    @Test
    void aStoredBodyWithWindowsLineEndingsIsComparedByContent() {
        // given
        EmailTemplate stored = new EmailTemplate();
        stored.setSubject("Temat");
        stored.setTextBody("a\r\nb");
        EmailTemplateForm form = EmailTemplateForm.empty(true);
        form.setSubject("Temat");
        form.setTextBody("a\nb");

        // when / then
        assertThat(form.sameContentAs(stored)).isTrue();
    }
}
