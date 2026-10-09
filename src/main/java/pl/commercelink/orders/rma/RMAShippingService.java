package pl.commercelink.orders.rma;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.*;
import pl.commercelink.shipping.api.Carrier;
import pl.commercelink.shipping.api.ShipmentRequest;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.Store;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RMAShippingService {

    /** Package templates offered to a customer returning goods are the ones named with this prefix. */
    public static final String RETURN_TEMPLATE_PREFIX = "RMA - ";

    @Autowired
    private ShippingService shippingService;

    @Autowired
    private ShipmentCreationService shipmentCreationService;

    public List<RMAReturnOption> getAvailableReturnOptions(Store store) {
        return store.getPackageTemplates()
                .stream()
                .filter(template -> StringUtils.startsWith(template.getName(), RETURN_TEMPLATE_PREFIX))
                .map(RMAReturnOption::from)
                .collect(Collectors.toList());
    }

    /** The customer's submission: refused while the RMA holds a return already (RmaReturnShipmentOwner). */
    public ShipmentCreationStart startReturnShipment(RMAShipmentRequest request, Store store) {
        return start(request, store, false);
    }

    /** The operator's "Spróbuj ponownie": books the failed return again, in place of its failed row. */
    public ShipmentCreationStart retryReturnShipment(RMAShipmentRequest request, Store store) {
        return start(request, store, true);
    }

    private ShipmentCreationStart start(RMAShipmentRequest request, Store store, boolean replacesFailedReturn) {
        validateStoreReturnConfiguration(store);
        AuthorizedCarrier ac = store.getRmaConfiguration().getCarrier();
        Carrier carrier = new Carrier(ac.getId(), ac.getName(), ac.getDisplayName());
        PackageTemplate packageTemplate = store.getPackageTemplate(request.getPackageTemplateId());
        List<ParcelForm> parcels = shippingService.retrieveParcelsListBasedOnPackageTemplate(
                request.getInsuranceValue(), String.valueOf(packageTemplate.getId()), store);
        ShipmentRequest shipmentRequest = shippingService.buildReturnRequest(request.getCustomerAddress(), parcels, carrier, store);
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setCarrier(ac.getName());
        return shipmentCreationService.start(ShipmentCreationCheckRequest.builder()
                .storeId(store.getStoreId())
                .ownerType(ShipmentOwnerType.RMA_RETURN)
                .ownerId(request.getRmaId())
                .replacesFailedReturn(replacesFailedReturn)
                .build(), shipmentRequest, store, placeholder);
    }

    public void validateStoreReturnConfiguration(Store store) {
        if (store.getRmaConfiguration() == null || store.getRmaConfiguration().getCarrier() == null) {
            throw new InvalidReturnConfigurationException(
                    "Store return settings not configured. Contact store administrator."
            );
        }
    }
}
