package pl.commercelink.taxonomy;

public enum ItemType {
    PRODUCT,
    SERVICE;

    public static ItemType of(ProductGroup productGroup) {
        return productGroup == ProductGroup.Services ? SERVICE : PRODUCT;
    }

    public static ItemType of(String groupKey) {
        return ProductGroup.Services.name().equals(groupKey) ? SERVICE : PRODUCT;
    }
}
