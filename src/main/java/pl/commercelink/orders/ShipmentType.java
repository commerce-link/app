package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum ShipmentType implements LocalizedEnum<ShipmentType> {
    PersonalCollection("Odbiór osobisty"),
    Courier("Kurier");

    private final String localizedName;

    ShipmentType(String localizedName) {
        this.localizedName = localizedName;
    }

    public static ShipmentType fromLocalizedName(String name) {
        return LocalizedEnum.fromLocalizedName(ShipmentType.class, name);
    }

    @Override
    public String getLocalizedName() {
        return localizedName;
    }
}
