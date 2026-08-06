package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;
import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

@Component
public class DeliveryFulfilmentUpdateService {

    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public OperationResult<Void> run(String storeId, String provider, DeliveryFulfilmentUpdateForm form) {
        if (StringUtils.isBlank(form.getEan()) || StringUtils.isBlank(form.getMfn()) || form.getUnitCost() < 0) {
            return OperationResult.failure("error.message.delivery.fulfilment.invalid");
        }

        String ean = unifyEan(form.getEan());
        String mfn = unifyMfn(form.getMfn());

        int requested = form.getAllocations().size() + form.getWarehouseItemIds().size();
        int updated = 0;
        for (DeliveryFulfilmentUpdateForm.AllocationRef ref : form.getAllocations()) {
            if (orderAllocationsManager.updateFulfilment(storeId, provider, ref.getOrderId(), ref.getItemId(), ean, mfn, form.getUnitCost())) {
                updated++;
            }
        }
        for (String warehouseItemId : form.getWarehouseItemIds()) {
            if (warehouseAllocationsManager.updateFulfilment(storeId, provider, warehouseItemId, ean, mfn, form.getUnitCost())) {
                updated++;
            }
        }

        if (updated == 0) {
            return OperationResult.failure("error.message.delivery.fulfilment.not.editable");
        }
        if (updated < requested) {
            return OperationResult.failure("error.message.delivery.fulfilment.partial");
        }

        return OperationResult.success();
    }
}
