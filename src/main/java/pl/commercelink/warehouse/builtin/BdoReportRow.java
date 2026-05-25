package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

public record BdoReportRow(
        String category,
        String name,
        String mfn,
        int qty,
        Integer weightNetG,
        Integer weightGrossG,
        Long totalWeightNetG,
        Long totalWeightGrossG,
        String supplier
) implements CSVReady {

    public static String[] headers() {
        return new String[]{
                "Category", "Name", "MFN", "Quantity",
                "Net weight (g)", "Gross weight (g)",
                "Total net weight (g)", "Total gross weight (g)",
                "Supplier"
        };
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                category != null ? category : "",
                name != null ? name : "",
                mfn != null ? mfn : "",
                String.valueOf(qty),
                weightNetG != null ? String.valueOf(weightNetG) : "",
                weightGrossG != null ? String.valueOf(weightGrossG) : "",
                totalWeightNetG != null ? String.valueOf(totalWeightNetG) : "",
                totalWeightGrossG != null ? String.valueOf(totalWeightGrossG) : "",
                supplier != null ? supplier : ""
        };
    }
}
