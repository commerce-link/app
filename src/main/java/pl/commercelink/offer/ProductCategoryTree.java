package pl.commercelink.offer;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.InventoryCategoryBridge;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.taxonomy.ProductCategories;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.ProductGroup;

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
    private String productCategory;
    @JsonProperty("productGroup")
    private ProductGroup productGroup;

    @JsonProperty("categoryTree")
    private String categoryTree;

    private ProductCategoryTree() {
    }

    public ProductCategoryTree(CategoryDefinition categoryDefinition,
                               boolean isDuplicated,
                               String productCategoryLocalizedName,
                               String productGroupLocalizedName) {
        this.categoryId = categoryDefinition.getCategoryId();
        this.name = categoryDefinition.getName();

        this.requiredDuringOrder = categoryDefinition.isRequiredDuringOrder();
        this.sequenceNumber = categoryDefinition.getSequenceNumber();
        this.groupingOrder = categoryDefinition.getGroupingOrder();
        this.maxQty = categoryDefinition.getMaxQty();

        this.productCategory = categoryDefinition.getCategory();
        this.productGroup = ProductCategories
                .tryParse(InventoryCategoryBridge.toInventoryCategory(categoryDefinition.getCategory()))
                .map(ProductCategory::getProductGroup)
                .orElse(null);

        String groupPrefix = StringUtils.isNotBlank(productGroupLocalizedName) ? productGroupLocalizedName + "/" : "";
        if (isDuplicated) {
            this.categoryTree = groupPrefix + productCategoryLocalizedName + "/" + name;
        } else {
            this.categoryTree = groupPrefix + productCategoryLocalizedName;
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

    public String getProductCategory() {
        return productCategory;
    }

    public ProductGroup getProductGroup() {
        return productGroup;
    }

    public String getCategoryTree() {
        return categoryTree;
    }
}
