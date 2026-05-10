package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum PaymentDirection implements LocalizedEnum<PaymentDirection> {
    Incoming("Przychodząca"),
    Outgoing("Wychodząca");

    private final String localizedName;

    PaymentDirection(String localizedName) {
        this.localizedName = localizedName;
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
