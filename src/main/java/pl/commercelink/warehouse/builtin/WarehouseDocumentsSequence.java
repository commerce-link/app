package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "WarehouseDocumentsSequences")
public class WarehouseDocumentsSequence {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;

    @DynamoDBRangeKey(attributeName = "sequenceKey")
    private String sequenceKey;

    @DynamoDBAttribute(attributeName = "currentValue")
    private long currentValue;

    public WarehouseDocumentsSequence() {
    }

    public WarehouseDocumentsSequence(String storeId, String sequenceKey, long currentValue) {
        this.storeId = storeId;
        this.sequenceKey = sequenceKey;
        this.currentValue = currentValue;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getSequenceKey() {
        return sequenceKey;
    }

    public void setSequenceKey(String sequenceKey) {
        this.sequenceKey = sequenceKey;
    }

    public long getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(long currentValue) {
        this.currentValue = currentValue;
    }
}
