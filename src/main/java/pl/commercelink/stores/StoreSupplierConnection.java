package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

@DynamoDBDocument
public class StoreSupplierConnection {

    @DynamoDBAttribute(attributeName = "supplierName")
    private String supplierName;

    @DynamoDBAttribute(attributeName = "mode")
    @DynamoDBTypeConvertedEnum
    private ConnectionMode mode = ConnectionMode.GLOBAL;

    public StoreSupplierConnection() {
    }

    public StoreSupplierConnection(String supplierName, ConnectionMode mode) {
        this.supplierName = supplierName;
        this.mode = mode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }
}
