package pl.commercelink.marketplace;

import pl.commercelink.starter.csv.CSVReady;

public class MarketplaceOfferSnapshot implements CSVReady {

    private String pimId;
    private long price;
    private long qty;
    private int removalAttempts;

    public MarketplaceOfferSnapshot(String pimId, long price, long qty, int removalAttempts) {
        this.pimId = pimId;
        this.price = price;
        this.qty = qty;
        this.removalAttempts = removalAttempts;
    }

    public boolean hasPimId(String otherPimId) {
        return this.pimId.equals(otherPimId);
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

    public int getRemovalAttempts() {
        return removalAttempts;
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                pimId,
                String.valueOf(price),
                String.valueOf(qty),
                String.valueOf(removalAttempts)
        };
    }

    public static MarketplaceOfferSnapshot fromStringArray(String[] data) {
        if (data.length == 4) {
            return new MarketplaceOfferSnapshot(
                    data[0],
                    Long.parseLong(data[1]),
                    Long.parseLong(data[2]),
                    Integer.parseInt(data[3])
            );
        }
        if (data.length == 3) {
            return new MarketplaceOfferSnapshot(
                    data[0],
                    Long.parseLong(data[1]),
                    Long.parseLong(data[2]),
                    0
            );
        }
        throw new IllegalArgumentException("Invalid CSV row: expected 3 (legacy) or 4 columns, got " + data.length);
    }

    public static String[] csvHeaders() {
        return new String[]{"pimId", "price", "qty", "removalAttempts"};
    }
}
