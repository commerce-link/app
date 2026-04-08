package pl.commercelink.templates;

import java.util.LinkedList;
import java.util.List;

public class EmailTemplateForm {
    private List<EmailTemplate> emailTemplates = new LinkedList<>();

    public EmailTemplateForm() {
    }

    public EmailTemplateForm(List<EmailTemplate> emailTemplates) {
        this.emailTemplates = emailTemplates;
    }

    public List<EmailTemplate> getEmailTemplates() {
        return emailTemplates;
    }

    public void setEmailTemplates(List<EmailTemplate> emailTemplates) {
        this.emailTemplates = emailTemplates;
    }
}
