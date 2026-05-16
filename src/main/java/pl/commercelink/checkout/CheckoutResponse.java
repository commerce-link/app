package pl.commercelink.checkout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.payments.api.PaymentLink;

import java.util.Map;

public class CheckoutResponse {

    @JsonIgnore
    private PaymentLink paymentLink;

    private CheckoutResponse() {

    }

    public CheckoutResponse(PaymentLink paymentLink) {
        this.paymentLink = paymentLink;
    }

    @JsonProperty("url")
    public String getUrl() {
        return paymentLink.url();
    }

    @JsonProperty("method")
    public String getMethod() {
        return paymentLink.method();
    }

    @JsonProperty("params")
    public Map<String, String> getParams() {
        return paymentLink.params();
    }
}
