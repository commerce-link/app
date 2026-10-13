package pl.commercelink.financials;

/** Deliveries ordered from one supplier in the report's range; a null provider gathers the deliveries without one. */
public record SupplierDeliveries(String provider, int deliveries, double shippingCost, double paymentCost) {
}
