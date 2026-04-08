package pl.commercelink.orders.rma;

import pl.commercelink.starter.localization.LocalizedEnum;
import pl.commercelink.starter.localization.EnumMessageResolver;

import java.util.Locale;

public enum RMAStatus implements LocalizedEnum<RMAStatus> {
    New,
    Approved,
    Rejected,
    WaitingForItems,
    ItemsReceived,
    Processing,
    Completed;

    @Override
    public String getLocalizedName() {
        return EnumMessageResolver.get("rma.status." + this.name());
    }

    public String getLocalizedName(Locale locale) {
        return EnumMessageResolver.get("rma.status." + this.name(), locale);
    }
}
