package pl.commercelink.starter.email;

import pl.commercelink.templates.EmailTemplate;

public interface EmailTemplateProvider {
    EmailTemplate getTemplate(String storeId, String templateName);
}
