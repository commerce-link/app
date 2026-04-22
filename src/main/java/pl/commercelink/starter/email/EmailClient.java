package pl.commercelink.starter.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.templates.EmailTemplate;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Collectors;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

@Service
public class EmailClient {

    private final SesV2Client sesClient;
    private final NotificationConfigProvider configProvider;
    private final EmailTemplateProvider templateProvider;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    @Value("${order.sender.mail:noreplay@commercelink.pl}")
    private String defaultSenderEmail;

    public EmailClient(SesV2Client sesClient, NotificationConfigProvider configProvider, EmailTemplateProvider templateProvider) {
        this.sesClient = sesClient;
        this.configProvider = configProvider;
        this.templateProvider = templateProvider;
    }

    public boolean send(String storeId, EmailNotificationType type, EmailNotification msg) {
        if (!configProvider.supports(storeId, type)) {
            return false;
        }

        ClientNotificationsConfiguration notificationsConfig = configProvider.getConfig(storeId);
        String templateName = notificationsConfig.getTemplateName(type);
        String replyToEmail = notificationsConfig.getReplyToEmail() != null ? notificationsConfig.getReplyToEmail() : defaultSenderEmail;

        return sendInternal(storeId, templateName, msg, defaultSenderEmail, notificationsConfig.getSenderName(), replyToEmail);
    }

    private boolean sendInternal(String storeId, String templateName, EmailNotification msg, String senderEmail, String senderName, String replyToEmail) {
        EmailTemplate template = templateProvider.getTemplate(storeId, templateName);
        if (template == null) return false;

        String subject = renderTemplate(template.getSubject(), msg);
        String body = renderTemplate(template.getTextBody(), msg);

        List<Attachment> attachments = template.getAttachments().stream()
                .map(EmailAttachmentBuilder::createAttachment)
                .collect(Collectors.toList());

        Attachment attachment = msg.createAttachment();
        if (attachment != null) attachments.add(attachment);

        EmailContent emailContent = EmailContent.builder()
                .simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .text(Content.builder().data(body).charset("UTF-8").build())
                                .build())
                        .attachments(attachments)
                        .build())
                .build();

        Destination destination = Destination.builder()
                .toAddresses(msg.getRecipientEmail())
                .bccAddresses(template.getBccAddresses())
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(String.format("\"%s\" <%s>", senderName, senderEmail))
                .destination(destination)
                .replyToAddresses(replyToEmail)
                .content(emailContent)
                .build();

        try {
            SendEmailResponse response = sesClient.sendEmail(request);
            return response.messageId() != null;
        } catch (SesV2Exception e) {
            System.err.println("Failed to send email: " + e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private String renderTemplate(String template, Object context) {
        Reader reader = new StringReader(template);
        Mustache mustache = mustacheFactory.compile(reader, "template");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context);
        return writer.toString();
    }

}