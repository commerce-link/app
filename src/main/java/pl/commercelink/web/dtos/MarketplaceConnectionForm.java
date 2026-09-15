package pl.commercelink.web.dtos;

import java.util.HashMap;
import java.util.Map;

public class MarketplaceConnectionForm {

    private String marketplace;
    private Map<String, String> configuration = new HashMap<>();
    private String schedule;
    private String returnsSchedule;

    public String getMarketplace() {
        return marketplace;
    }

    public void setMarketplace(String marketplace) {
        this.marketplace = marketplace;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, String> configuration) {
        this.configuration = configuration;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getReturnsSchedule() {
        return returnsSchedule;
    }

    public void setReturnsSchedule(String returnsSchedule) {
        this.returnsSchedule = returnsSchedule;
    }
}
