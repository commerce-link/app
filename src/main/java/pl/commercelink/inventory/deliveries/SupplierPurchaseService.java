package pl.commercelink.inventory.deliveries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.SupplierSkuResolver;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
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
import pl.commercelink.inventory.supplier.api.SupplierShippingAddress;
import pl.commercelink.orders.ShippingDetails;
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
    private static final String DELIVERY_CREATED_EVENT = "DELIVERY_CREATED";

    private final SupplierProviderFactory supplierProviderFactory;
    private final StoresRepository storesRepository;
    private final DeliveryCreationService deliveryCreationService;
    private final DeliveriesRepository deliveriesRepository;
    private final DeliveryTaxResolver deliveryTaxResolver;
    private final SupplierRegistry supplierRegistry;
    private final SupplierSkuResolver supplierSkuResolver;
    private final SupplierPurchaseEventPublisher supplierPurchaseEventPublisher;
    private final ObjectMapper objectMapper;
    private final ExchangeRates exchangeRates;

    public boolean isOrderingAvailable(String storeId, String provider) {
        try {
            Store store = storesRepository.findById(storeId);
            if (store == null || !store.isOwnSupplier(provider)) {
                return false;
            }
            SupplierProvider supplierProvider = supplierProviderFactory.get(store, provider);
            return supplierProvider != null && supplierProvider.supportsOrdering();
        } catch (Exception e) {
            return false;
        }
    }

    public record DeliveryAddressChoices(boolean required, List<SupplierDeliveryAddress> options) {
    }

    public DeliveryAddressChoices deliveryAddressChoices(String storeId, String provider) {
        SupplierProvider supplierProvider = getProvider(storeId, provider);
        if (supplierProvider == null) {
            return new DeliveryAddressChoices(false, List.of());
        }
        if (supplierProvider.requiresDeliveryAddress()) {
            return new DeliveryAddressChoices(true, supplierProvider.deliveryAddresses());
        }
        if (supplierProvider.acceptsShippingAddress()) {
            return new DeliveryAddressChoices(false, storeAddressOptions(storeId));
        }
        return new DeliveryAddressChoices(false, List.of());
    }

    private List<SupplierDeliveryAddress> storeAddressOptions(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null || store.getShippingDetails() == null) {
            return List.of();
        }
        return store.getShippingDetails().stream()
                .filter(details -> StringUtils.isNotBlank(details.getId()))
                .filter(ShippingDetails::isProperlyFilled)
                .map(details -> new SupplierDeliveryAddress(details.getId(),
                        details.getDisplayName() + ", " + details.getStreetAndNumber(),
                        details.getCity(), details.getPostalCode(), details.getCountry()))
                .toList();
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
        String quoteCurrency = quotes.stream()
                .map(SupplierQuote::currency)
                .filter(currency -> currency != null && !currency.isBlank())
                .findFirst()
                .orElse(ExchangeRates.LOCAL_CURRENCY);
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
            return;
        }

        DeliveryCreationForm form = readPendingForm(delivery);
        try {
            PurchaseValidation validation = validate(storeId, form, delivery.getPurchaseRef());
            List<SupplierOrderLine> lines = validation.lines().stream()
                    .map(line -> new SupplierOrderLine(line.sku(), line.ean(), line.mfn(), line.requestedQty()))
                    .toList();
            if (lines.isEmpty()) {
                throw new SupplierOrderException("No orderable lines in pending purchase");
            }

            SupplierProvider supplierProvider = getProvider(storeId, form.getProvider());
            SupplierOrderResult orderResult = supplierProvider.placeOrder(
                    purchaseRequest(storeId, supplierProvider, delivery.getPurchaseRef(), lines, form));
            if (StringUtils.isBlank(orderResult.externalOrderId())) {
                throw new SupplierOrderException(
                        "Supplier confirmed the order without an order number - check the supplier panel before ordering again");
            }

            applyOrderResult(form, validation, orderResult);
            delivery.addEvent(new Event(EventType.action, ORDERED_AUTOMATICALLY_EVENT, LocalDateTime.now()));
            deliveryCreationService.completePending(storeId, delivery, form);
        } catch (SupplierOrderException e) {
            delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
            delivery.setOrderErrorMessage(e.getMessage());
            deliveriesRepository.save(delivery);
        }
    }

    private DeliveryCreationForm readPendingForm(Delivery delivery) {
        try {
            return objectMapper.readValue(delivery.getPendingOrderForm(), DeliveryCreationForm.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable pending order form on delivery " + delivery.getDeliveryId(), e);
        }
    }

    private SupplierPurchaseRequest purchaseRequest(String storeId, SupplierProvider provider,
                                                    String purchaseRef, List<SupplierOrderLine> lines,
                                                    DeliveryCreationForm form) {
        if (provider.acceptsShippingAddress() && StringUtils.isNotBlank(form.getDeliveryAddressId())) {
            ShippingDetails details = findStoreAddress(storeId, form.getDeliveryAddressId())
                    .orElseThrow(() -> new SupplierOrderException(
                            "Chosen delivery address no longer exists in the store address book"));
            try {
                return new SupplierPurchaseRequest(purchaseRef, lines, null, toShippingAddress(details));
            } catch (IllegalArgumentException e) {
                throw new SupplierOrderException(
                        "Chosen delivery address is not usable for supplier ordering: " + e.getMessage(), e);
            }
        }
        return new SupplierPurchaseRequest(purchaseRef, lines, form.getDeliveryAddressId());
    }

    private Optional<ShippingDetails> findStoreAddress(String storeId, String addressId) {
        Store store = storesRepository.findById(storeId);
        if (store == null || store.getShippingDetails() == null) {
            return Optional.empty();
        }
        return store.getShippingDetails().stream()
                .filter(details -> addressId.equals(details.getId()))
                .findFirst();
    }

    private static SupplierShippingAddress toShippingAddress(ShippingDetails details) {
        return new SupplierShippingAddress(details.getCompanyName(), details.getName(), details.getSurname(),
                details.getStreetAndNumber(), details.getPostalCode(), details.getCity(),
                details.getCountry(), details.getPhone(), details.getEmail());
    }

    public OperationResult<String> enqueuePurchase(String storeId, DeliveryCreationForm form,
                                                   String purchaseRef, boolean isSuperAdmin) {
        boolean hasOrderableItems = form.getItems().stream().anyMatch(item -> item.getRequestedQty() > 0);
        if (!hasOrderableItems) {
            return OperationResult.failure("deliveries.purchase.error.availability");
        }
        if (isDeliveryAddressMissing(storeId, form)) {
            return OperationResult.failure("deliveries.purchase.error.address");
        }
        if (isChosenStoreAddressUnusable(storeId, form)) {
            return OperationResult.failure("deliveries.purchase.error.address");
        }

        Optional<Delivery> existing = deliveriesRepository.findByPurchaseRef(storeId, purchaseRef);
        if (existing.isPresent()) {
            return OperationResult.success(existing.get().getDeliveryId());
        }

        Delivery delivery = new Delivery(storeId, null, form.getProvider());
        delivery.setManaged(isSuperAdmin);
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        delivery.setPurchaseRef(purchaseRef);
        try {
            delivery.setPendingOrderForm(objectMapper.writeValueAsString(form));
        } catch (JsonProcessingException e) {
            System.err.println("[SupplierPurchase] failed to serialize pending order form for store " + storeId
                    + ", ref " + purchaseRef + ": " + e.getMessage());
            e.printStackTrace();
            return OperationResult.failure("deliveries.purchase.error.failed");
        }
        delivery.addEvent(new Event(EventType.action, DELIVERY_CREATED_EVENT, LocalDateTime.now()));

        deliveriesRepository.save(delivery);
        supplierPurchaseEventPublisher.publish(new SupplierPurchaseEventRequest(
                storeId, delivery.getDeliveryId(), form.getProvider(), purchaseRef));

        return OperationResult.success(delivery.getDeliveryId());
    }

    private void applyOrderResult(DeliveryCreationForm form, PurchaseValidation validation,
                                  SupplierOrderResult orderResult) {
        form.setExternalDeliveryId(orderResult.externalOrderId());
        form.setSourceCurrency(orderResult.currency());
        form.setTax(deliveryTaxResolver.resolveFor(form.getProvider()));

        Map<String, Double> confirmedPricesByEan = orderResult.confirmedLines() != null
                ? orderResult.confirmedLines().stream()
                        .collect(Collectors.toMap(SupplierQuote::ean, SupplierQuote::netPrice, (a, b) -> a))
                : Map.of();
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
        double totalNetForShipping = orderResult.totalNet() > 0 ? orderResult.totalNet() : validation.totalNet();
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

    private boolean isChosenStoreAddressUnusable(String storeId, DeliveryCreationForm form) {
        SupplierProvider supplierProvider = getProvider(storeId, form.getProvider());
        if (supplierProvider == null || !supplierProvider.acceptsShippingAddress()
                || StringUtils.isBlank(form.getDeliveryAddressId())) {
            return false;
        }
        Optional<ShippingDetails> details = findStoreAddress(storeId, form.getDeliveryAddressId());
        if (details.isEmpty() || !details.get().isProperlyFilled()) {
            return true;
        }
        try {
            toShippingAddress(details.get());
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private SupplierProvider getProvider(String storeId, String provider) {
        Store store = storesRepository.findById(storeId);
        return supplierProviderFactory.get(store, provider);
    }
}
