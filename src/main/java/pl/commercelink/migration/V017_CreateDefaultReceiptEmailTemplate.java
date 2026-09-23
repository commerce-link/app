package pl.commercelink.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;

/**
 * The shared default e-receipt e-mail: stores that switch e-receipts on send it without writing their own copy.
 * An existing default (e.g. edited by hand) is kept.
 */
@ChangeUnit(id = "V017-create-default-receipt-email-template", order = "017", author = "commercelink")
public class V017_CreateDefaultReceiptEmailTemplate {

    static final String SUBJECT = "E-paragon do zamówienia {{orderId}}";
    static final String BODY = """
            Dzień dobry,

            dziękujemy za zakupy. Paragon fiskalny do zamówienia {{orderId}} jest dostępny w formie elektronicznej:
            {{receiptUrl}}

            Zachowaj ten link — e-paragon jest dowodem zakupu, także przy reklamacji i zwrocie.
            """;

    private final EmailTemplatesRepository repository;

    public V017_CreateDefaultReceiptEmailTemplate(EmailTemplatesRepository repository) {
        this.repository = repository;
    }

    @Execution
    public void createTemplate() {
        String name = EmailNotificationType.ORDER_RECEIPT.getTemplateName();
        if (repository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, name) != null) {
            return;
        }
        EmailTemplate template = new EmailTemplate();
        template.setStoreId(EmailTemplatesRepository.DEFAULT_STORE);
        template.setTemplateName(name);
        template.setTypeName(EmailNotificationType.ORDER_RECEIPT.name());
        template.setSubject(SUBJECT);
        template.setTextBody(BODY);
        repository.save(template);
    }

    @RollbackExecution
    public void rollback() {}
}
