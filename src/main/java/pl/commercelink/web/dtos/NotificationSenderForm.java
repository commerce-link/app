package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** How emails to customers are signed: the sender's display name and the address customer replies go to. */
@Getter
@Setter
public class NotificationSenderForm {

    public static final int SENDER_NAME_MAX_LENGTH = 100;

    // The name is written into the From header between quotes; quotes, angle brackets and line breaks would end it.
    private static final Pattern HEADER_BREAKING = Pattern.compile("[\"<>\\r\\n]");

    private String senderName;
    private String replyToEmail;

    public static NotificationSenderForm from(Store store) {
        NotificationSenderForm form = new NotificationSenderForm();
        ClientNotificationsConfiguration configuration = store.getClientNotificationsConfiguration();
        if (configuration != null) {
            form.senderName = configuration.getSenderName();
            form.replyToEmail = configuration.getReplyToEmail();
        }
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = StringUtils.trimToEmpty(senderName);
        if (HEADER_BREAKING.matcher(name).find()) {
            errors.put("senderName", "store.notification.senderName.invalid");
        } else if (name.length() > SENDER_NAME_MAX_LENGTH) {
            errors.put("senderName", "store.notification.senderName.too.long");
        }
        if (StringUtils.isNotBlank(replyToEmail) && !FormRules.isEmail(replyToEmail)) {
            errors.put("replyToEmail", "store.notification.replyToEmail.invalid");
        }
        return errors;
    }

    /** Only the sender fields change; the enabled message types are managed with the email templates. */
    public void applyTo(Store store) {
        ClientNotificationsConfiguration configuration = store.getClientNotificationsConfiguration();
        if (configuration == null) {
            configuration = new ClientNotificationsConfiguration();
            store.setClientNotificationsConfiguration(configuration);
        }
        configuration.setSenderName(StringUtils.trimToNull(senderName));
        configuration.setReplyToEmail(StringUtils.trimToNull(replyToEmail));
    }
}
