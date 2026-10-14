package pl.commercelink.reports;

import java.util.Optional;

public enum ReportType {
    /** A file kept under {@code generated-reports/<store>/stock-ledger/}. */
    STOCK_LEDGER_EXPORT("stock-ledger"),
    /** Stores its own result (the closed period), so it has no file here. */
    STOCK_LEDGER_CLOSING(null);

    private final String folder;

    ReportType(String folder) {
        this.folder = folder;
    }

    public String titleKey() {
        return "reports.generated.type." + name();
    }

    Optional<String> folder() {
        return Optional.ofNullable(folder);
    }
}
