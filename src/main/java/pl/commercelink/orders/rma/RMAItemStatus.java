package pl.commercelink.orders.rma;

import pl.commercelink.starter.localization.LocalizedEnum;
import pl.commercelink.starter.localization.EnumMessageResolver;

import java.util.Locale;

public enum RMAItemStatus implements LocalizedEnum<RMAItemStatus> {
    New,
    Received,
    SentForRepair,
    ReturnedToClient,
    MovedToWarehouse;

    @Override
    public String getLocalizedName() {
        return EnumMessageResolver.get("rma.itemStatus." + this.name());
    }

    public String getLocalizedName(Locale locale) {
        return EnumMessageResolver.get("rma.itemStatus." + this.name(), locale);
    }
}
