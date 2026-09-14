package pl.commercelink.clientaccess;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import pl.commercelink.starter.email.EmailNotification;

@Getter
public class ClientVerificationEmailNotification extends EmailNotification {

    @JsonProperty("code")
    private final String code;
    @JsonProperty("expiresInMinutes")
    private final int expiresInMinutes;
    @JsonProperty("orderId")
    private final String orderId;
    @JsonProperty("rmaId")
    private final String rmaId;

    ClientVerificationEmailNotification(String recipientEmail, String recipientName, String code, int expiresInMinutes,
                                        ClientVerificationSubject subject) {
        super(recipientEmail, recipientName);
        this.code = code;
        this.expiresInMinutes = expiresInMinutes;
        this.orderId = subject.getOrderId();
        this.rmaId = subject.getRmaId();
    }
}
