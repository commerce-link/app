package pl.commercelink.orders.filters;

public enum Marketplace {

    Allegro("Allegro"),
    Empik("Empik"),
    Ceneo("Ceneo"),
    Morele("Morele"),
    CsCartMultiVendor("CsCartMultiVendor");

    private final String sourceName;

    Marketplace(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }
}
