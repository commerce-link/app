package pl.commercelink.inventory.supplier;

import pl.commercelink.stores.ConnectionMode;

import java.time.LocalDateTime;

/**
 * One row of the supplier tables. Keeps the connection identity separate from the label shown to
 * the operator: today they are equal for own and global connections, but manual connections
 * already differ and the upcoming multi-instance support will make that the rule.
 */
public record SupplierConnectionView(
        String identity,
        String providerName,
        String label,
        ConnectionMode mode,
        boolean includeInPricing,
        boolean includeInFulfilment,
        boolean enabled,
        LocalDateTime feedLastModified,
        boolean knownProvider) {

    public boolean hasFeed() {
        return feedLastModified != null;
    }

    public boolean isManual() {
        return mode == ConnectionMode.MANUAL;
    }

    public boolean isGlobal() {
        return mode == ConnectionMode.GLOBAL;
    }
}
