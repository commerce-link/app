package pl.commercelink.web.reports;

import java.util.List;

/** Everything the reports page shows, worked out on the server; the template only prints it. */
public record ReportsPage(String periodLabel, String rangeText, boolean shortened, String previousHref, String previousLabel,
                          String nextHref, String nextLabel, List<PresetLink> presets, String dateFrom, String dateTo,
                          String dateMin, String dateMax, String closingHref,
                          List<FileGroup> files, ChannelComparison channels, List<DailyChart> charts,
                          SupplierCosts suppliers, StockLedgerClosingShortcut closing) {

    /** The longest range the page shows, named in the note under the period when a longer one was asked for. */
    public int maxYears() {
        return ReportPeriod.MAX_YEARS;
    }

    public record PresetLink(String labelKey, String href, boolean current) {
    }

    public record FileGroup(String titleKey, List<FileLink> files) {
    }

    public record FileLink(String titleKey, String descriptionKey, String href, boolean download) {

        public FileLink(String titleKey, String descriptionKey, String href) {
            this(titleKey, descriptionKey, href, true);
        }
    }
}
