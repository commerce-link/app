package pl.commercelink.orders.filters;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@DynamoDBTable(tableName = "OrderFilters")
public class OrderFilter {

    public static final String STORE_SCOPE = "STORE";

    private static final char SCOPE_SEPARATOR = '#';

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "filterKey")
    private String filterKey;

    @DynamoDBAttribute(attributeName = "label")
    private String label;

    @DynamoDBAttribute(attributeName = "conditions")
    private List<String> conditions = new LinkedList<>();

    @DynamoDBVersionAttribute
    private Long version;

    public OrderFilter() {
    }

    private OrderFilter(String storeId, String scope, String label, OrderFilterConditions conditions) {
        this.storeId = storeId;
        this.filterKey = scope + SCOPE_SEPARATOR + conditions.fingerprint();
        this.label = label;
        this.conditions = new LinkedList<>(conditions.entries());
    }

    public static OrderFilter sharedWithStore(String storeId, String label, OrderFilterConditions conditions) {
        return new OrderFilter(storeId, STORE_SCOPE, label, conditions);
    }

    public static OrderFilter ownedBy(String storeId, String userId, String label, OrderFilterConditions conditions) {
        return new OrderFilter(storeId, userId, label, conditions);
    }

    @DynamoDBIgnore
    public String getScope() {
        int separator = filterKey == null ? -1 : filterKey.indexOf(SCOPE_SEPARATOR);
        return separator < 0 ? "" : filterKey.substring(0, separator);
    }

    @DynamoDBIgnore
    public boolean isSharedWithStore() {
        return STORE_SCOPE.equals(getScope());
    }

    @DynamoDBIgnore
    public boolean isVisibleTo(String userId) {
        return isSharedWithStore() || (userId != null && userId.equals(getScope()));
    }

    @DynamoDBIgnore
    public OrderFilterConditions conditions() {
        return OrderFilterConditions.ofStored(conditions);
    }

    @DynamoDBIgnore
    public OrderFilter withConditions(String label, OrderFilterConditions conditions) {
        return new OrderFilter(storeId, getScope(), label, conditions);
    }

    @DynamoDBIgnore
    public Map<String, String> getConditionsByField() {
        Map<String, String> byField = new LinkedHashMap<>();
        for (String entry : conditions) {
            OrderFilterConditions.fieldOf(entry)
                    .ifPresent(field -> byField.put(field.name(), OrderFilterConditions.valueOf(entry)));
        }
        return byField;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getFilterKey() {
        return filterKey;
    }

    public void setFilterKey(String filterKey) {
        this.filterKey = filterKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
