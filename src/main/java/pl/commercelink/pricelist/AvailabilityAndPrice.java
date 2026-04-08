package pl.commercelink.pricelist;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.taxonomy.ProductCategory;

public class AvailabilityAndPrice  implements CSVReady {

    public static final String[] HEADERS = { "PimId","EAN", "Mfn", "Brand", "Label", "Name", "Category", "Price", "Qty", "Estimated Delivery Days", "Lowest 30 Days Price"};

    @JsonProperty("pimId")
    private String pimId;
    @JsonProperty("ean")
    private String ean;
    @JsonProperty("mfn")
    private String manufacturerCode;
    @JsonProperty("brand")
    private String brand;
    @JsonProperty("label")
    private String label;
    @JsonProperty("name")
    private String name;
    @JsonProperty("category")
    private ProductCategory category;
    @JsonProperty("price")
    private long price;
    @JsonProperty("qty")
    private long qty;
    @JsonProperty("estimatedDeliveryDays")
    private int estimatedDeliveryDays;
    @JsonProperty("lowest30DaysPrice")
    private long lowest30DaysPrice;

    private AvailabilityAndPrice() {
    }
    

    public AvailabilityAndPrice(String pimId, String ean,  String manufacturerCode,
                                String brand, String label, String name, ProductCategory category,
                                long price, long qty, int estimatedDeliveryDays, long lowest30DaysPrice) {
        this.ean = ean;
        this.pimId = pimId;
        this.manufacturerCode = manufacturerCode;
        this.brand = brand;
        this.label = label;
        this.name = name;
        this.category = category;
        this.price = price;
        this.qty = qty;
        this.estimatedDeliveryDays = estimatedDeliveryDays;
        this.lowest30DaysPrice = lowest30DaysPrice;
    }

    public String getEan() {
        return ean;
    }
    
    public String getPimId() {
        return pimId;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public String getBrand() {
        return brand;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public long getPrice() {
        return price;
    }

    public long getQty() {
        return qty;
    }

    public int getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public long getLowest30DaysPrice() {
        return lowest30DaysPrice;
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                pimId,
                ean,
                manufacturerCode,
                brand,
                label,
                name,
                category.toString(),
                String.valueOf(price),
                String.valueOf(qty),
                String.valueOf(estimatedDeliveryDays),
                String.valueOf(lowest30DaysPrice)
        };
    }
}
