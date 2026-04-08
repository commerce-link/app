package pl.commercelink.checkout;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckoutResponse {

    @JsonProperty("url")
    private String url;

    private CheckoutResponse() {

    }

    public CheckoutResponse(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
