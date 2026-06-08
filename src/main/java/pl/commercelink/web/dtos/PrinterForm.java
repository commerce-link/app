package pl.commercelink.web.dtos;

import java.util.HashMap;
import java.util.Map;

public class PrinterForm {

    private String providerName;
    private String name;
    private Map<String, String> settings = new HashMap<>();

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }
}
