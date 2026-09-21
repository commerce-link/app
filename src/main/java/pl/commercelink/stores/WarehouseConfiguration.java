package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public void removePrinter(String id) {
        printers.removeIf(printer -> id.equals(printer.getId()));
    }

    @DynamoDBIgnore
    public Optional<Printer> findPrinter(String id) {
        return printers.stream().filter(printer -> id.equals(printer.getId())).findFirst();
    }

    // Label printing picks a printer by its name, so two printers must not share one.
    @DynamoDBIgnore
    public boolean isPrinterNameTaken(String name, String exceptPrinterId) {
        String wanted = StringUtils.trimToEmpty(name);
        return printers.stream()
                .filter(printer -> exceptPrinterId == null || !exceptPrinterId.equals(printer.getId()))
                .anyMatch(printer -> wanted.equalsIgnoreCase(StringUtils.trimToEmpty(printer.getName())));
    }

    @DynamoDBIgnore
    public boolean assignMissingPrinterIds() {
        boolean changed = false;
        for (Printer printer : printers) {
            if (StringUtils.isBlank(printer.getId())) {
                printer.setId(UUID.randomUUID().toString());
                changed = true;
            }
        }
        return changed;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return StringUtils.isNotBlank(warehouseId) && StringUtils.isNotBlank(costCenterId);
    }
}
