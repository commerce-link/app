package pl.commercelink.offer;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.products.ProductCategoryLocalization;
import pl.commercelink.taxonomy.ProductGroup;
import pl.commercelink.products.ProductGroupLocalization;

import java.util.List;

public class ProductCategoryTree {

    @JsonProperty("categoryId")
    private String categoryId;
    @JsonProperty("name")
    private String name;

    @JsonProperty("requiredDuringOrder")
    private boolean requiredDuringOrder;
    @JsonProperty("sequenceNumber")
    private int sequenceNumber;
    @JsonProperty("groupingOrder")
    private List<String> groupingOrder;
    @JsonProperty("maxQty")
    private int maxQty;

    @JsonProperty("productCategory")
    private ProductCategory productCategory;
    @JsonProperty("productGroup")
    private ProductGroup productGroup;

    @JsonProperty("categoryTree")
    private String categoryTree;

    private ProductCategoryTree() {
    }

    public ProductCategoryTree(CategoryDefinition categoryDefinition, boolean isDuplicated) {
        this.categoryId = categoryDefinition.getCategoryId();
        this.name = categoryDefinition.getName();

        this.requiredDuringOrder = categoryDefinition.isRequiredDuringOrder();
        this.sequenceNumber = categoryDefinition.getSequenceNumber();
        this.groupingOrder = categoryDefinition.getGroupingOrder();
        this.maxQty = categoryDefinition.getMaxQty();

        this.productCategory = categoryDefinition.getCategory();
        this.productGroup = categoryDefinition.getCategory().getProductGroup();

        String productCategoryLocalizedName = ProductCategoryLocalization.INSTANCE.name(categoryDefinition.getCategory());
        String productGroupLocalizedName = ProductGroupLocalization.INSTANCE.name(categoryDefinition.getCategory().getProductGroup());

        if (isDuplicated) {
            this.categoryTree = productGroupLocalizedName + "/" + productCategoryLocalizedName + "/" + name;
        } else {
            this.categoryTree = productGroupLocalizedName + "/" + productCategoryLocalizedName;
        }
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public boolean isRequiredDuringOrder() {
        return requiredDuringOrder;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public List<String> getGroupingOrder() {
        return groupingOrder;
    }

    public int getMaxQty() {
        return maxQty;
    }

    public ProductCategory getProductCategory() {
        return productCategory;
    }

    public ProductGroup getProductGroup() {
        return productGroup;
    }

    public String getCategoryTree() {
        return categoryTree;
    }
}
