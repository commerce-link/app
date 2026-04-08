package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import pl.commercelink.orders.ShippingDetails;

@DynamoDBTable(tableName = "RMACenters")
public class RMACenter {

    public static final String MANAGED_RMA_CENTER_STORE_ID = "default";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "rmaCenterId")
    private String rmaCenterId;
    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    @DynamoDBAttribute(attributeName = "shippingDetails")
    private ShippingDetails shippingDetails;

    public RMACenter() {
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getRmaCenterId() {
        return rmaCenterId;
    }

    public void setRmaCenterId(String rmaCenterId) {
        this.rmaCenterId = rmaCenterId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public ShippingDetails getShippingDetails() {
        return shippingDetails;
    }

    public void setShippingDetails(ShippingDetails shippingDetails) {
        this.shippingDetails = shippingDetails;
    }
}
