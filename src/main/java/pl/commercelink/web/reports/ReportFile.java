package pl.commercelink.web.reports;

/**
 * The six CSV files of the reports page, all for the page's range. The stock ledger is not downloaded at once: it opens
 * its page, where the report is ordered and later downloaded, because it can take longer than a request may.
 */
public enum ReportFile {
    ORDERS("reports.files.group.sales", "orders", "/dashboard/reports/ordersExport"),
    PAYMENTS("reports.files.group.sales", "payments", "/dashboard/reports/paymentsExport"),
    GOOGLE_CONVERSIONS("reports.files.group.sales", "googleConversions", "/dashboard/reports/googleOfflineConversionsExport"),
    PURCHASES("reports.files.group.purchases", "purchases", "/dashboard/reports/purchaseExport"),
    STOCK_LEDGER("reports.files.group.purchases", "stockLedger", "/dashboard/reports/stock-ledger", false),
    WEIGHT_ORIGIN("reports.files.group.purchases", "weightOrigin", "/dashboard/reports/productWeightOriginComplianceExport");

    private final String group;
    private final String key;
    private final String path;
    private final boolean immediate;

    ReportFile(String group, String key, String path) {
        this(group, key, path, true);
    }

    ReportFile(String group, String key, String path, boolean immediate) {
        this.group = group;
        this.key = key;
        this.path = path;
        this.immediate = immediate;
    }

    public boolean immediate() {
        return immediate;
    }

    public String group() {
        return group;
    }

    public String titleKey() {
        return "reports.files." + key;
    }

    public String descriptionKey() {
        return "reports.files." + key + ".desc";
    }

    public String href(ReportPeriod period) {
        return immediate ? path + "?" + period.query() : path + "?from=" + period.from() + "&to=" + period.to();
    }
}
