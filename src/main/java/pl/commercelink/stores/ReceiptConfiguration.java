package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/**
 * E-receipt settings of a store (no secrets; the provider and its access details live in the integrations and the
 * secret). Automatic receipts cover only orders delivered since {@link #getEnabledAt()}: stores keep B2C orders
 * delivered long ago open without a document, and switching the feature on must not fiscalise them now. When
 * enabled, every qualifying order gets an e-receipt whatever its source; there is no per-channel selection, but
 * point-of-sale orders follow {@link #getPosReceiptMode()}.
 */
@DynamoDBDocument
public class ReceiptConfiguration {

    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled;
    @DynamoDBAttribute(attributeName = "enabledAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime enabledAt;
    @DynamoDBAttribute(attributeName = "posReceiptMode")
    @DynamoDBTypeConvertedEnum
    private PosReceiptMode posReceiptMode;

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

    /** Never null: stores that never chose a mode ask on every sale, the safe default. */
    public PosReceiptMode getPosReceiptMode() {
        return posReceiptMode == null ? PosReceiptMode.ASK : posReceiptMode;
    }

    public void setPosReceiptMode(PosReceiptMode posReceiptMode) {
        this.posReceiptMode = posReceiptMode;
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
}
