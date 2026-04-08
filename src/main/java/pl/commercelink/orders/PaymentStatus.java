package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum PaymentStatus implements LocalizedEnum<PaymentStatus> {
    New("Nowy"),
    Unpaid("Nieopłacony"),
    Paid("Opłacony");

    private final String localizedName;

    PaymentStatus(String localizedName) {
        this.localizedName = localizedName;
    }

    public static PaymentStatus fromLocalizedName(String name) {
        return LocalizedEnum.fromLocalizedName(PaymentStatus.class, name);
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
