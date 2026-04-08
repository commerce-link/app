package pl.commercelink.web.dtos;

import java.util.HashMap;
import java.util.Map;

public class IntegrationCredentialsForm {

    private String storeId;
    private String providerType;
    private String providerName;
    private Map<String, String> providerConfiguration = new HashMap<>();

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Map<String, String> getProviderConfiguration() {
        return providerConfiguration;
    }

    public void setProviderConfiguration(Map<String, String> providerConfiguration) {
        this.providerConfiguration = providerConfiguration;
    }
}
