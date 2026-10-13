package pl.commercelink.financials;

import java.util.List;

public record FinancialReports(FinancialReport ownSources, FinancialReport marketplace,
                               List<DailySales> days, List<SupplierDeliveries> suppliers) {
}
