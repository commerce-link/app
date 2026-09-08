package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

public record PurchaseReportRow(
        String category,
        String supplier,
        String brand,
        String name,
        String mfn,
        int qty
) implements CSVReady {

    public static String[] headers() {
        return new String[]{"Category", "Supplier", "Brand", "Name", "MFN", "Quantity"};
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                category != null ? category : "",
                supplier != null ? supplier : "",
                brand != null ? brand : "",
                name != null ? name : "",
                mfn != null ? mfn : "",
                String.valueOf(qty)
        };
    }
}
