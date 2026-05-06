package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import static pl.commercelink.invoicing.api.Price.DEFAULT_VAT_RATE;

@Component
public class DeliveryTaxResolver {

    @Autowired
    private SupplierRegistry supplierRegistry;

    public double resolveFor(String supplier) {
        String origin = supplierRegistry.get(supplier).origin();
        boolean isForeignKnown = !"PL".equalsIgnoreCase(origin) && !"XX".equalsIgnoreCase(origin);
        return isForeignKnown ? 1.0 : DEFAULT_VAT_RATE;
    }
}
