package pl.commercelink.web.dtos;

import org.springframework.web.multipart.MultipartFile;
import pl.commercelink.orders.*;
import pl.commercelink.orders.imports.OrderReferenceType;

public class OfferCreationDto {

    private String storeId;
    private MultipartFile csvFile;
    private String offerName;
    private String type;

    public MultipartFile getCsvFile() {
        return csvFile;
    }

    public void setCsvFile(MultipartFile csvFile) {
        this.csvFile = csvFile;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getOfferName() {
        return offerName;
    }

    public void setOfferName(String offerName) {
        this.offerName = offerName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}