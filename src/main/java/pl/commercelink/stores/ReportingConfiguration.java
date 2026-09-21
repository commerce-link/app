package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

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

    // The token is part of the conversions address the store pastes into Google Ads, so it changes only on an explicit
    // request; enabling again or disabling keeps it, otherwise Google Ads silently stops receiving conversions.
    @DynamoDBIgnore
    public void enableGoogleAds() {
        googleAdsEnabled = true;
        if (StringUtils.isBlank(googleAdsToken)) {
            googleAdsToken = UUID.randomUUID().toString();
        }
    }

    @DynamoDBIgnore
    public void disableGoogleAds() {
        googleAdsEnabled = false;
    }

    @DynamoDBIgnore
    public void regenerateGoogleAdsToken() {
        googleAdsToken = UUID.randomUUID().toString();
    }
}
