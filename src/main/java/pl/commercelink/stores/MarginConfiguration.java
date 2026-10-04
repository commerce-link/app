package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * What the store counts as a low margin (Settings › Margins): a default percent and percents for chosen product
 * categories. An order item whose margin is below the threshold of its category, or the default for a category without
 * one, is marked as low-margin on the order page. Both are optional: with neither, only a sale below cost is marked.
 * Categories are matched by name, ignoring case and surrounding spaces, against the item's category (the catalog
 * category the item was added from, e.g. "CPU").
 */
@DynamoDBDocument
public class MarginConfiguration {

    /** The percent an item's margin is compared with, and the category it belongs to (null: the store's default). */
    public record Threshold(double percent, String category) {
    }

    @DynamoDBAttribute(attributeName = "defaultPercent")
    private Double defaultPercent;
    @DynamoDBAttribute(attributeName = "categories")
    private List<CategoryMargin> categories = new LinkedList<>();

    public MarginConfiguration() {
    }

    public MarginConfiguration(Double defaultPercent, List<CategoryMargin> categories) {
        this.defaultPercent = defaultPercent;
        this.categories = new LinkedList<>(categories);
    }

    /** The threshold for an item of this category: the category's own, else the default; null when neither is set. */
    @DynamoDBIgnore
    public Threshold thresholdFor(String category) {
        String wanted = normalize(category);
        if (wanted != null && categories != null) {
            for (CategoryMargin margin : categories) {
                if (margin.getPercent() != null && wanted.equals(normalize(margin.getCategory()))) {
                    return new Threshold(margin.getPercent(), margin.getCategory());
                }
            }
        }
        return defaultPercent == null ? null : new Threshold(defaultPercent, null);
    }

    private static String normalize(String category) {
        String trimmed = StringUtils.trimToNull(category);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public Double getDefaultPercent() {
        return defaultPercent;
    }

    public void setDefaultPercent(Double defaultPercent) {
        this.defaultPercent = defaultPercent;
    }

    public List<CategoryMargin> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryMargin> categories) {
        this.categories = categories;
    }

    @DynamoDBDocument
    public static class CategoryMargin {
        @DynamoDBAttribute(attributeName = "category")
        private String category;
        @DynamoDBAttribute(attributeName = "percent")
        private Double percent;

        public CategoryMargin() {
        }

        public CategoryMargin(String category, Double percent) {
            this.category = category;
            this.percent = percent;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Double getPercent() {
            return percent;
        }

        public void setPercent(Double percent) {
            this.percent = percent;
        }
    }
}
