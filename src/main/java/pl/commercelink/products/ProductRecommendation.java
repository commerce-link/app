package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ProductRecommendation {

    private String catagoryId;
    private ProductCategory productCategory;

    private String pimId;
    private String brand;
    private String name;
    private String label;
    private String pricingGroup;

    private String ean;
    private String manufacturerCode;

    private double lowestGrossPrice;

    private final InventoryKey inventoryKey;
    private final MatchedInventory matchedInventory;

    public ProductRecommendation(CategoryDefinition categoryDefinition, MatchedInventory matchedInventory, Optional<PimEntry> pimEntry) {
        this.catagoryId = categoryDefinition.getCategoryId();
        this.productCategory = categoryDefinition.getCategory();

        Taxonomy taxonomy = matchedInventory.getTaxonomy();

        if (pimEntry.isPresent()) {
            PimEntry entry = pimEntry.get();

            this.pimId = entry.pimId();
            this.brand = entry.brand();
            this.name = entry.name();
            this.label = StringUtils.isNotBlank(entry.subcategory()) ? entry.subcategory() : entry.name();
        } else {
            this.pimId = null;
            this.brand = taxonomy.brand();
            this.name = taxonomy.name();
            this.label = taxonomy.name();
        }

        this.lowestGrossPrice = matchedInventory.getLowestPrice().grossValue();
        this.pricingGroup = assignPricingGroup(categoryDefinition.getPriceDefinitions(), label, lowestGrossPrice);

        this.ean = taxonomy.ean();
        this.manufacturerCode = taxonomy.mfn();


        this.inventoryKey = matchedInventory.getInventoryKey();
        this.matchedInventory = matchedInventory;

    }

    private String assignPricingGroup(List<PriceDefinition> priceDefinitions, String label, double lowestGrossPrice) {
        for (PriceDefinition priceDefinition : priceDefinitions) {
            if (priceDefinition.matches(label, lowestGrossPrice)) {
                return priceDefinition.getPricingGroup();
            }
        }
        return PriceDefinition.DEFAULT_PRICING_GROUP;
    }

    public Product toProduct() {
        return new Product(
                catagoryId,
                pimId,
                ean,
                manufacturerCode,
                brand,
                label,
                name,
                productCategory,
                pricingGroup
        );
    }

    public String getPimId() {
        return pimId;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getBrand() {
        return brand;
    }

    public String getEan() {
        return ean;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public double getLowestGrossPrice() {
        return lowestGrossPrice;
    }

    public Collection<String> getAlternativeEans() {
        return inventoryKey.getProductEans();
    }

    public Collection<String> getAlternativeProductCodes() {
        return inventoryKey.getProductCodes();
    }

    public Collection<String> getAlternativeSuppliers() {
        return matchedInventory.getSuppliers();
    }

    public InventoryKey getInventoryKey() {
        return inventoryKey;
    }

    public boolean hasPimId() {
        return StringUtils.isNotBlank(pimId);
    }

}
