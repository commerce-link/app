package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;

@DynamoDBDocument
public class WarehouseConfiguration {

    @DynamoDBAttribute(attributeName = "warehouseId")
    private String warehouseId;

    @DynamoDBAttribute(attributeName = "costCenterId")
    private String costCenterId;

    @DynamoDBAttribute(attributeName = "documentsGenerationEnabled")
    private boolean documentsGenerationEnabled;

    @DynamoDBAttribute(attributeName = "printers")
    private List<Printer> printers = new LinkedList<>();

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

    public List<Printer> getPrinters() {
        return printers;
    }

    public void setPrinters(List<Printer> printers) {
        this.printers = printers;
    }

    @DynamoDBIgnore
    public void addPrinter(Printer printer) {
        printers.add(printer);
    }

    @DynamoDBIgnore
    public void removePrinter(String name) {
        printers.removeIf(printer -> name.equals(printer.getName()));
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return StringUtils.isNotBlank(warehouseId) && StringUtils.isNotBlank(costCenterId);
    }
}
