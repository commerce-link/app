package pl.commercelink.offer;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;

import java.util.List;

public class ProductCategoryTree {

    @JsonProperty("categoryId")
    private String categoryId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("path")
    private String path;

    @JsonProperty("requiredDuringOrder")
    private boolean requiredDuringOrder;
    @JsonProperty("sequenceNumber")
    private int sequenceNumber;
    @JsonProperty("groupingOrder")
    private List<String> groupingOrder;
    @JsonProperty("maxQty")
    private int maxQty;

    private ProductCategoryTree() {
    }

    public ProductCategoryTree(CategoryDefinition categoryDefinition, String catalogName) {
        this.categoryId = categoryDefinition.getCategoryId();
        this.name = categoryDefinition.getName();
        this.path = StringUtils.isNotBlank(catalogName) ? catalogName + "/" + name : name;

        this.requiredDuringOrder = categoryDefinition.isRequiredDuringOrder();
        this.sequenceNumber = categoryDefinition.getSequenceNumber();
        this.groupingOrder = categoryDefinition.getGroupingOrder();
        this.maxQty = categoryDefinition.getMaxQty();
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
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
}
