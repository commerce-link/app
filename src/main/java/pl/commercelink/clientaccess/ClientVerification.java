package pl.commercelink.clientaccess;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBTable(tableName = "ClientVerifications")
@Getter
@Setter
@NoArgsConstructor
public class ClientVerification {

    @DynamoDBHashKey(attributeName = "subjectKey")
    private String subjectKey;
    @DynamoDBRangeKey(attributeName = "verificationId")
    private String verificationId;
    @DynamoDBAttribute(attributeName = "purpose")
    @DynamoDBTypeConvertedEnum
    private ClientVerificationPurpose purpose;
    @DynamoDBAttribute(attributeName = "codeHash")
    private String codeHash;
    @DynamoDBAttribute(attributeName = "attempts")
    private int attempts;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
    @DynamoDBAttribute(attributeName = "expiresAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime expiresAt;
    @DynamoDBAttribute(attributeName = "editTokenHash")
    private String editTokenHash;
    @DynamoDBAttribute(attributeName = "editTokenExpiresAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime editTokenExpiresAt;
    @DynamoDBAttribute(attributeName = "consumedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime consumedAt;
    @DynamoDBAttribute(attributeName = "ttl")
    private Long ttl;
    @DynamoDBVersionAttribute
    private Long version;

    public ClientVerification(String subjectKey, String verificationId, ClientVerificationPurpose purpose, String codeHash,
                              LocalDateTime createdAt, LocalDateTime expiresAt, Long ttl) {
        this.subjectKey = subjectKey;
        this.verificationId = verificationId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ttl = ttl;
    }

    public boolean isCodeExpired(LocalDateTime now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    @DynamoDBIgnore
    public boolean isCodeConfirmed() {
        return editTokenHash != null;
    }

    @DynamoDBIgnore
    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean hasAttemptsLeft(int maxAttempts) {
        return attempts < maxAttempts;
    }

    public void registerAttempt() {
        attempts++;
    }

    public void confirmCode(String editTokenHash, LocalDateTime editTokenExpiresAt) {
        this.editTokenHash = editTokenHash;
        this.editTokenExpiresAt = editTokenExpiresAt;
    }

    public boolean isEditTokenValid(LocalDateTime now) {
        return isCodeConfirmed() && !isConsumed() && editTokenExpiresAt != null && now.isBefore(editTokenExpiresAt);
    }

    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }

    public boolean wasCreatedAfter(LocalDateTime threshold) {
        return createdAt != null && createdAt.isAfter(threshold);
    }
}
