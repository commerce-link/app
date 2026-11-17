package pl.commercelink.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "inventory.matching")
public record InventoryMatchingProperties(
        @DefaultValue("true") boolean excludeConflictingOffers,
        @DefaultValue("true") boolean ignorePlaceholderEans,
        @DefaultValue("true") boolean splitSingleCodeByPrice,
        @DefaultValue("true") boolean linkOwnToGlobalByCode) {

    public static InventoryMatchingProperties allEnabled() {
        return new InventoryMatchingProperties(true, true, true, true);
    }
}
