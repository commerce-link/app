package pl.commercelink.stores.lifecycle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.DeactivationStatus;
import pl.commercelink.stores.Store;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The e-mail telling the owner of a store that its trial ended: the store is now read-only and when its data goes.
 * Sent by CommerceLink itself, not in the store's name, and in Polish, the platform's primary language.
 */
@Component
class TrialEndedNotice {

    private static final Locale LOCALE = Locale.forLanguageTag("pl");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SENDER_NAME = "CommerceLink";
    private static final String CHARSET = "UTF-8";

    private final SesV2Client sesClient;
    private final MessageSource messageSource;
    private final String senderEmail;
    private final String contactEmail;

    TrialEndedNotice(SesV2Client sesClient, MessageSource messageSource,
                     @Value("${order.sender.mail:noreplay@commercelink.pl}") String senderEmail,
                     @Value("${app.registration.trial-contact-email:}") String contactEmail) {
        this.sesClient = sesClient;
        this.messageSource = messageSource;
        this.senderEmail = senderEmail;
        this.contactEmail = contactEmail;
    }

    /** Throws when SES does not take the e-mail, so the sweep sends it again next time. */
    void send(Store store, DeactivationStatus status) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(SENDER_NAME + " <" + senderEmail + ">")
                .destination(Destination.builder().toAddresses(store.getTrial().getOwnerEmail()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(text(message("store.trial-ended.mail.subject")))
                                .body(Body.builder().text(text(body(store, status))).build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
    }

    private String body(Store store, DeactivationStatus status) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(message("store.trial-ended.mail.body", store.getName(),
                DATE.format(status.deactivatedOn()), DATE.format(status.deletionOn())));
        if (!contactEmail.isBlank()) {
            paragraphs.add(message("store.trial-ended.mail.contact", contactEmail));
        }
        paragraphs.add(message("store.trial-ended.mail.signature"));
        return String.join("\n\n", paragraphs);
    }

    private String message(String key, Object... arguments) {
        return messageSource.getMessage(key, arguments, LOCALE);
    }

    private static Content text(String data) {
        return Content.builder().data(data).charset(CHARSET).build();
    }
}
