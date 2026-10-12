package pl.commercelink.web.reports;

import java.util.List;

/** Everything the reports page shows, worked out on the server; the template only prints it. */
public record ReportsPage(String periodLabel, String rangeText, String previousHref, String previousLabel,
                          String nextHref, String nextLabel, List<PresetLink> presets, String dateFrom, String dateTo,
                          List<FileGroup> files, ChannelComparison channels, List<DailyChart> charts,
                          SupplierCosts suppliers, StockLedgerClosingShortcut closing) {

    public record PresetLink(String labelKey, String href, boolean current) {
    }

    public record FileGroup(String titleKey, List<FileLink> files) {
    }

    public record FileLink(String titleKey, String descriptionKey, String href) {
    }
}
