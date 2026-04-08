package pl.commercelink.starter.email;

import org.springframework.stereotype.Component;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;

@Component
public class DefaultEmailTemplateProvider implements EmailTemplateProvider {

    private final EmailTemplatesRepository emailTemplatesRepository;

    public DefaultEmailTemplateProvider(EmailTemplatesRepository emailTemplatesRepository) {
        this.emailTemplatesRepository = emailTemplatesRepository;
    }

    @Override
    public EmailTemplate getTemplate(String storeId, String templateName) {
        EmailTemplate template = emailTemplatesRepository.findByTemplateName(storeId, templateName);
        if (template == null) {
            template = emailTemplatesRepository.findByTemplateName("default", templateName);
        }
        return template;
    }
}
