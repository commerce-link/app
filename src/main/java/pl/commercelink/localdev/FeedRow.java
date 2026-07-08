package pl.commercelink.localdev;

import pl.commercelink.starter.csv.CSVReady;

public record FeedRow(
        String ean,
        String mfn,
        String brand,
        String name,
        String category,
        String price,
        String currency,
        String qty) implements CSVReady {

    public static final String[] HEADERS = {"ean", "mfn", "brand", "name", "category", "price", "currency", "qty"};

    @Override
    public String[] asStringArray() {
        return new String[]{ean, mfn, brand, name, category, price, currency, qty};
    }
}
