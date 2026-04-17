package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

import java.util.Locale;

public record StockLedgerRow(
        String mfn,
        String name,
        int boQty,
        double boValue,
        int inQty,
        double inValue,
        int outQty,
        double outValue,
        int bzQty,
        double bzValue
) implements CSVReady {

    @Override
    public String[] asStringArray() {
        return new String[]{
                mfn,
                name != null ? name : "",
                "szt.",
                String.valueOf(boQty),
                formatMoney(boValue),
                String.valueOf(inQty),
                formatMoney(inValue),
                String.valueOf(outQty),
                formatMoney(outValue),
                String.valueOf(bzQty),
                formatMoney(bzValue),
                "PLN"
        };
    }

    public static String[] headers() {
        return new String[]{
                "SKU (MFN)",
                "Nazwa",
                "J.m.",
                "BO ilość",
                "BO wartość",
                "Przychód ilość",
                "Przychód wartość",
                "Rozchód ilość",
                "Rozchód wartość",
                "BZ ilość",
                "BZ wartość",
                "Waluta"
        };
    }

    private static String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value).replace('.', ',');
    }
}
