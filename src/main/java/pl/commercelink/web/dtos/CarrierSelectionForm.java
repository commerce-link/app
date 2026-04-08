package pl.commercelink.web.dtos;

import java.util.List;

public class CarrierSelectionForm {

    private String storeId;
    private List<CarrierSelection> carriers;

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public List<CarrierSelection> getCarriers() {
        return carriers;
    }

    public void setCarriers(List<CarrierSelection> carriers) {
        this.carriers = carriers;
    }

    public static class CarrierSelection {
        private String id;
        private String name;
        private String displayName;
        private boolean selected;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
}
