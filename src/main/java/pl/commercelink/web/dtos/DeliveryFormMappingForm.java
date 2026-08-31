package pl.commercelink.web.dtos;

import java.util.List;

public class DeliveryFormMappingForm {

    private String storeId;
    private List<DeliveryFormMapping> mappings;

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public List<DeliveryFormMapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<DeliveryFormMapping> mappings) {
        this.mappings = mappings;
    }

    public static class DeliveryFormMapping {
        private String source;
        private String deliveryForm;
        private String carrier;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDeliveryForm() {
            return deliveryForm;
        }

        public void setDeliveryForm(String deliveryForm) {
            this.deliveryForm = deliveryForm;
        }

        public String getCarrier() {
            return carrier;
        }

        public void setCarrier(String carrier) {
            this.carrier = carrier;
        }
    }
}
