package pl.commercelink.orders.filters.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@DynamoDBTable(tableName = "OrderFilters")
public class OwnedOrderFilters {

    public static final String WHOLE_STORE = "default";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "userId")
    private String userId;

    @DynamoDBAttribute(attributeName = "filters")
    private List<OrderFilter> filters = new LinkedList<>();

    @DynamoDBVersionAttribute
    private Long version;

    public OwnedOrderFilters() {
    }

    public static OwnedOrderFilters emptyFor(String storeId, String userId) {
        OwnedOrderFilters owned = new OwnedOrderFilters();
        owned.storeId = storeId;
        owned.userId = userId;
        return owned;
    }

    @DynamoDBIgnore
    public boolean isWholeStore() {
        return WHOLE_STORE.equals(userId);
    }

    @DynamoDBIgnore
    public Optional<OrderFilter> byId(String filterId) {
        return filters.stream().filter(filter -> filter.getId().equals(filterId)).findFirst();
    }

    @DynamoDBIgnore
    public void add(OrderFilter filter) {
        filters.add(filter);
    }

    @DynamoDBIgnore
    public boolean remove(String filterId) {
        return filters.removeIf(filter -> filter.getId().equals(filterId));
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<OrderFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<OrderFilter> filters) {
        this.filters = filters;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
