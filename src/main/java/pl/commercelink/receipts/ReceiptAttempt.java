package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One issuing attempt of a fiscal e-receipt: its state, the frozen request, the provider's answers, the side effects
 * already done and its own schedule ({@code nextCheckAt}, in the sparse due index while there is work to do).
 */
@DynamoDBTable(tableName = ReceiptAttempt.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class ReceiptAttempt {

    public static final String TABLE_NAME = "ReceiptAttempts";
    public static final String DUE_INDEX = "ReceiptDue";
    public static final String DUE_BUCKET = "due";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "receiptKey")
    private String receiptKey;
    @DynamoDBAttribute(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "attemptNo")
    private int attemptNo;
    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    @DynamoDBAttribute(attributeName = "state")
    @DynamoDBTypeConvertedEnum
    private ReceiptAttemptState state;
    @DynamoDBAttribute(attributeName = "requestSnapshot")
    private String requestSnapshot;
    @DynamoDBAttribute(attributeName = "providerReceiptId")
    private String providerReceiptId;
    @DynamoDBAttribute(attributeName = "issueCalls")
    private int issueCalls;
    /** Failures preparing the call (provider load, request snapshot) before {@code issueCalls} is ever incremented. */
    @DynamoDBAttribute(attributeName = "preSendFailures")
    private int preSendFailures;
    @DynamoDBAttribute(attributeName = "pollCount")
    private int pollCount;
    @DynamoDBAttribute(attributeName = "lastError")
    private String lastError;
    @DynamoDBAttribute(attributeName = "lastErrorAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant lastErrorAt;
    @DynamoDBAttribute(attributeName = "invalidAfterSend")
    private boolean invalidAfterSend;
    @DynamoDBAttribute(attributeName = "failureCode")
    private String failureCode;
    @DynamoDBAttribute(attributeName = "failureMessage")
    private String failureMessage;
    @DynamoDBAttribute(attributeName = "blockedReason")
    private String blockedReason;
    @DynamoDBAttribute(attributeName = "blockedDetail")
    private String blockedDetail;
    @DynamoDBAttribute(attributeName = "receiptNumber")
    private String receiptNumber;
    @DynamoDBAttribute(attributeName = "cashRegisterUniqueNumber")
    private String cashRegisterUniqueNumber;
    @DynamoDBAttribute(attributeName = "fiscalisedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant fiscalisedAt;
    @DynamoDBAttribute(attributeName = "documentUrl")
    private String documentUrl;
    @DynamoDBAttribute(attributeName = "linkGaveUpAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant linkGaveUpAt;
    @DynamoDBAttribute(attributeName = "documentAttachedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant documentAttachedAt;
    @DynamoDBAttribute(attributeName = "documentLinkedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant documentLinkedAt;
    @DynamoDBAttribute(attributeName = "marketplaceNotifiedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant marketplaceNotifiedAt;
    @DynamoDBAttribute(attributeName = "emailClaimedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant emailClaimedAt;
    @DynamoDBAttribute(attributeName = "emailSentAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant emailSentAt;
    /** Set instead of {@code emailSentAt} when the claim resolved without ever calling the provider: the store does
     *  not send this e-mail type, or the buyer has no address. Not a failure — nothing for the operator to do. */
    @DynamoDBAttribute(attributeName = "emailSkippedAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant emailSkippedAt;
    @DynamoDBAttribute(attributeName = "attention")
    private String attention;
    @DynamoDBAttribute(attributeName = "leaseOwner")
    private String leaseOwner;
    @DynamoDBAttribute(attributeName = "leaseUntil")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant leaseUntil;
    @DynamoDBIndexHashKey(globalSecondaryIndexName = DUE_INDEX, attributeName = "dueBucket")
    private String dueBucket;
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = DUE_INDEX, attributeName = "nextCheckAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant nextCheckAt;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = ReceiptInstantConverter.class)
    private Instant createdAt;
    @DynamoDBAttribute(attributeName = "createdBy")
    private String createdBy;
    @DynamoDBVersionAttribute
    private Long version;

    /** Puts the attempt in the due index at the given moment. */
    public void schedule(Instant at) {
        this.dueBucket = DUE_BUCKET;
        this.nextCheckAt = at;
    }

    /** Takes the attempt out of the due index: nothing left to do. */
    public void unschedule() {
        this.dueBucket = null;
        this.nextCheckAt = null;
    }

    @DynamoDBIgnore
    public boolean isScheduled() {
        return nextCheckAt != null;
    }

    @DynamoDBIgnore
    public boolean isLeasedAt(Instant now) {
        return leaseUntil != null && leaseUntil.isAfter(now);
    }

    /** Whether this attempt still needs the provider: ISSUING and PENDING always do, and a FISCALISED attempt does
     *  too until its link is fetched ({@code documentUrl}) or the fetch is given up on ({@code linkGaveUpAt}) — until
     *  then it is still polled and the customer still waits for the e-mail. */
    @DynamoDBIgnore
    public boolean needsProvider() {
        return state == ReceiptAttemptState.ISSUING || state == ReceiptAttemptState.PENDING
                || (state == ReceiptAttemptState.FISCALISED && documentUrl == null && linkGaveUpAt == null);
    }

    /** The e-mail was claimed and really failed to send (not skipped, not delivered): the one case the operator can
     *  retry with "resend e-mail". */
    @DynamoDBIgnore
    public boolean emailFailed() {
        return state == ReceiptAttemptState.FISCALISED && emailClaimedAt != null && emailSentAt == null
                && emailSkippedAt == null;
    }
}
