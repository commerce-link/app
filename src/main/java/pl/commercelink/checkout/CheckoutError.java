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
    PaymentUnavailable("payment-unavailable", HttpStatus.INTERNAL_SERVER_ERROR),
    DiscountCodeNotFound("discount-code-not-found", HttpStatus.BAD_REQUEST),
    DiscountCodeInactive("discount-code-inactive", HttpStatus.BAD_REQUEST),
    DiscountCodeExhausted("discount-code-exhausted", HttpStatus.BAD_REQUEST),
    DiscountCodeExpired("discount-code-expired", HttpStatus.BAD_REQUEST),
    DiscountCodeNotYetValid("discount-code-not-yet-valid", HttpStatus.BAD_REQUEST),
    DiscountCodePaymentMethodMismatch("discount-code-payment-method-mismatch", HttpStatus.BAD_REQUEST),
    DiscountCodeMinimumNotReached("discount-code-minimum-not-reached", HttpStatus.BAD_REQUEST),
    DiscountCodeNotApplicable("discount-code-not-applicable", HttpStatus.BAD_REQUEST);

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
