package pl.commercelink.web.reports;

/** The six CSV files of the reports page, all for the page's range; their addresses are unchanged from the old page. */
public enum ReportFile {
    ORDERS("reports.files.group.sales", "orders", "/dashboard/reports/ordersExport"),
    PAYMENTS("reports.files.group.sales", "payments", "/dashboard/reports/paymentsExport"),
    GOOGLE_CONVERSIONS("reports.files.group.sales", "googleConversions", "/dashboard/reports/googleOfflineConversionsExport"),
    PURCHASES("reports.files.group.purchases", "purchases", "/dashboard/reports/purchaseExport"),
    STOCK_LEDGER("reports.files.group.purchases", "stockLedger", "/dashboard/reports/stockLedgerExport"),
    WEIGHT_ORIGIN("reports.files.group.purchases", "weightOrigin", "/dashboard/reports/productWeightOriginComplianceExport");

    private final String group;
    private final String key;
    private final String path;

    ReportFile(String group, String key, String path) {
        this.group = group;
        this.key = key;
        this.path = path;
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
        return path + "?" + period.query();
    }
}
