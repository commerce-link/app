package pl.commercelink.orders;

import pl.commercelink.starter.localization.LocalizedEnum;

public enum OrderStatus implements LocalizedEnum<OrderStatus> {
    New("Nowe"),
    Blocked("Zablokowane"),
    Assembly("W kompletacji"),
    Assembled("Skompletowane"),
    Realization("W realizacji"),
    Shipping("W dostawie"),
    Delivered("Dostarczone"),
    Completed("Zakończone"); // meaning paid, delivered and reviewed,

    private final String localizedName;

    OrderStatus(String localizedName) {
        this.localizedName = localizedName;
    }

    public static OrderStatus fromLocalizedName(String name) {
        return LocalizedEnum.fromLocalizedName(OrderStatus.class, name);
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
