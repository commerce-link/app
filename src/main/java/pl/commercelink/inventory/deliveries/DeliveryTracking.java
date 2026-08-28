package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/**
 * Supplier tracking progress of a single dropship delivery: the polling state and the counters that decide when
 * the next check happens and when the app gives up. It owns its transitions, so the tracking service only decides
 * which transition applies.
 */
@DynamoDBDocument
public class DeliveryTracking {

    static final int MAX_ERROR_LENGTH = 500;

    @DynamoDBAttribute(attributeName = "state")
    @DynamoDBTypeConvertedEnum
    private DeliveryTrackingState state;
    @DynamoDBAttribute(attributeName = "lastCheckedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime lastCheckedAt;
    @DynamoDBAttribute(attributeName = "nextCheckAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime nextCheckAt;
    @DynamoDBAttribute(attributeName = "attempts")
    private int attempts;
    @DynamoDBAttribute(attributeName = "consecutiveErrors")
    private int consecutiveErrors;
    @DynamoDBAttribute(attributeName = "lastError")
    private String lastError;

    // required by DynamoDb
    public DeliveryTracking() {

    }

    /**
     * A delivery that has never been scheduled is pending too - the state attribute is only written once the
     * first check happens.
     */
    @DynamoDBIgnore
    public boolean isPending() {
        return state == null || state == DeliveryTrackingState.PENDING;
    }

    @DynamoDBIgnore
    public DeliveryTrackingState effectiveState() {
        return state == null ? DeliveryTrackingState.PENDING : state;
    }

    @DynamoDBIgnore
    public boolean isDue(LocalDateTime now) {
        return nextCheckAt == null || !nextCheckAt.isAfter(now);
    }

    public void recordCheck(LocalDateTime now) {
        this.lastCheckedAt = now;
        this.attempts++;
        this.consecutiveErrors = 0;
        this.lastError = null;
    }

    public void recordError(String message, LocalDateTime now, boolean countsTowardsGiveUp) {
        this.lastCheckedAt = now;
        this.attempts++;
        if (countsTowardsGiveUp) {
            this.consecutiveErrors++;
        }
        this.lastError = StringUtils.abbreviate(message, MAX_ERROR_LENGTH);
        if (this.state == null) {
            this.state = DeliveryTrackingState.PENDING;
        }
    }

    public boolean isExhausted(int maxConsecutiveErrors) {
        return consecutiveErrors >= maxConsecutiveErrors;
    }

    public void scheduleNext(LocalDateTime at) {
        if (this.state == null) {
            this.state = DeliveryTrackingState.PENDING;
        }
        this.nextCheckAt = at;
    }

    public void finish(DeliveryTrackingState terminal) {
        this.state = terminal;
        this.nextCheckAt = null;
    }

    public void finish(DeliveryTrackingState terminal, String lastError) {
        this.lastError = StringUtils.abbreviate(lastError, MAX_ERROR_LENGTH);
        finish(terminal);
    }

    public DeliveryTrackingState getState() {
        return state;
    }

    public void setState(DeliveryTrackingState state) {
        this.state = state;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(LocalDateTime nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public void setConsecutiveErrors(int consecutiveErrors) {
        this.consecutiveErrors = consecutiveErrors;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
