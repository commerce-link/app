package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * E-receipt settings of a store (no secrets; the provider and its access details live in the integrations and the
 * secret). Automatic receipts cover only orders delivered since {@link #getEnabledAt()}: stores keep B2C orders
 * delivered long ago open without a document, and switching the feature on must not fiscalise them now.
 */
@DynamoDBDocument
public class ReceiptConfiguration {

    public static final Set<OrderSourceType> DEFAULT_SOURCES =
            EnumSet.complementOf(EnumSet.of(OrderSourceType.PointOfSale));

    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled;
    @DynamoDBAttribute(attributeName = "enabledAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime enabledAt;
    @DynamoDBAttribute(attributeName = "sources")
    private List<String> sources;

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

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
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

    @DynamoDBIgnore
    public Set<OrderSourceType> sourceTypes() {
        if (sources == null) {
            return DEFAULT_SOURCES;
        }
        Set<OrderSourceType> types = EnumSet.noneOf(OrderSourceType.class);
        for (String source : sources) {
            try {
                types.add(OrderSourceType.valueOf(source));
            } catch (IllegalArgumentException ignored) {
                // a source removed from the enum is simply no longer covered
            }
        }
        return types;
    }

    public void setSourceTypes(Set<OrderSourceType> types) {
        this.sources = types.stream().map(Enum::name).sorted().collect(Collectors.toList());
    }

    public boolean covers(OrderSourceType type) {
        return type != null && sourceTypes().contains(type);
    }
}
