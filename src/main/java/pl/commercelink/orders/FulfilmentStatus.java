package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum FulfilmentStatus implements LocalizedEnum<FulfilmentStatus> {
    New("Nowy"),
    Allocation("W alokacji"),
    Ordered("Zamówiony"),
    Reserved("Zarezerwowany"),
    Delivered("Dostarczony"),
    InRMA("W reklamacji"),
    InExternalService("W serwisie zewnętrznym"),
    Returned("Zwrócony"),
    Replaced("Wymieniony"),
    Destroyed("Zniszczony");

    private final String localizedName;

    FulfilmentStatus(String localizedName) {
        this.localizedName = localizedName;
    }

    public static FulfilmentStatus fromLocalizedName(String name) {
        return LocalizedEnum.fromLocalizedName(FulfilmentStatus.class, name);
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
