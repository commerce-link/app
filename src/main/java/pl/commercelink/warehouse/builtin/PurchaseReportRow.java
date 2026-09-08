package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

public record PurchaseReportRow(
        String supplier,
        String category,
        String mfn,
        String name,
        int qty
) implements CSVReady {

    public static String[] headers() {
        return new String[]{"Supplier", "Category", "MFN", "Name", "Quantity"};
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                supplier != null ? supplier : "",
                category != null ? category : "",
                mfn != null ? mfn : "",
                name != null ? name : "",
                String.valueOf(qty)
        };
    }
}
