package pl.commercelink.starter.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.templates.EmailTemplate;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailClientTest {

    private static final String SENDER = "noreply@commercelink.pl";

    @Mock
    private SesV2Client sesClient;

    @Mock
    private NotificationConfigProvider configProvider;

    @Mock
    private EmailTemplateProvider templateProvider;

    @InjectMocks
    private EmailClient emailClient;

    private ClientNotificationsConfiguration configuration;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailClient, "defaultSenderEmail", SENDER);
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        configuration.enableNotification(EmailNotificationType.ORDER_SHIPPING, "OrderShippingTemplate");
        this.configuration = configuration;
        EmailTemplate template = new EmailTemplate();
        template.setSubject("Wysłane");
        template.setTextBody("Paczka w drodze");
        when(templateProvider.getTemplate("store-1", "OrderShippingTemplate")).thenReturn(template);
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().messageId("m-1").build());
    }

    private SendEmailRequest sentRequest() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        return captor.getValue();
    }

    @Test
    void signsTheEmailWithTheStoreSenderNameAndRepliesGoToTheStore() {
        // given
        when(configProvider.settings("store-1")).thenReturn(new NotificationSettings(configuration, "Sklep Demo", "kontakt@sklep-demo.pl"));

        // when
        boolean sent = emailClient.send("store-1", EmailNotificationType.ORDER_SHIPPING, new EmailNotification("klient@example.com", "Jan"));

        // then
        assertThat(sent).isTrue();
        assertThat(sentRequest().fromEmailAddress()).isEqualTo("\"Sklep Demo\" <noreply@commercelink.pl>");
        assertThat(sentRequest().replyToAddresses()).containsExactly("kontakt@sklep-demo.pl");
    }

    @Test
    void sendsFromTheBareAddressInsteadOfTheWordNullWhenThereIsNoSenderName() {
        // given
        when(configProvider.settings("store-1")).thenReturn(new NotificationSettings(configuration, null, null));

        // when
        emailClient.send("store-1", EmailNotificationType.ORDER_SHIPPING, new EmailNotification("klient@example.com", "Jan"));

        // then
        assertThat(sentRequest().fromEmailAddress()).isEqualTo(SENDER);
        assertThat(sentRequest().replyToAddresses()).containsExactly(SENDER);
    }

    @Test
    void aTypeTheStoreDoesNotSendIsSkippedWithoutCallingSes() {
        // given
        when(configProvider.settings("store-1")).thenReturn(new NotificationSettings(configuration, "Sklep Demo", null));

        // when
        boolean sent = emailClient.send("store-1", EmailNotificationType.RMA_REJECTED, new EmailNotification("klient@example.com", "Jan"));

        // then
        assertThat(sent).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sesClient);
    }

    @Test
    void aTemplateWithoutABodyIsNotSentInsteadOfFailing() {
        // given
        EmailTemplate withoutBody = new EmailTemplate();
        withoutBody.setSubject("Wysłane");
        when(templateProvider.getTemplate("store-1", "OrderShippingTemplate")).thenReturn(withoutBody);
        when(configProvider.settings("store-1")).thenReturn(new NotificationSettings(configuration, "Sklep Demo", null));

        // when
        boolean sent = emailClient.send("store-1", EmailNotificationType.ORDER_SHIPPING, new EmailNotification("klient@example.com", "Jan"));

        // then
        assertThat(sent).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sesClient);
    }

    @Test
    void aTemplateWithABlankSubjectIsNotSent() {
        // given
        EmailTemplate withoutSubject = new EmailTemplate();
        withoutSubject.setSubject("  ");
        withoutSubject.setTextBody("Paczka w drodze");
        when(templateProvider.getTemplate("store-1", "OrderShippingTemplate")).thenReturn(withoutSubject);
        when(configProvider.settings("store-1")).thenReturn(new NotificationSettings(configuration, "Sklep Demo", null));

        // when
        boolean sent = emailClient.send("store-1", EmailNotificationType.ORDER_SHIPPING, new EmailNotification("klient@example.com", "Jan"));

        // then
        assertThat(sent).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sesClient);
    }
}
