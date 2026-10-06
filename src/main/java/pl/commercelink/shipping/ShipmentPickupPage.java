package pl.commercelink.shipping;

import java.util.List;

/**
 * The "Zamów odbiór" page, every text ready to show: the groups of waiting packages (selectedKey is null when nothing
 * waits), the packages and pickup address of the chosen group, its pickup windows, where to go back, the provider's
 * reason when the windows could not be read (windowsError) and the reason a sent form was not accepted (formError).
 */
public record ShipmentPickupPage(List<GroupOption> groups, String selectedKey, List<PackageRow> packages,
                                 String address, List<WindowOption> windows, String windowsError, String formError,
                                 String back) {

    /** One option of the carrier select: "{carrier} · {integration} · {address} · paczek: n". */
    public record GroupOption(String key, String label, boolean selected) {
    }

    /** A package of the chosen group: "{short owner id} · {tracking number}", marker for the owner the page came from. */
    public record PackageRow(String externalId, String label, String marker) {
    }

    /** value: date|from|to|token, as the form posts it back. */
    public record WindowOption(String value, String label) {
    }

    public boolean hasGroups() {
        return selectedKey != null;
    }

    public boolean canOrder() {
        return !windows.isEmpty();
    }
}
