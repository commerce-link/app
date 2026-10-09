package pl.commercelink.shipping;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderListService;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.rma.RMARepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The store's packages that wait for "Zamów odbiór", read from the shipments of its orders and RMAs. Customer returns
 * and the warehouse order their pickup right away and have no pickup address on the page, so they are never listed.
 */
@Component
@RequiredArgsConstructor
public class PickupCandidates {

    private final OrdersRepository ordersRepository;
    private final RMARepository rmaRepository;

    /** One entry per package (every parcel row of a package carries its externalId), ordered by package id. */
    public List<PickupCandidate> of(String storeId) {
        // the open statuses hold every order whose package can wait: Completed needs every shipment delivered
        // (Order.hasNothingLeftToDeliver), and Cancelled needs it Delivered with every product returned; a manual
        // "Dostarczone" plus all items returned can cancel an order whose parcel still waits, which then drops off this
        // page on purpose: nothing is left to collect
        return of(storeId, ordersRepository.findByStoreAndStatuses(storeId, OrderListService.OPEN));
    }

    /**
     * How many packages the pickup page would list, from the store's open orders already read by the caller (the
     * orders list reads the same ones), so only the RMAs are read here.
     */
    public int count(String storeId, List<Order> openOrders) {
        return of(storeId, openOrders).size();
    }

    private List<PickupCandidate> of(String storeId, List<Order> openOrders) {
        Map<String, PickupCandidate> byPackage = new LinkedHashMap<>();
        openOrders.forEach(order -> add(byPackage, ShipmentOwnerType.ORDER, order.getOrderId(), order.getShipments()));
        // every status: the operator may close an RMA right after its shipment was created, the package still waits
        rmaRepository.findAllByStoreId(storeId).forEach(rma ->
                add(byPackage, ShipmentOwnerType.RMA, rma.getRmaId(), rma.getShipments()));
        return byPackage.values().stream().sorted(Comparator.comparing(PickupCandidate::externalId)).toList();
    }

    private static void add(Map<String, PickupCandidate> byPackage, ShipmentOwnerType ownerType, String ownerId,
                            List<Shipment> shipments) {
        if (shipments == null) {
            return;
        }
        shipments.stream().filter(ShipmentLinks::listedForPickup).forEach(s ->
                byPackage.putIfAbsent(s.getExternalId(), new PickupCandidate(ownerType, ownerId, s.getExternalId(),
                        s.getTrackingNo(), s.getProvider(), s.getCarrier(), s.getPickUpAddressId())));
    }
}
