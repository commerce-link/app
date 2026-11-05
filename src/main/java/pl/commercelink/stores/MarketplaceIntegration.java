package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBDocument
public class MarketplaceIntegration {

    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "lastFetchedAt")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime lastFetchedAt;
    @DynamoDBAttribute(attributeName = "loggedIn")
    private boolean loggedIn;
    @DynamoDBAttribute(attributeName = "ordersImportSchedule")
    private String ordersImportSchedule;
    @DynamoDBAttribute(attributeName = "returnsImportSchedule")
    private String returnsImportSchedule;
    /**
     * Whether the nightly sweep orders offer matching for this connection. Null on connections saved before the field
     * existed, read as off; switched on by the operator's first manual run.
     */
    @DynamoDBAttribute(attributeName = "offerMatchingScheduled")
    private Boolean offerMatchingScheduled;

    public MarketplaceIntegration() {
    }

    public MarketplaceIntegration(String name) {
        this.name = name;
        this.loggedIn = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getLastFetchedAt() {
        return lastFetchedAt;
    }

    public void setLastFetchedAt(LocalDateTime lastFetchedAt) {
        this.lastFetchedAt = lastFetchedAt;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public String getOrdersImportSchedule() {
        return ordersImportSchedule;
    }

    public void setOrdersImportSchedule(String ordersImportSchedule) {
        this.ordersImportSchedule = ordersImportSchedule;
    }

    public String getReturnsImportSchedule() {
        return returnsImportSchedule;
    }

    public void setReturnsImportSchedule(String returnsImportSchedule) {
        this.returnsImportSchedule = returnsImportSchedule;
    }

    public Boolean getOfferMatchingScheduled() {
        return offerMatchingScheduled;
    }

    public void setOfferMatchingScheduled(Boolean offerMatchingScheduled) {
        this.offerMatchingScheduled = offerMatchingScheduled;
    }

    // No get/is prefix: the DynamoDB mapper would take it for a second attribute.
    public boolean runsOfferMatchingDaily() {
        return Boolean.TRUE.equals(offerMatchingScheduled);
    }
}
