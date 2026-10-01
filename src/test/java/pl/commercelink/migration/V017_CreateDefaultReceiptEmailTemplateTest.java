package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import pl.commercelink.templates.EmailTemplate;
import pl.commercelink.templates.EmailTemplatesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class V017_CreateDefaultReceiptEmailTemplateTest {

    @Test
    void createsTheDefaultTemplateWhenMissing() {
        EmailTemplatesRepository repository = mock(EmailTemplatesRepository.class);
        when(repository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, "OrderReceiptTemplate")).thenReturn(null);

        new V017_CreateDefaultReceiptEmailTemplate(repository).createTemplate();

        verify(repository).save(argThat((EmailTemplate t) -> t.getTextBody().contains("{{receiptUrl}}")
                && t.getSubject().contains("{{orderId}}")));
    }

    @Test
    void keepsAnExistingTemplate() {
        EmailTemplatesRepository repository = mock(EmailTemplatesRepository.class);
        when(repository.findByTemplateName(EmailTemplatesRepository.DEFAULT_STORE, "OrderReceiptTemplate")).thenReturn(new EmailTemplate());

        new V017_CreateDefaultReceiptEmailTemplate(repository).createTemplate();

        verify(repository, never()).save(any());
    }
}
