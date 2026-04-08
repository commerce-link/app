package pl.commercelink.orders.rma;

import pl.commercelink.starter.localization.LocalizedEnum;
import pl.commercelink.starter.localization.EnumMessageResolver;

import java.util.Locale;

public enum RMAResolutionType implements LocalizedEnum<RMAResolutionType> {
    Repair,
    Return,
    Replacement;

    @Override
    public String getLocalizedName() {
        return EnumMessageResolver.get("rma.resolutionType." + this.name());
    }

    public String getLocalizedName(Locale locale) {
        return EnumMessageResolver.get("rma.resolutionType." + this.name(), locale);
    }
}