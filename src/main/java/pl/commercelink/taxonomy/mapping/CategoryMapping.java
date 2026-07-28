package pl.commercelink.taxonomy.mapping;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@DynamoDBTable(tableName = "TaxonomyCategoryMappings")
public class CategoryMapping {

    public static final String LEARNING = "LEARNING";
    public static final String ACTIVE = "ACTIVE";
    static final int CANDIDATE_CAP = 20;

    @DynamoDBHashKey(attributeName = "supplier")
    private String supplier;

    @DynamoDBRangeKey(attributeName = "rawCategory")
    private String rawCategory;

    @DynamoDBAttribute(attributeName = "rawCategoryOriginal")
    private String rawCategoryOriginal;

    @DynamoDBAttribute(attributeName = "counts")
    private Map<String, Integer> counts;

    @DynamoDBAttribute(attributeName = "total")
    private Integer total;

    @DynamoDBAttribute(attributeName = "activeCategoryId")
    private String activeCategoryId;

    @DynamoDBAttribute(attributeName = "activeCategoryName")
    private String activeCategoryName;

    @DynamoDBAttribute(attributeName = "state")
    private String state;

    @DynamoDBAttribute(attributeName = "lastMatchAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime lastMatchAt;

    @DynamoDBVersionAttribute
    private Long version;

    public CategoryMapping() {
    }

    public static CategoryMapping learning(String supplier, String rawCategoryKey, String rawCategoryOriginal) {
        CategoryMapping mapping = new CategoryMapping();
        mapping.supplier = supplier;
        mapping.rawCategory = rawCategoryKey;
        mapping.rawCategoryOriginal = rawCategoryOriginal;
        mapping.counts = new HashMap<>();
        mapping.total = 0;
        mapping.state = LEARNING;
        return mapping;
    }

    public boolean recordSample(String categoryId, String categoryName, int minSamples, double minShare) {
        Map<String, Integer> current = counts != null ? new HashMap<>(counts) : new HashMap<>();
        if (!current.containsKey(categoryId) && current.size() >= CANDIDATE_CAP) {
            return false;
        }
        current.merge(categoryId, 1, Integer::sum);
        counts = current;
        total = (total == null ? 0 : total) + 1;
        lastMatchAt = LocalDateTime.now();
        recomputeState(categoryId, categoryName, minSamples, minShare);
        return true;
    }

    private void recomputeState(String sampledCategoryId, String sampledCategoryName, int minSamples, double minShare) {
        Map.Entry<String, Integer> winner = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        boolean promoted = winner.getValue() >= minSamples && winner.getValue() >= minShare * total;
        if (promoted) {
            state = ACTIVE;
            activeCategoryId = winner.getKey();
            if (winner.getKey().equals(sampledCategoryId)) {
                activeCategoryName = sampledCategoryName;
            }
        } else {
            state = LEARNING;
            activeCategoryId = null;
            activeCategoryName = null;
        }
    }

    @DynamoDBIgnore
    public boolean isActive() {
        return ACTIVE.equals(state);
    }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getRawCategory() { return rawCategory; }
    public void setRawCategory(String rawCategory) { this.rawCategory = rawCategory; }
    public String getRawCategoryOriginal() { return rawCategoryOriginal; }
    public void setRawCategoryOriginal(String rawCategoryOriginal) { this.rawCategoryOriginal = rawCategoryOriginal; }
    public Map<String, Integer> getCounts() { return counts; }
    public void setCounts(Map<String, Integer> counts) { this.counts = counts; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public String getActiveCategoryId() { return activeCategoryId; }
    public void setActiveCategoryId(String activeCategoryId) { this.activeCategoryId = activeCategoryId; }
    public String getActiveCategoryName() { return activeCategoryName; }
    public void setActiveCategoryName(String activeCategoryName) { this.activeCategoryName = activeCategoryName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public LocalDateTime getLastMatchAt() { return lastMatchAt; }
    public void setLastMatchAt(LocalDateTime lastMatchAt) { this.lastMatchAt = lastMatchAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
