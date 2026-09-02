package pl.commercelink.orders.filters;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@DynamoDBTable(tableName = "OrderFilters")
public class OrderFilter {

    public static final String GLOBAL_OWNER = "GLOBAL";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "filterKey")
    private String filterKey;

    @DynamoDBAttribute(attributeName = "name")
    private String name;

    @DynamoDBAttribute(attributeName = "conditions")
    private List<OrderFilterCondition> conditions = new LinkedList<>();

    @DynamoDBVersionAttribute
    private Long version;

    public OrderFilter() {
    }

    public static OrderFilter global(String storeId, String name, List<OrderFilterCondition> conditions) {
        return create(storeId, GLOBAL_OWNER, name, conditions);
    }

    public static OrderFilter ownedBy(String storeId, String userId, String name, List<OrderFilterCondition> conditions) {
        return create(storeId, userId, name, conditions);
    }

    private static OrderFilter create(String storeId, String owner, String name, List<OrderFilterCondition> conditions) {
        OrderFilter filter = new OrderFilter();
        filter.storeId = storeId;
        filter.filterKey = owner + "#" + UUID.randomUUID();
        filter.name = name;
        filter.conditions = new LinkedList<>(conditions);
        return filter;
    }

    @DynamoDBIgnore
    public String getOwner() {
        int separator = filterKey == null ? -1 : filterKey.indexOf('#');
        return separator < 0 ? GLOBAL_OWNER : filterKey.substring(0, separator);
    }

    @DynamoDBIgnore
    public boolean isGlobal() {
        return GLOBAL_OWNER.equals(getOwner());
    }

    @DynamoDBIgnore
    public boolean isVisibleTo(String userId) {
        return isGlobal() || getOwner().equals(userId);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<OrderFilterCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<OrderFilterCondition> conditions) {
        this.conditions = conditions;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
