package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum OrderReviewStatus implements LocalizedEnum<OrderReviewStatus> {
    ToBeCollected("Do zebrania"),
    InProgress("W trakcie"),
    Positive("Pozytywna"),
    Negative("Negatywna"),
    NoResponse("Brak odpowiedzi"),
    NotApplicable("Nie dotyczy");

    private final String localizedName;

    OrderReviewStatus(String localizedName) {
        this.localizedName = localizedName;
    }

    public static OrderReviewStatus fromLocalizedName(String name) {
        return LocalizedEnum.fromLocalizedName(OrderReviewStatus.class, name);
    }

    public boolean isOneOf(LocalizedEnum<?>... other) {
        for (LocalizedEnum<?> localizedEnum : other) {
            if (this == localizedEnum) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
