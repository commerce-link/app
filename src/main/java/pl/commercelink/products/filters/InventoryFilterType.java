package pl.commercelink.products.filters;

import java.util.ArrayList;
import java.util.List;

public enum InventoryFilterType {
    BRAND_NAME(BrandNameInventoryFilter.class),
    PRICE_RANGE(PriceRangeInventoryFilter.class),
    PRODUCT_LINE_BY_BRAND(ProductLineByBrandInventoryFilter.class),
    PRODUCT_LINE_BY_BRAND_NOT_CONTAIN(ProductLineByBrandNotContainInventoryFilter.class),
    PRODUCT_TITLE_CONTAINS(ProductTitleContainsInventoryFilter.class),
    PRODUCT_TITLE_DOES_NOT_CONTAIN(ProductTitleDoesNotContainInventoryFilter.class),
    PRODUCT_EAN_BY_BRAND_NOT_EQ(ProductEanByBrandNotEqInventoryFilter.class),
    EAN_NOT_EQ(EanNotEqInventoryFilter.class);

    private final Class<? extends InventoryFilter> clazz;

    InventoryFilterType(Class<? extends InventoryFilter> clazz) {
        this.clazz = clazz;
    }

    public InventoryFilter getInstance() throws IllegalAccessException, InstantiationException {
        return clazz.newInstance();
    }

    public static List<InventoryFilter> getInstances() throws IllegalAccessException, InstantiationException {
        List<InventoryFilter> instances = new ArrayList<>();
        for (InventoryFilterType type : values()) {
            instances.add(type.getInstance());
        }
        return instances;
    }
}