package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

@DynamoDBDocument
public class WarehouseConfiguration {

    @DynamoDBAttribute(attributeName = "warehouseId")
    private String warehouseId;

    @DynamoDBAttribute(attributeName = "costCenterId")
    private String costCenterId;

    @DynamoDBAttribute(attributeName = "documentsGenerationEnabled")
    private boolean documentsGenerationEnabled;

    public WarehouseConfiguration() {
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCostCenterId() {
        return costCenterId;
    }

    public void setCostCenterId(String costCenterId) {
        this.costCenterId = costCenterId;
    }

    public boolean isDocumentsGenerationEnabled() {
        return documentsGenerationEnabled;
    }

    public void setDocumentsGenerationEnabled(boolean documentsGenerationEnabled) {
        this.documentsGenerationEnabled = documentsGenerationEnabled;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return StringUtils.isNotBlank(warehouseId) && StringUtils.isNotBlank(costCenterId);
    }
}
