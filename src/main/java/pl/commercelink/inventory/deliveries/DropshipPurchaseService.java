package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.api.SupplierConsignee;
import pl.commercelink.inventory.supplier.api.SupplierDropshipRequest;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderOption;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierPickupPoint;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DropshipPurchaseService {

    private final StoresRepository storesRepository;
    private final DeliveriesRepository deliveriesRepository;
    private final DeliveryCreationService deliveryCreationService;
    private final SupplierConnectionModeResolver supplierConnectionModeResolver;
    private final SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    private final SupplierProviderResolver supplierProviderResolver;
    private final OrdersRepository ordersRepository;
    private final DropshipOrderLocator dropshipOrderLocator;

    public boolean isDropshipAvailable(String storeId, String provider) {
        try {
            SupplierProvider supplierProvider = supplierProviderResolver.resolve(storeId, provider);
            return supplierProvider != null && supplierProvider.supportsDropshipping();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPickupPointDropshipAvailable(String storeId, String provider) {
        try {
            SupplierProvider supplierProvider = supplierProviderResolver.resolve(storeId, provider);
            return supplierProvider != null && supplierProvider.supportsDropshipping()
                    && supplierProvider.supportsPickupPointDropship();
        } catch (Exception e) {
            return false;
        }
    }

    public OperationResult<PurchaseSubmission> submitDropship(String storeId, Order order,
                                                              DeliveryCreationForm form, String purchaseRef) {
        String validationError = dropshipValidationError(order, form);
        if (validationError != null) {
            return OperationResult.failure(validationError);
        }
        if (!isValidConsignee(order.getShippingDetails())) {
            return OperationResult.failure("orders.dropship.error.consignee");
        }
        if (!isDropshipAvailable(storeId, form.getProvider())) {
            return OperationResult.failure("orders.dropship.error.unsupported");
        }
        String blocked = purchaseBlockedReason(storeId, order, form.getProvider());
        if (blocked != null) {
            return OperationResult.failure(blocked);
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return OperationResult.failure("deliveries.purchase.error.failed");
        }
        boolean requiresApproval = store.isGlobalSupplier(form.getProvider());

        List<SupplierOrderOption> options = null;
        if (requiresApproval) {
            try {
                options = supplierProviderResolver.resolve(storeId, form.getProvider()).orderOptions(optionsContext(order));
            } catch (Exception e) {
                log.error("Supplier order options lookup failed for a global dropship submission: store={} provider={}",
                        storeId, form.getProvider(), e);
            }
        } else {
            try {
                options = supplierProviderResolver.resolve(storeId, form.getProvider()).orderOptions(optionsContext(order));
            } catch (Exception e) {
                log.error("Supplier order options lookup failed: store={} provider={}", storeId, form.getProvider(), e);
                return OperationResult.failure("deliveries.options.error");
            }
            if (!SupplierOptions.missingRequiredOptions(options, form.getSupplierOptions()).isEmpty()) {
                return OperationResult.failure("deliveries.purchase.error.options");
            }
        }

        Optional<Delivery> existing = deliveriesRepository.findByPurchaseRef(storeId, purchaseRef);
        if (existing.isPresent()) {
            return OperationResult.success(
                    new PurchaseSubmission(existing.get().getDeliveryId(), requiresApproval));
        }

        Delivery delivery = newDropshipDelivery(storeId, store, order, form);
        delivery.setOrderStatus(requiresApproval
                ? DeliveryOrderStatus.AWAITING_APPROVAL
                : DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        if (options != null) {
            SupplierOptions.applySupplierOptions(delivery, options, form.getSupplierOptions());
        } else {
            // The dictionary lookup failed for this global submission - keep the posted values
            // unfiltered (no declared options to validate/label against) rather than losing them.
            delivery.setSupplierOptions(form.getSupplierOptions());
        }
        deliveryCreationService.claimAllocations(storeId, delivery, form);
        deliveriesRepository.save(delivery);

        if (!requiresApproval) {
            supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                    storeId, delivery.getDeliveryId(), form.getProvider(), purchaseRef, order.getOrderId()));
        }
        return OperationResult.success(new PurchaseSubmission(delivery.getDeliveryId(), requiresApproval));
    }

    /** Message key explaining why "order at supplier" is blocked for this order, or null. */
    public String purchaseBlockedReason(String storeId, Order order, String provider) {
        if (!requiresPickupPoint(order)) {
            return null;
        }
        if (!isPickupPointDropshipAvailable(storeId, provider)) {
            return "orders.dropship.error.pickupPointUnsupported";
        }
        try {
            toPickupPoint(order);
            return null;
        } catch (IllegalArgumentException e) {
            return "orders.dropship.error.pickupPointIncomplete";
        }
    }

    /** The order's first shipment, when it is a pickup-point delivery. */
    public static Optional<Shipment> pickupShipment(Order order) {
        return order.firstShipment().filter(shipment -> shipment.getType() == ShipmentType.PickupPoint);
    }

    public static SupplierOrderOptionsContext optionsContext(Order order) {
        return SupplierOrderOptionsContext.dropship(toPickupPoint(order).orElse(null));
    }

    /** The order claiming a dropship delivery's allocations, resolved the same way placement does. */
    public Optional<Order> orderOf(String storeId, Delivery delivery) {
        return dropshipOrderLocator.locate(delivery.getDeliveryId())
                .map(orderId -> ordersRepository.findById(storeId, orderId));
    }

    public OperationResult<String> createManualDropship(String storeId, Order order, DeliveryCreationForm form) {
        String validationError = dropshipValidationError(order, form);
        if (validationError != null) {
            return OperationResult.failure(validationError);
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return OperationResult.failure("deliveries.purchase.error.failed");
        }

        Delivery delivery = newDropshipDelivery(storeId, store, order, form);
        delivery.setExternalDeliveryId(form.getExternalDeliveryId());
        deliveryCreationService.claimAllocations(storeId, delivery, form);
        deliveriesRepository.save(delivery);

        return OperationResult.success(delivery.getDeliveryId());
    }

    /** "Zapisz" with every allocation unchecked and "remove unselected" ticked: free the items, create nothing. */
    public void releaseUnselected(String storeId, DeliveryCreationForm form) {
        deliveryCreationService.releaseUnselectedAllocations(storeId, form);
    }

    private String dropshipValidationError(Order order, DeliveryCreationForm form) {
        if (order.getFulfilmentType() != FulfilmentType.DirectToConsumer) {
            return "orders.dropship.error.fulfilmentType";
        }
        if (!order.hasShippingDetails()) {
            return "orders.dropship.error.address";
        }
        if (!form.hasRequestedItems()) {
            return "orders.dropship.error.nothingSelected";
        }
        return null;
    }

    private static boolean isValidConsignee(ShippingDetails details) {
        try {
            toConsignee(details);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Delivery newDropshipDelivery(String storeId, Store store, Order order, DeliveryCreationForm form) {
        Delivery delivery = new Delivery(storeId, null, form.getProvider());
        delivery.setConnectionMode(supplierConnectionModeResolver.resolve(store, form.getProvider()));
        delivery.setType(DeliveryType.DROPSHIP);
        delivery.setDeliveryAddress(consigneeLabel(order.getShippingDetails()));
        delivery.setEstimatedDeliveryAt(form.getEstimatedDeliveryAt());
        delivery.setShippingCost(form.getShippingCost());
        delivery.setPaymentCost(form.getPaymentCost());
        delivery.setPaymentTerms(form.getPaymentTerms());
        delivery.setTax(form.getTax());
        delivery.addEvent(new Event(EventType.action, SupplierPurchaseService.DELIVERY_CREATED_EVENT, LocalDateTime.now()));
        return delivery;
    }

    SupplierOrderResult placeDropshipOrder(String storeId, Delivery delivery, List<SupplierOrderLine> lines,
                                           String orderId) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null || !order.hasShippingDetails()) {
            throw new SupplierOrderException("Order " + orderId
                    + " has no complete shipping details for a dropship purchase");
        }
        SupplierConsignee consignee;
        SupplierPickupPoint pickupPoint;
        try {
            consignee = toConsignee(order.getShippingDetails());
            pickupPoint = toPickupPoint(order).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new SupplierOrderException(e.getMessage());
        }
        return supplierProviderResolver.resolve(storeId, delivery.getProvider()).placeDropshipOrder(
                new SupplierDropshipRequest(delivery.getPurchaseRef(), lines, consignee,
                        "CommerceLink " + delivery.getPurchaseRef(), pickupPoint, delivery.getSupplierOptions()));
    }

    /** The customer's pickup point, when the order's first shipment is a pickup-point delivery. */
    static Optional<SupplierPickupPoint> toPickupPoint(Order order) {
        return pickupShipment(order)
                .map(shipment -> new SupplierPickupPoint(shipment.getCarrier(), shipment.getCollectionPointCode(),
                        null, null, null, null));
    }

    static boolean requiresPickupPoint(Order order) {
        return pickupShipment(order).isPresent();
    }

    static SupplierConsignee toConsignee(ShippingDetails details) {
        return new SupplierConsignee(
                StringUtils.trimToNull(details.getCompanyName()),
                StringUtils.trimToNull(details.getName()),
                StringUtils.trimToNull(details.getSurname()),
                details.getStreetAndNumber(),
                details.getPostalCode(),
                details.getCity(),
                StringUtils.upperCase(StringUtils.trimToNull(details.getCountry()), Locale.ROOT),
                details.getPhone(),
                details.getEmail());
    }

    private static String consigneeLabel(ShippingDetails details) {
        return Stream.of(details.getDisplayName(), details.getStreetAndNumber(),
                        StringUtils.trim(StringUtils.joinWith(" ", details.getPostalCode(), details.getCity())))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }
}
