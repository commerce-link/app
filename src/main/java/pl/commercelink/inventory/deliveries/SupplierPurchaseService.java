package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierConnectionModeResolver;
import pl.commercelink.inventory.supplier.SupplierProviderResolver;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderException;
import pl.commercelink.inventory.supplier.api.SupplierOrderLine;
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
public class SupplierPurchaseService {

    private static final String ORDERED_AUTOMATICALLY_EVENT = "DELIVERY_ORDERED_AUTOMATICALLY";
    static final String DELIVERY_CREATED_EVENT = "DELIVERY_CREATED";
    private static final String PURCHASE_APPROVED_EVENT = "DELIVERY_PURCHASE_APPROVED";
    private static final String PURCHASE_RETRIED_EVENT = "DELIVERY_PURCHASE_RETRIED";
    static final int MAX_SQS_ATTEMPTS = 3;

    private final SupplierProviderResolver supplierProviderResolver;
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
    private final DropshipOrderCompletion dropshipOrderCompletion;
    private final DeliveriesQueryService deliveriesQueryService;
    private final DropshipPurchaseService dropshipPurchaseService;
    private final DropshipOrderLocator dropshipOrderLocator;

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

    public void processPending(String storeId, String deliveryId, String orderId, int attempt) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        if (delivery == null) {
            throw new IllegalStateException("Delivery " + deliveryId + " not found for pending purchase");
        }
        if (!delivery.isOrderPending()) {
            return;
        }

        DeliveryCreationForm form = rebuildForm(storeId, delivery);
        if (!delivery.isDropship()) {
            delivery.setDeliveryAddress(resolveDeliveryAddressLabel(storeId, form));
        }

        String dropshipOrderId = null;
        if (delivery.isDropship()) {
            dropshipOrderId = resolveDropshipOrderId(delivery, orderId, attempt);
            if (dropshipOrderId == null) {
                return;
            }
        }

        try {
            PurchaseValidation validation = validate(storeId, form, delivery.getPurchaseRef());
            List<SupplierOrderLine> lines = validation.lines().stream()
                    .map(line -> new SupplierOrderLine(line.sku(), line.ean(), line.mfn(), line.requestedQty()))
                    .toList();
            if (lines.isEmpty()) {
                throw new SupplierOrderException("No orderable lines in pending purchase");
            }

            SupplierOrderResult orderResult = delivery.isDropship()
                    ? dropshipPurchaseService.placeDropshipOrder(storeId, delivery, lines, dropshipOrderId)
                    : getProvider(storeId, form.getProvider())
                            .placeOrder(new SupplierPurchaseRequest(delivery.getPurchaseRef(), lines,
                                    form.getDeliveryAddressId()));
            if (StringUtils.isBlank(orderResult.externalOrderId())) {
                throw new SupplierOrderException(
                        "Supplier confirmed the order without an order number - check the supplier panel before ordering again");
            }

            applyOrderResult(form, validation, orderResult);
            delivery.setExternalDeliveryIdProvisional(orderResult.provisional());
            delivery.addEvent(new Event(EventType.action, ORDERED_AUTOMATICALLY_EVENT, LocalDateTime.now()));
            if (delivery.isDropship()) {
                dropshipOrderCompletion.markSuppliedByDropship(storeId, dropshipOrderId, delivery.getDeliveryId());
                deliveryCreationService.completeDropshipPending(storeId, delivery, form);
            } else {
                deliveryCreationService.completePending(storeId, delivery, form);
            }
            if (orderResult.provisional()) {
                orderIdRefreshEventPublisher.publish(new OrderIdRefreshEventRequest(
                        storeId, delivery.getDeliveryId(), form.getProvider(), delivery.getPurchaseRef()));
            }
        } catch (SupplierOrderException e) {
            failDelivery(delivery, e.getMessage());
        }
    }

    private String resolveDropshipOrderId(Delivery delivery, String payloadOrderId, int attempt) {
        if (StringUtils.isNotBlank(payloadOrderId)) {
            return payloadOrderId;
        }
        Optional<String> located;
        try {
            located = dropshipOrderLocator.locate(delivery.getDeliveryId());
        } catch (IllegalStateException e) {
            failDelivery(delivery, e.getMessage());
            return null;
        }
        if (located.isPresent()) {
            return located.get();
        }
        if (attempt >= MAX_SQS_ATTEMPTS) {
            failDelivery(delivery, "Dropship order could not be resolved for delivery "
                    + delivery.getDeliveryId());
            return null;
        }
        throw new DropshipOrderPendingException(delivery.getDeliveryId(), attempt);
    }

    private void failDelivery(Delivery delivery, String message) {
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setOrderErrorMessage(message);
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
        if (!delivery.isDropship() && supplierProvider.requiresDeliveryAddress()
                && StringUtils.isBlank(deliveryAddressId)) {
            return OperationResult.failure("deliveries.purchase.error.address");
        }

        DeliveryCreationForm form = rebuildForm(storeId, delivery);
        if (!delivery.isDropship()) {
            form.setDeliveryAddressId(deliveryAddressId);
            delivery.setDeliveryAddressId(deliveryAddressId);
        }

        PurchaseValidation validation;
        try {
            validation = validate(storeId, form, delivery.getPurchaseRef());
        } catch (Exception e) {
            System.err.println("[SupplierPurchase] availability re-check failed for store " + storeId
                    + ", delivery " + deliveryId + ": " + e.getMessage());
            e.printStackTrace();
            return OperationResult.failure("deliveries.purchase.error.availability");
        }
        if (!validation.fullyAvailable()) {
            return OperationResult.failure("deliveries.purchase.error.availability");
        }

        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.addEvent(new Event(EventType.action, PURCHASE_APPROVED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);

        supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                storeId, delivery.getDeliveryId(), delivery.getProvider(), delivery.getPurchaseRef()));

        return OperationResult.success(delivery.getDeliveryId());
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
        supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                storeId, delivery.getDeliveryId(), delivery.getProvider(), delivery.getPurchaseRef()));
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
        delivery.setType(DeliveryType.WAREHOUSE);
        delivery.setOrderStatus(requiresApproval
                ? DeliveryOrderStatus.AWAITING_APPROVAL
                : DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        delivery.setDeliveryAddressId(form.getDeliveryAddressId());
        deliveryCreationService.claimAllocations(storeId, delivery, form);
        delivery.addEvent(new Event(EventType.action, DELIVERY_CREATED_EVENT, LocalDateTime.now()));

        deliveriesRepository.save(delivery);

        if (!requiresApproval) {
            supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                    storeId, delivery.getDeliveryId(), form.getProvider(), purchaseRef));
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
        return supplierProviderResolver.resolve(storeId, provider);
    }
}
