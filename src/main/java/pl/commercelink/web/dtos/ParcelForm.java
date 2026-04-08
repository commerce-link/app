package pl.commercelink.web.dtos;

import pl.commercelink.stores.Parcel;

import java.util.List;

public class ParcelForm {
    private String storeId;
    private String templateId;
    private String templateName;
    private List<Parcel> parcels;

    public ParcelForm() {
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public List<Parcel> getParcels() {
        return parcels;
    }

    public void setParcels(List<Parcel> parcels) {
        this.parcels = parcels;
    }
}
