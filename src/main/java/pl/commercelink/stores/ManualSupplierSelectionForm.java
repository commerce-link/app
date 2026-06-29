package pl.commercelink.stores;

import org.springframework.web.multipart.MultipartFile;

public class ManualSupplierSelectionForm {

    private String identity;
    private String label;
    private boolean enabled;
    private boolean includeInPricing;
    private boolean includeInFulfilment;
    private boolean hasFeed;
    private MultipartFile feed;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeInPricing() {
        return includeInPricing;
    }

    public void setIncludeInPricing(boolean includeInPricing) {
        this.includeInPricing = includeInPricing;
    }

    public boolean isIncludeInFulfilment() {
        return includeInFulfilment;
    }

    public void setIncludeInFulfilment(boolean includeInFulfilment) {
        this.includeInFulfilment = includeInFulfilment;
    }

    public boolean isHasFeed() {
        return hasFeed;
    }

    public void setHasFeed(boolean hasFeed) {
        this.hasFeed = hasFeed;
    }

    public MultipartFile getFeed() {
        return feed;
    }

    public void setFeed(MultipartFile feed) {
        this.feed = feed;
    }
}
