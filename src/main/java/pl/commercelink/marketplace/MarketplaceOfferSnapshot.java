package pl.commercelink.marketplace;

import pl.commercelink.starter.csv.CSVReady;

public class MarketplaceOfferSnapshot implements CSVReady {

    private String pimId;
    private long price;
    private long qty;

    public MarketplaceOfferSnapshot(String pimId, long price, long qty) {
        this.pimId = pimId;
        this.price = price;
        this.qty = qty;
    }

    public boolean hasPimId(String otherPimId) {
        return this.pimId.equals(otherPimId);
    }

    public boolean hasChangedComparedTo(MarketplaceOfferSnapshot other) {
        return this.price != other.price || this.qty != other.qty;
    }

    public String getPimId() {
        return pimId;
    }

    public long getPrice() {
        return price;
    }

    public long getQty() {
        return qty;
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                pimId,
                String.valueOf(price),
                String.valueOf(qty)
        };
    }

    public static MarketplaceOfferSnapshot fromStringArray(String[] data) {
        if (data.length < 3) {
            throw new IllegalArgumentException("Invalid CSV row: expected 3 columns, got " + data.length);
        }
        return new MarketplaceOfferSnapshot(
                data[0],
                Long.parseLong(data[1]),
                Long.parseLong(data[2])
        );
    }

    public static String[] csvHeaders() {
        return new String[]{"pimId", "price", "qty"};
    }
}
