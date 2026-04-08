package pl.commercelink.orders.rma;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.*;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RMAShippingService {

    @Autowired
    private ShippingService shippingService;

    public List<RMAReturnOption> getAvailableReturnOptions(Store store) {
        return store.getPackageTemplates()
                .stream()
                .filter(template -> template.getName().startsWith("RMA - "))
                .map(RMAReturnOption::from)
                .collect(Collectors.toList());
    }

    public RMAShipmentResult createReturnShipment(RMAShipmentRequest request, Store store) {
        validateStoreReturnConfiguration(store);

        AuthorizedCarrier ac = store.getRmaSettings().getCarrier();
        Carrier carrier = new Carrier(ac.getId(), ac.getName(), ac.getDisplayName());

        PackageTemplate packageTemplate = store.getPackageTemplate(request.getPackageTemplateId());

        List<ParcelForm> parcels = shippingService.retrieveParcelsListBasedOnPackageTemplate(
                request.getInsuranceValue(),
                String.valueOf(packageTemplate.getId()),
                store
        );

        OperationResult<List<Shipment>> shipments = shippingService.createShipping(
                request.getCustomerAddress(),
                parcels,
                carrier,
                store
        );

        return new RMAShipmentResult(shipments);
    }

    public void validateStoreReturnConfiguration(Store store) {
        if (store.getRmaSettings() == null || store.getRmaSettings().getCarrier() == null) {
            throw new InvalidReturnConfigurationException(
                    "Store return settings not configured. Contact store administrator."
            );
        }
    }
}
