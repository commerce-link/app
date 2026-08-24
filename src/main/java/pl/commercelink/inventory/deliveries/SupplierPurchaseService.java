package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
import pl.commercelink.inventory.supplier.api.SupplierOrderOutcomeUnknownException;
import pl.commercelink.inventory.supplier.api.SupplierOrderResult;
import pl.commercelink.inventory.supplier.api.SupplierProvider;
import pl.commercelink.inventory.supplier.api.SupplierPurchaseRequest;
import pl.commercelink.inventory.supplier.api.SupplierQuote;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierPurchaseService {

    private static final String ORDERED_AUTOMATICALLY_EVENT = "DELIVERY_ORDERED_AUTOMATICALLY";
    private static final String DELIVERY_CREATED_EVENT = "DELIVERY_CREATED";
    private static final String PURCHASE_APPROVED_EVENT = "DELIVERY_PURCHASE_APPROVED";
    private static final String PURCHASE_RETRIED_EVENT = "DELIVERY_PURCHASE_RETRIED";
    private static final String ORDER_RECONCILED_EVENT = "DELIVERY_ORDER_RECONCILED";
    private static final String ORDER_MANUALLY_CONFIRMED_EVENT = "DELIVERY_ORDER_MANUALLY_CONFIRMED";

    private final StoreSupplierProviderResolver storeSupplierProviderResolver;
    private final StoresRepository storesRepository;
    private final DeliveryCreationService deliveryCreationService;
    private final DeliveriesRepository deliveriesRepository;
    private final DeliveryTaxResolver deliveryTaxResolver;
    private final SupplierRegistry supplierRegistry;
    private final SupplierSkuResolver supplierSkuResolver;
    private final SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    private final OrderIdRefreshEventPublisher orderIdRefreshEventPublisher;
    private final ExchangeRates exchangeRates;
    private final SupplierConnectionModeResolver supplierConnectionModeResolver;
    private final DeliveriesQueryService deliveriesQueryService;

    public boolean isOrderingAvailable(String storeId, String provider) {
        try {
            SupplierProvider supplierProvider = getProvider(storeId, provider);
            return supplierProvider != null && supplierProvider.supportsOrdering();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean requiresApproval(String storeId, String provider) {
        Store store = storesRepository.findById(storeId);
        return store != null && store.isGlobalSupplier(provider);
    }

    public List<SupplierDeliveryAddress> deliveryAddresses(String storeId, String provider) {
        SupplierProvider supplierProvider = getProvider(storeId, provider);
        if (supplierProvider == null || !supplierProvider.requiresDeliveryAddress()) {
            return List.of();
        }
        return supplierProvider.deliveryAddresses();
    }

    public void mergeSuggestedItems(DeliveryCreationForm form) {
        if (form.getSuggestedItems() == null) {
            return;
        }
        form.getSuggestedItems().stream()
                .filter(suggested -> suggested.getRequestedQty() > 0)
                .map(SuggestedDeliveryItem::toDeliveryItem)
                .forEach(form.getItems()::add);
        form.getSuggestedItems().clear();
    }

    public PurchaseValidation validate(String storeId, DeliveryCreationForm form) {
        return validate(storeId, form, UUID.randomUUID().toString());
    }

    public PurchaseValidation validate(String storeId, DeliveryCreationForm form, String purchaseRef) {
        List<DeliveryItem> items = form.getItems().stream()
                .filter(item -> item.getRequestedQty() > 0)
                .toList();

        SupplierSkuResolver.StoreSkuLookup skuLookup = supplierSkuResolver.forStore(storeId, form.getProvider());

        List<SupplierOrderLine> lines = items.stream()
                .map(item -> new SupplierOrderLine(skuLookup.skuFor(item.getEan(), item.getMfn()),
                        item.getEan(), item.getMfn(), item.getRequestedQty()))
                .toList();

        SupplierProvider supplierProvider = getProvider(storeId, form.getProvider());
        List<SupplierQuote> quotes = lines.isEmpty() ? List.of() : supplierProvider.checkAvailability(lines);
        Map<String, SupplierQuote> quotesByEan = quotes.stream()
                .collect(Collectors.toMap(SupplierQuote::ean, Function.identity(), (a, b) -> a));

        LiveCostConversion conversion = liveCostConversion(quotes);

        List<PurchaseValidation.Line> validationLines = IntStream.range(0, items.size())
                .mapToObj(i -> toValidationLine(items.get(i), lines.get(i).sku(),
                        quotesByEan.get(items.get(i).getEan()), conversion.sellRate()))
                .toList();

        boolean fullyAvailable = !validationLines.isEmpty()
                && validationLines.stream().allMatch(PurchaseValidation.Line::isAvailable);
        double totalNet = validationLines.stream()
                .mapToDouble(line -> line.requestedQty() * line.liveUnitCost())
                .sum();

        return new PurchaseValidation(form.getProvider(), purchaseRef, conversion.currency(),
                totalNet, fullyAvailable, validationLines);
    }

    private record LiveCostConversion(String currency, double sellRate) {
    }

    private LiveCostConversion liveCostConversion(List<SupplierQuote> quotes) {
        return liveCostConversionFor(quotes.stream()
                .map(SupplierQuote::currency)
                .filter(currency -> currency != null && !currency.isBlank())
                .findFirst()
                .orElse(ExchangeRates.LOCAL_CURRENCY));
    }

    private LiveCostConversion liveCostConversionFor(String currency) {
        String quoteCurrency = currency != null && !currency.isBlank() ? currency : ExchangeRates.LOCAL_CURRENCY;
        if (ExchangeRates.LOCAL_CURRENCY.equals(quoteCurrency)) {
            return new LiveCostConversion(quoteCurrency, 1.0);
        }
        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        Double sellRate = sellRates == null ? null : sellRates.get(quoteCurrency);
        if (sellRate == null) {
            return new LiveCostConversion(quoteCurrency, 1.0);
        }
        return new LiveCostConversion(ExchangeRates.LOCAL_CURRENCY, sellRate);
    }

    public void processPending(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            throw new IllegalStateException("Delivery " + deliveryId + " not found for pending purchase");
        }
        if (!delivery.isOrderPending()) {
            // Covers SQS redelivery after a crash mid-placement: an ORDER_DISPATCHED
            // delivery must never trigger a second placement.
            return;
        }

        DeliveryCreationForm form = rebuildForm(storeId, delivery);
        delivery.setDeliveryAddress(resolveDeliveryAddressLabel(storeId, form));
        PurchaseValidation validation;
        List<SupplierOrderLine> lines;
        try {
            validation = validate(storeId, form, delivery.getPurchaseRef());
            lines = validation.lines().stream()
                    .map(line -> new SupplierOrderLine(line.sku(), line.ean(), line.mfn(), line.requestedQty()))
                    .toList();
            if (lines.isEmpty()) {
                throw new SupplierOrderException("No orderable lines in pending purchase");
            }
        } catch (SupplierOrderException e) {
            failDelivery(delivery, e);
            log.error("Supplier purchase validation failed: store={} delivery={} provider={} ref={}",
                    storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef(), e);
            return;
        }

        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        deliveriesRepository.save(delivery);

        try {
            SupplierOrderResult orderResult = getProvider(storeId, form.getProvider())
                    .placeOrder(new SupplierPurchaseRequest(delivery.getPurchaseRef(), lines,
                            form.getDeliveryAddressId()));
            if (StringUtils.isBlank(orderResult.externalOrderId())) {
                throw new SupplierOrderOutcomeUnknownException(
                        "Supplier confirmed the order without an order number - check the supplier panel before ordering again");
            }
            applyOrderResult(form, validation, orderResult);
            delivery.setExternalDeliveryIdProvisional(orderResult.provisional());
            delivery.addEvent(new Event(EventType.action, ORDERED_AUTOMATICALLY_EVENT, LocalDateTime.now()));
            deliveryCreationService.completePending(storeId, delivery, form);
            log.info("Supplier purchase placed: store={} delivery={} provider={} ref={} externalOrderId={}",
                    storeId, deliveryId, form.getProvider(), delivery.getPurchaseRef(),
                    orderResult.externalOrderId());
            if (orderResult.provisional()) {
                orderIdRefreshEventPublisher.publish(new OrderIdRefreshEventRequest(
                        storeId, delivery.getDeliveryId(), form.getProvider(), delivery.getPurchaseRef()));
            }
        } catch (SupplierOrderOutcomeUnknownException e) {
            delivery.setOrderErrorMessage(e.getMessage());
            deliveriesRepository.save(delivery); // stays ORDER_DISPATCHED
            log.error("Supplier purchase outcome UNKNOWN - order may exist at the supplier: "
                            + "store={} delivery={} provider={} ref={}",
                    storeId, deliveryId, form.getProvider(), delivery.getPurchaseRef(), e);
        } catch (SupplierOrderException e) {
            failDelivery(delivery, e);
            log.error("Supplier purchase failed: store={} delivery={} provider={} ref={}",
                    storeId, deliveryId, form.getProvider(), delivery.getPurchaseRef(), e);
        }
    }

    private void failDelivery(Delivery delivery, SupplierOrderException e) {
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setOrderErrorMessage(e.getMessage());
        deliveriesRepository.save(delivery);
    }

    private String resolveDeliveryAddressLabel(String storeId, DeliveryCreationForm form) {
        if (StringUtils.isBlank(form.getDeliveryAddressId())) {
            return null;
        }
        try {
            return deliveryAddresses(storeId, form.getProvider()).stream()
                    .filter(address -> address.id().equals(form.getDeliveryAddressId()))
                    .map(SupplierDeliveryAddress::label)
                    .findFirst()
                    .orElse(form.getDeliveryAddressId());
        } catch (Exception e) {
            return form.getDeliveryAddressId();
        }
    }

    public List<SupplierDeliveryAddress> deliveryAddressesForDelivery(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            return List.of();
        }
        return deliveryAddresses(storeId, delivery.getProvider());
    }

    public OperationResult<String> approve(String storeId, String deliveryId, String deliveryAddressId) {
        Delivery delivery = findAwaitingApproval(storeId, deliveryId);
        if (delivery == null) {
            return OperationResult.failure("deliveries.approval.error.state");
        }
        if (!requiresApproval(storeId, delivery.getProvider())) {
            return OperationResult.failure("deliveries.approval.error.state");
        }
        if (delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.approval.error.state");
        }

        SupplierProvider supplierProvider = getProvider(storeId, delivery.getProvider());
        if (supplierProvider == null) {
            return OperationResult.failure("deliveries.approval.error.state");
        }
        if (supplierProvider.requiresDeliveryAddress() && StringUtils.isBlank(deliveryAddressId)) {
            return OperationResult.failure("deliveries.purchase.error.address");
        }

        DeliveryCreationForm form = rebuildForm(storeId, delivery);
        form.setDeliveryAddressId(deliveryAddressId);
        delivery.setDeliveryAddressId(deliveryAddressId);

        PurchaseValidation validation;
        try {
            validation = validate(storeId, form, delivery.getPurchaseRef());
        } catch (Exception e) {
            log.error("Supplier availability re-check failed: store={} delivery={}", storeId, deliveryId, e);
            return OperationResult.failure("deliveries.purchase.error.availability");
        }
        if (!validation.fullyAvailable()) {
            return OperationResult.failure("deliveries.purchase.error.availability");
        }

        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.addEvent(new Event(EventType.action, PURCHASE_APPROVED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);

        publishPurchase(delivery);

        return OperationResult.success(delivery.getDeliveryId());
    }

    private void publishPurchase(Delivery delivery) {
        delivery.setPurchaseAttempts(delivery.getPurchaseAttempts() + 1);
        deliveriesRepository.save(delivery);
        supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                delivery.getStoreId(), delivery.getDeliveryId(), delivery.getProvider(),
                delivery.getPurchaseRef(), delivery.getPurchaseAttempts()));
    }

    public OperationResult<String> retry(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isOrderFailed()
                || delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.purchase.retry.error.state");
        }
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setOrderErrorMessage(null);
        delivery.addEvent(new Event(EventType.action, PURCHASE_RETRIED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);
        publishPurchase(delivery);
        return OperationResult.success(delivery.getDeliveryId());
    }

    public OperationResult<String> reconcile(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isOrderDispatched()
                || delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.purchase.reconcile.error.state");
        }
        DeliveryCreationForm form = rebuildForm(storeId, delivery);
        try {
            PurchaseValidation validation = validate(storeId, form, delivery.getPurchaseRef());
            List<SupplierOrderLine> lines = validation.lines().stream()
                    .map(line -> new SupplierOrderLine(line.sku(), line.ean(), line.mfn(), line.requestedQty()))
                    .toList();
            Optional<SupplierOrderResult> placed = getProvider(storeId, delivery.getProvider())
                    .findPlacedOrder(new SupplierPurchaseRequest(delivery.getPurchaseRef(), lines,
                            form.getDeliveryAddressId()));
            if (placed.isEmpty()) {
                log.info("Reconcile found no supplier order: store={} delivery={} provider={} ref={}",
                        storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef());
                return OperationResult.failure("deliveries.purchase.reconcile.notFound");
            }
            SupplierOrderResult orderResult = placed.get();
            applyOrderResult(form, validation, orderResult);
            delivery.setExternalDeliveryIdProvisional(orderResult.provisional());
            delivery.setOrderErrorMessage(null);
            delivery.addEvent(new Event(EventType.action, ORDER_RECONCILED_EVENT, LocalDateTime.now()));
            deliveryCreationService.completePending(storeId, delivery, form);
            if (orderResult.provisional()) {
                try {
                    orderIdRefreshEventPublisher.publish(new OrderIdRefreshEventRequest(
                            storeId, delivery.getDeliveryId(), delivery.getProvider(), delivery.getPurchaseRef()));
                } catch (RuntimeException e) {
                    log.error("Order id refresh publish failed after reconcile: store={} delivery={}",
                            storeId, deliveryId, e);
                }
            }
            log.info("Reconcile confirmed supplier order: store={} delivery={} provider={} ref={} externalOrderId={}",
                    storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef(),
                    orderResult.externalOrderId());
            return OperationResult.success(delivery.getDeliveryId());
        } catch (Exception e) {
            log.error("Reconcile failed: store={} delivery={} provider={} ref={}",
                    storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef(), e);
            return OperationResult.failure("deliveries.purchase.reconcile.error.failed");
        }
    }

    public OperationResult<String> forceRetry(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isOrderDispatched()
                || delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.purchase.retry.error.state");
        }
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setOrderErrorMessage(null);
        delivery.addEvent(new Event(EventType.action, PURCHASE_RETRIED_EVENT, LocalDateTime.now()));
        publishPurchase(delivery);
        log.info("Force retry of unconfirmed purchase: store={} delivery={} provider={} ref={} attempt={}",
                storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef(),
                delivery.getPurchaseAttempts());
        return OperationResult.success(delivery.getDeliveryId());
    }

    public OperationResult<String> confirmManually(String storeId, String deliveryId, String externalOrderId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isOrderDispatched()
                || delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.purchase.manual.error.state");
        }
        if (StringUtils.isBlank(externalOrderId)) {
            return OperationResult.failure("deliveries.purchase.manual.error.blank");
        }
        delivery.setExternalDeliveryId(externalOrderId.trim());
        delivery.setExternalDeliveryIdProvisional(false);
        delivery.setOrderStatus(null);
        delivery.setOrderErrorMessage(null);
        delivery.setTax(deliveryTaxResolver.resolveFor(delivery.getProvider()));
        ShippingTerms terms = supplierRegistry.get(delivery.getProvider()).shippingTermsFor("PL");
        delivery.setEstimatedDeliveryAt(LocalDate.now().plusDays(terms.arrivalDays()));
        delivery.addEvent(new Event(EventType.action, ORDER_MANUALLY_CONFIRMED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);
        log.info("Supplier order id entered manually: store={} delivery={} provider={} ref={} externalOrderId={}",
                storeId, deliveryId, delivery.getProvider(), delivery.getPurchaseRef(), externalOrderId.trim());
        return OperationResult.success(delivery.getDeliveryId());
    }

    public OperationResult<String> reject(String storeId, String deliveryId, String reason) {
        Delivery delivery = findAwaitingApproval(storeId, deliveryId);
        if (delivery == null || delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("deliveries.approval.error.state");
        }
        deliveryCreationService.releaseAllocations(storeId, delivery);
        deliveriesRepository.delete(delivery);
        return OperationResult.success(delivery.getDeliveryId());
    }

    public PurchaseValidation validatePending(String storeId, String deliveryId) {
        Delivery delivery = findAwaitingApproval(storeId, deliveryId);
        if (delivery == null) {
            throw new IllegalStateException("Delivery " + deliveryId + " is not awaiting approval");
        }
        return validate(storeId, rebuildForm(storeId, delivery), delivery.getPurchaseRef());
    }

    private DeliveryCreationForm rebuildForm(String storeId, Delivery delivery) {
        Delivery withAllocations = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, delivery.getDeliveryId());
        withAllocations.getItems().forEach(item -> item.setRequestedQty(item.getOrderedQty()));

        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(delivery.getProvider());
        form.setDeliveryAddressId(delivery.getDeliveryAddressId());
        form.getItems().addAll(withAllocations.getItems());
        return form;
    }

    private Delivery findAwaitingApproval(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null || !delivery.isAwaitingApproval()) {
            return null;
        }
        return delivery;
    }

    public OperationResult<PurchaseSubmission> submitPurchase(String storeId, DeliveryCreationForm form,
                                                              String purchaseRef) {
        boolean hasOrderableItems = form.getItems().stream().anyMatch(item -> item.getRequestedQty() > 0);
        if (!hasOrderableItems) {
            return OperationResult.failure("deliveries.purchase.error.availability");
        }

        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return OperationResult.failure("deliveries.purchase.error.failed");
        }
        boolean requiresApproval = store.isGlobalSupplier(form.getProvider());

        if (!requiresApproval && isDeliveryAddressMissing(storeId, form)) {
            return OperationResult.failure("deliveries.purchase.error.address");
        }

        Optional<Delivery> existing = deliveriesRepository.findByPurchaseRef(storeId, purchaseRef);
        if (existing.isPresent()) {
            return OperationResult.success(
                    new PurchaseSubmission(existing.get().getDeliveryId(), requiresApproval));
        }

        Delivery delivery = new Delivery(storeId, null, form.getProvider());
        delivery.setConnectionMode(supplierConnectionModeResolver.resolve(store, form.getProvider()));
        delivery.setOrderStatus(requiresApproval
                ? DeliveryOrderStatus.AWAITING_APPROVAL
                : DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setDeliveryAddressId(form.getDeliveryAddressId());
        deliveryCreationService.claimAllocations(storeId, delivery, form);
        delivery.addEvent(new Event(EventType.action, DELIVERY_CREATED_EVENT, LocalDateTime.now()));

        deliveriesRepository.save(delivery);

        if (!requiresApproval) {
            publishPurchase(delivery);
        }

        return OperationResult.success(new PurchaseSubmission(delivery.getDeliveryId(), requiresApproval));
    }

    private void applyOrderResult(DeliveryCreationForm form, PurchaseValidation validation,
                                  SupplierOrderResult orderResult) {
        form.setExternalDeliveryId(orderResult.externalOrderId());
        form.setSourceCurrency(ExchangeRates.LOCAL_CURRENCY);
        form.setTax(deliveryTaxResolver.resolveFor(form.getProvider()));

        List<SupplierQuote> confirmedLines = orderResult.confirmedLines() != null
                ? orderResult.confirmedLines()
                : List.of();
        LiveCostConversion conversion = liveCostConversionFor(orderResult.currency());
        Map<String, Double> confirmedPricesByEan = confirmedLines.stream()
                .collect(Collectors.toMap(SupplierQuote::ean,
                        quote -> quote.netPrice() * conversion.sellRate(), (a, b) -> a));
        Map<String, PurchaseValidation.Line> linesByEan = validation.lines().stream()
                .collect(Collectors.toMap(PurchaseValidation.Line::ean, Function.identity(), (a, b) -> a));
        form.getItems().forEach(item -> {
            Double confirmedPrice = confirmedPricesByEan.get(item.getEan());
            if (confirmedPrice != null) {
                item.setUnitCost(confirmedPrice);
            } else {
                PurchaseValidation.Line line = linesByEan.get(item.getEan());
                if (line != null) {
                    item.setUnitCost(line.liveUnitCost());
                }
            }
        });

        SupplierInfo supplierInfo = supplierRegistry.get(form.getProvider());
        ShippingTerms terms = supplierInfo.shippingTermsFor("PL");
        form.setEstimatedDeliveryAt(LocalDate.now().plusDays(terms.arrivalDays()));
        double totalNetForShipping = orderResult.totalNet() > 0
                ? orderResult.totalNet() * conversion.sellRate()
                : validation.totalNet();
        form.setShippingCost(terms.costPolicy().calculate(totalNetForShipping));
    }

    private PurchaseValidation.Line toValidationLine(DeliveryItem item, String sku, SupplierQuote quote,
                                                     double sellRate) {
        int availableQty = quote != null ? quote.availableQuantity() : 0;
        double livePrice = quote != null ? quote.netPrice() * sellRate : 0;
        return new PurchaseValidation.Line(item.getName(), sku, item.getEan(), item.getMfn(),
                item.getRequestedQty(), availableQty, item.getUnitCost(), livePrice);
    }

    private boolean isDeliveryAddressMissing(String storeId, DeliveryCreationForm form) {
        SupplierProvider supplierProvider = getProvider(storeId, form.getProvider());
        return supplierProvider != null && supplierProvider.requiresDeliveryAddress()
                && StringUtils.isBlank(form.getDeliveryAddressId());
    }

    private SupplierProvider getProvider(String storeId, String provider) {
        return storeSupplierProviderResolver.resolve(storeId, provider);
    }
}
