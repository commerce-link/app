package pl.commercelink.warehouse.builtin;

import pl.commercelink.starter.csv.CSVReady;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record StockLedgerRow(
        String mfn,
        String name,
        int boQty,
        double boValue,
        Map<LedgerCategory, Integer> qtyByCategory,
        Map<LedgerCategory, Double> valueByCategory
) implements CSVReady {

    @Override
    public String[] asStringArray() {
        List<String> cells = new ArrayList<>();
        cells.add(mfn);
        cells.add(name != null ? name : "");
        cells.add("szt.");
        cells.add(String.valueOf(boQty));
        cells.add(formatMoney(boValue));

        appendSide(cells, LedgerCategory.receipts());
        appendSide(cells, LedgerCategory.issues());

        int bzQty = boQty + totalQty(LedgerCategory.receipts()) - totalQty(LedgerCategory.issues());
        double bzValue = boValue + totalValue(LedgerCategory.receipts()) - totalValue(LedgerCategory.issues());
        cells.add(String.valueOf(bzQty));
        cells.add(formatMoney(bzValue));
        cells.add("PLN");

        return cells.toArray(new String[0]);
    }

    private void appendSide(List<String> cells, List<LedgerCategory> categories) {
        for (LedgerCategory category : categories) {
            cells.add(String.valueOf(qty(category)));
            cells.add(formatMoney(value(category)));
        }
        cells.add(String.valueOf(totalQty(categories)));
        cells.add(formatMoney(totalValue(categories)));
    }

    private int qty(LedgerCategory category) {
        return qtyByCategory.getOrDefault(category, 0);
    }

    private double value(LedgerCategory category) {
        return valueByCategory.getOrDefault(category, 0.0);
    }

    private int totalQty(List<LedgerCategory> categories) {
        return categories.stream().mapToInt(this::qty).sum();
    }

    private double totalValue(List<LedgerCategory> categories) {
        return categories.stream().mapToDouble(this::value).sum();
    }

    public static String[] headers() {
        List<String> headers = new ArrayList<>();
        headers.add("SKU (MFN)");
        headers.add("Nazwa");
        headers.add("J.m.");
        headers.add("BO ilość");
        headers.add("BO wartość");

        appendHeaders(headers, LedgerCategory.receipts(), "Przychód RAZEM");
        appendHeaders(headers, LedgerCategory.issues(), "Rozchód RAZEM");

        headers.add("BZ ilość");
        headers.add("BZ wartość");
        headers.add("Waluta");

        return headers.toArray(new String[0]);
    }

    private static void appendHeaders(List<String> headers, List<LedgerCategory> categories, String totalLabel) {
        for (LedgerCategory category : categories) {
            headers.add(category.label() + " ilość");
            headers.add(category.label() + " wartość");
        }
        headers.add(totalLabel + " ilość");
        headers.add(totalLabel + " wartość");
    }

    private static String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value).replace('.', ',');
    }
}
