package pl.commercelink.shipping;

public record DeliveryTarget(String carrier, String pointCode) {

    public static final DeliveryTarget NONE = new DeliveryTarget(null, null);

    public static DeliveryTarget carrier(String carrier) {
        return new DeliveryTarget(carrier, null);
    }
}
