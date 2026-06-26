package pl.commercelink.taxonomy;

public enum ItemType {
    PRODUCT,
    SERVICE;

    public static ItemType of(String groupKey) {
        return "Services".equals(groupKey) ? SERVICE : PRODUCT;
    }
}
