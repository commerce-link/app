package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

public record ProductWeightOriginComplianceReportRow(
        String country,
        String category,
        String brand,
        String name,
        String mfn,
        int qty,
        Integer weightNetG,
        Integer weightGrossG,
        Long totalWeightNetG,
        Long totalWeightGrossG
) implements CSVReady {

    public static String[] headers() {
        return new String[]{
                "Country", "Category", "Brand", "Name", "MFN", "Quantity",
                "Net weight (g)", "Gross weight (g)",
                "Total net weight (g)", "Total gross weight (g)"
        };
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                country != null ? country : "",
                category != null ? category : "",
                brand != null ? brand : "",
                name != null ? name : "",
                mfn != null ? mfn : "",
                String.valueOf(qty),
                weightNetG != null ? String.valueOf(weightNetG) : "",
                weightGrossG != null ? String.valueOf(weightGrossG) : "",
                totalWeightNetG != null ? String.valueOf(totalWeightNetG) : "",
                totalWeightGrossG != null ? String.valueOf(totalWeightGrossG) : ""
        };
    }
}
