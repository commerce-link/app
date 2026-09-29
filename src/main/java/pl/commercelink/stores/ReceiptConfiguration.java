package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/**
 * E-receipt settings of a store (no secrets; the provider and its access details live in the integrations and the
 * secret). Automatic receipts cover only orders delivered since {@link #getEnabledAt()}: stores keep B2C orders
 * delivered long ago open without a document, and switching the feature on must not fiscalise them now. When
 * enabled, every qualifying order gets an e-receipt whatever its source; there is no per-channel selection.
 */
@DynamoDBDocument
public class ReceiptConfiguration {

    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled;
    @DynamoDBAttribute(attributeName = "enabledAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime enabledAt;
    /**
     * When the store last disconnected its receipt system, null if it never did: its orders may still hold attempts
     * (and their bell alerts) from before, so the order lifecycle keeps reconciling them. Needed for a system used only
     * by hand ({@code enabledAt} stays null then); V019 set it, with the backfill's moment, for such stores that
     * disconnected before this field existed.
     */
    @DynamoDBAttribute(attributeName = "disconnectedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime disconnectedAt;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getEnabledAt() {
        return enabledAt;
    }

    public void setEnabledAt(LocalDateTime enabledAt) {
        this.enabledAt = enabledAt;
    }

    /** Turns automatic receipts on; the moment moves only when they were off. */
    public void enable(LocalDateTime now) {
        if (!enabled) {
            enabled = true;
            enabledAt = now;
        }
    }

    public void disable() {
        enabled = false;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public void setDisconnectedAt(LocalDateTime disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }
}
