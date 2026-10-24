package pl.commercelink.checkout;

import org.springframework.http.HttpStatus;

public enum CheckoutError {

    BillingDetailsMissing("billing-details-missing", HttpStatus.BAD_REQUEST),
    ShippingDetailsMissing("shipping-details-missing", HttpStatus.BAD_REQUEST),
    PricelistOutdated("pricelist-outdated", HttpStatus.BAD_REQUEST),
    ItemNotFound("item-not-found", HttpStatus.BAD_REQUEST),
    RequiredItemMissing("required-item-missing", HttpStatus.BAD_REQUEST),
    DeliveryOptionNotFound("delivery-option-not-found", HttpStatus.BAD_REQUEST),
    PaymentOptionNotFound("payment-option-not-found", HttpStatus.BAD_REQUEST),
    PaymentUnavailable("payment-unavailable", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String apiCode;
    private final HttpStatus status;

    CheckoutError(String apiCode, HttpStatus status) {
        this.apiCode = apiCode;
        this.status = status;
    }

    public String apiCode() {
        return apiCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String messageKey() {
        return "checkout.error." + name();
    }
}
