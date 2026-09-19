package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.WarehouseConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Warehouse documents: whether goods-in and goods-out create documents, and the invoicing system ids they are issued
 * with. The ids are kept when documents are switched off, so switching them on again needs no retyping.
 */
@Getter
@Setter
public class WarehouseDocumentsForm {

    private boolean documentsEnabled;
    private String warehouseId;
    private String costCenterId;

    public static WarehouseDocumentsForm from(Store store) {
        WarehouseDocumentsForm form = new WarehouseDocumentsForm();
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        if (configuration != null) {
            form.documentsEnabled = configuration.isDocumentsGenerationEnabled();
            form.warehouseId = configuration.getWarehouseId();
            form.costCenterId = configuration.getCostCenterId();
        }
        return form;
    }

    // Goods-in and goods-out fail on missing ids only once documents are on, so the ids are required only then.
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (documentsEnabled) {
            FormRules.requireText(errors, "warehouseId", warehouseId, "store.warehouse.documents.warehouseId.required");
            FormRules.requireText(errors, "costCenterId", costCenterId, "store.warehouse.documents.costCenterId.required");
        }
        return errors;
    }

    public void applyTo(Store store) {
        WarehouseConfiguration configuration = store.getWarehouseConfiguration();
        if (configuration == null) {
            configuration = new WarehouseConfiguration();
            store.setWarehouseConfiguration(configuration);
        }
        configuration.setDocumentsGenerationEnabled(documentsEnabled);
        configuration.setWarehouseId(StringUtils.trimToNull(warehouseId));
        configuration.setCostCenterId(StringUtils.trimToNull(costCenterId));
    }
}
