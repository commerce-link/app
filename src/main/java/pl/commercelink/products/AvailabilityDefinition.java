package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

@DynamoDBDocument
public class AvailabilityDefinition {

    @DynamoDBAttribute(attributeName = "totalMinQty")
    private int totalMinQty;
    @DynamoDBAttribute(attributeName = "minNumberOfProviders")
    private int minNumberOfProviders;

    // required by DynamoDB
    public AvailabilityDefinition() {
    }

    public AvailabilityDefinition(int totalMinQty, int minNumberOfProviders) {
        this.totalMinQty = totalMinQty;
        this.minNumberOfProviders = minNumberOfProviders;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return totalMinQty > 0 && minNumberOfProviders > 0;
    }

    public int getTotalMinQty() {
        return totalMinQty;
    }

    public void setTotalMinQty(int totalMinQty) {
        this.totalMinQty = totalMinQty;
    }

    public int getMinNumberOfProviders() {
        return minNumberOfProviders;
    }

    public void setMinNumberOfProviders(int minNumberOfProviders) {
        this.minNumberOfProviders = minNumberOfProviders;
    }
}
