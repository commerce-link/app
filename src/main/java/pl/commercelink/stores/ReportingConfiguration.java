package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class ReportingConfiguration {

    @DynamoDBAttribute(attributeName = "googleAdsEnabled")
    private boolean googleAdsEnabled;

    @DynamoDBAttribute(attributeName = "googleAdsToken")
    private String googleAdsToken;

    public ReportingConfiguration() {
    }

    public boolean isGoogleAdsEnabled() {
        return googleAdsEnabled;
    }

    public void setGoogleAdsEnabled(boolean googleAdsEnabled) {
        this.googleAdsEnabled = googleAdsEnabled;
    }

    public String getGoogleAdsToken() {
        return googleAdsToken;
    }

    public void setGoogleAdsToken(String googleAdsToken) {
        this.googleAdsToken = googleAdsToken;
    }
}
