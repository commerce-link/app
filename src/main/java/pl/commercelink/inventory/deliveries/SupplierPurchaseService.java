package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
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

@Component
@RequiredArgsConstructor
public class SupplierPurchaseService {

    private final SupplierProviderFactory supplierProviderFactory;
    private final StoresRepository storesRepository;
    private final DeliveryCreationService deliveryCreationService;
    private final DeliveriesRepository deliveriesRepository;
    private final DeliveryTaxResolver deliveryTaxResolver;
    private final SupplierRegistry supplierRegistry;

    public boolean isOrderingAvailable(String storeId, String provider) {
        try {
            SupplierProvider supplierProvider = getProvider(storeId, provider);
            return supplierProvider != null && supplierProvider.supportsOrdering();
        } catch (Exception e) {
            return false;
        }
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

        List<SupplierOrderLine> lines = items.stream()
                .map(item -> new SupplierOrderLine(item.getEan(), item.getMfn(), item.getRequestedQty()))
                .toList();

        SupplierProvider supplierProvider = getProvider(storeId, form.getProvider());
        List<SupplierQuote> quotes = lines.isEmpty() ? List.of() : supplierProvider.checkAvailability(lines);
        Map<String, SupplierQuote> quotesByEan = quotes.stream()
                .collect(Collectors.toMap(SupplierQuote::ean, Function.identity(), (a, b) -> a));

        List<PurchaseValidation.Line> validationLines = items.stream()
                .map(item -> toValidationLine(item, quotesByEan.get(item.getEan())))
                .toList();

        boolean fullyAvailable = validationLines.stream().allMatch(PurchaseValidation.Line::isAvailable);
        double totalNet = validationLines.stream()
                .mapToDouble(line -> line.requestedQty() * line.liveUnitCost())
                .sum();
        String currency = quotes.stream().map(SupplierQuote::currency).findFirst().orElse("PLN");

        return new PurchaseValidation(form.getProvider(), purchaseRef, currency,
                totalNet, fullyAvailable, validationLines);
    }

    public OperationResult<String> purchase(String storeId, DeliveryCreationForm form,
                                            String purchaseRef, boolean isSuperAdmin) {
        PurchaseValidation validation = validate(storeId, form, purchaseRef);
        if (!validation.fullyAvailable()) {
            return OperationResult.failure("deliveries.purchase.error.availability");
        }

        List<SupplierOrderLine> lines = validation.lines().stream()
                .map(line -> new SupplierOrderLine(line.ean(), line.mfn(), line.requestedQty()))
                .toList();

        SupplierOrderResult orderResult;
        try {
            orderResult = getProvider(storeId, form.getProvider())
                    .placeOrder(new SupplierPurchaseRequest(purchaseRef, lines));
        } catch (Exception e) {
            return OperationResult.failure("deliveries.purchase.error.failed");
        }

        Optional<Delivery> existingDelivery = deliveriesRepository.findByExternalDeliveryId(
                storeId, orderResult.externalOrderId());
        if (existingDelivery.isPresent()) {
            return OperationResult.success(existingDelivery.get().getDeliveryId());
        }

        applyOrderResult(form, validation, orderResult);
        String deliveryId = deliveryCreationService.run(storeId, form, isSuperAdmin);
        markAsOrderedAutomatically(storeId, deliveryId);
        return OperationResult.success(deliveryId);
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

    private void markAsOrderedAutomatically(String storeId, String deliveryId) {
        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_ORDERED_AUTOMATICALLY", LocalDateTime.now()));
        deliveriesRepository.save(delivery);
    }

    private PurchaseValidation.Line toValidationLine(DeliveryItem item, SupplierQuote quote) {
        int availableQty = quote != null ? quote.availableQuantity() : 0;
        double livePrice = quote != null ? quote.netPrice() : 0;
        return new PurchaseValidation.Line(item.getName(), item.getEan(), item.getMfn(),
                item.getRequestedQty(), availableQty, item.getUnitCost(), livePrice);
    }

    private SupplierProvider getProvider(String storeId, String provider) {
        Store store = storesRepository.findById(storeId);
        return supplierProviderFactory.get(store, provider);
    }
}
