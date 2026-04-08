package pl.commercelink.products;

import pl.commercelink.starter.localization.EnumMessageResolver;
import pl.commercelink.taxonomy.ProductCategory;

public class ProductCategoryLocalization {

    public static final ProductCategoryLocalization INSTANCE = new ProductCategoryLocalization();

    public String name(ProductCategory c) {
        return plural(c);
    }

    public String singular(ProductCategory c) {
        return EnumMessageResolver.get("product.category." + c.name() + ".singular");
    }

    public String plural(ProductCategory c) {
        return EnumMessageResolver.get("product.category." + c.name() + ".plural");
    }

}
