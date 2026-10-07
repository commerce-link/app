package pl.commercelink.orders.rma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.ShipmentLinks;
import pl.commercelink.shipping.ShippingService;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderLabels;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RmaShipmentsViewFactory {

    private final ShippingService shippingService;
    private final StoresRepository storesRepository;

    /** closed: a closed RMA keeps its record, only the label stays downloadable. */
    public RmaShipmentsView build(RMA rma, boolean closed, Locale locale) {
        List<Shipment> shipments = rma.getShipments() == null ? List.of() : rma.getShipments();
        Store store = shipments.stream().anyMatch(ShipmentLinks::hasPackage)
                ? storesRepository.findById(rma.getStoreId()) : null;
        String details = "/dashboard/rma/" + rma.getRmaId();
        boolean returnRetry = !closed && CustomerReturnRetry.possible(rma);
        Set<String> labelProviders = ShipmentLinks.labelProviders(shipments,
                provider -> shippingService.supportsLabels(store, provider));
        List<RmaShipmentsView.Row> rows = shipments.stream().map(s -> {
            OrderLabels.ShipmentState state = OrderLabels.shipmentState(s, locale);
            return new RmaShipmentsView.Row(s,
                    state == null ? null : state.key(), state == null ? null : state.args(),
                    state == null ? null : state.tone(),
                    ShipmentLinks.hasPackage(s) && labelProviders.contains(s.getProvider())
                            ? ShipmentLinks.label(s.getProvider(), s.getExternalId(), details) : null,
                    !closed && s.creationFailed() && !isCustomerReturn(s) ? details + "#rmaItemsForm" : null,
                    !closed && s.creationFailed() ? removeAction(details, s) : null,
                    !closed && isCustomerReturn(s) && s.awaitsPickup() && s.getExternalId() != null
                            ? pickupRetryAction(details, s) : null,
                    returnRetry && isCustomerReturn(s) && s.creationFailed() ? details + "/return-shipment/retry" : null);
        }).toList();
        LocalDateTime now = LocalDateTime.now();
        String pollHref = shipments.stream().anyMatch(s -> s.awaitsProviderAnswer(now)) ? details + "/shipments/state" : null;
        return new RmaShipmentsView(rows, closed ? null : ShipmentLinks.pickup(shipments, details), pollHref);
    }

    private static boolean isCustomerReturn(Shipment s) {
        return CustomerReturnRetry.isCustomerReturn(s);
    }

    private static String removeAction(String details, Shipment s) {
        return UriComponentsBuilder.fromPath(details + "/shipments/creations/{commandId}/remove")
                .buildAndExpand(s.getCreation().getCommandId()).encode().toUriString();
    }

    private static String pickupRetryAction(String details, Shipment s) {
        return UriComponentsBuilder.fromPath(details + "/shipments/{externalId}/pickup")
                .buildAndExpand(s.getExternalId()).encode().toUriString();
    }
}
