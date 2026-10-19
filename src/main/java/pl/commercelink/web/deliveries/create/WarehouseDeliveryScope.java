package pl.commercelink.web.deliveries.create;

import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.inventory.supplier.api.SupplierOrderOptionsContext;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.RestockSuggestionService;
import pl.commercelink.web.OrderOptionsModel;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.deliveries.DeliveryItem.groupAndUnify;

/** Every allocation waiting at one supplier, from all orders and the warehouse restock; goods come to the warehouse. */
final class WarehouseDeliveryScope implements DeliveryScope {

    private final String storeId;
    private final String provider;
    private final DeliveriesPlanningService planning;
    private final RestockSuggestionService restockSuggestions;
    private final DeliveryTaxResolver taxResolver;
    private final SupplierPurchaseService supplierPurchase;
    private final DeliveryCreationService deliveryCreation;
    private final StoresRepository stores;

    WarehouseDeliveryScope(String storeId, String provider, DeliveriesPlanningService planning,
                           RestockSuggestionService restockSuggestions, DeliveryTaxResolver taxResolver,
                           SupplierPurchaseService supplierPurchase, DeliveryCreationService deliveryCreation,
                           StoresRepository stores) {
        this.storeId = storeId;
        this.provider = provider;
        this.planning = planning;
        this.restockSuggestions = restockSuggestions;
        this.taxResolver = taxResolver;
        this.supplierPurchase = supplierPurchase;
        this.deliveryCreation = deliveryCreation;
        this.stores = stores;
    }

    @Override public String storeId() { return storeId; }
    @Override public String provider() { return provider; }
    @Override public Order order() { return null; }

    @Override
    public DeliveryCreationForm plannedForm() {
        Delivery delivery = planning.run(storeId, provider);
        if (delivery == null) {
            return null;
        }
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(provider);
        form.setItems(groupAndUnify(delivery.getAllocations()));
        form.setTax(defaultTax());
        form.getItems().forEach(item -> item.getAllocations().forEach(allocation -> allocation.setSelected(true)));
        return form;
    }

    @Override
    public List<SuggestedDeliveryItem> suggestions() {
        Delivery delivery = planning.run(storeId, provider);
        Set<String> plannedMfns = delivery == null ? Set.of() : delivery.getAllocations().stream()
                .map(Allocation::getMfn)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return restockSuggestions.suggestForDelivery(storeId, provider, plannedMfns).stream()
                .map(SuggestedDeliveryItem::from)
                .collect(Collectors.toList());
    }

    @Override public double defaultTax() { return taxResolver.resolveFor(provider); }
    @Override public boolean purchaseAvailable() { return supplierPurchase.isOrderingAvailable(storeId, provider); }
    @Override public String purchaseBlockedReason() { return null; }
    @Override public boolean requiresApproval() { return supplierPurchase.requiresApproval(storeId, provider); }
    @Override public boolean requiresOrderIdentity() { return true; }

    @Override
    public void addPurchaseModel(DeliveryCreationForm form, Model model) {
        if (requiresApproval()) {
            model.addAttribute("requiresApproval", true);
            return;
        }
        model.addAttribute("requiresApproval", false);
        try {
            List<SupplierDeliveryAddress> addresses = supplierPurchase.deliveryAddresses(storeId, provider);
            model.addAttribute("deliveryAddresses", addresses);
            if (form.getDeliveryAddressId() == null) {
                if (addresses.size() == 1) {
                    form.setDeliveryAddressId(addresses.getFirst().id());
                } else {
                    Store store = stores.findById(storeId);
                    ShippingDetails storeDefault = store == null ? null : store.getDefaultShippingDetails();
                    SuggestedDeliveryAddress.match(storeDefault, addresses).ifPresent(form::setDeliveryAddressId);
                }
            }
        } catch (Exception e) {
            model.addAttribute("deliveryAddresses", List.of());
            model.addAttribute("deliveryAddressError", e.getMessage());
        }
        OrderOptionsModel.addOrderOptions(supplierPurchase, storeId, provider, SupplierOrderOptionsContext.warehouse(),
                form.getSupplierOrderChoices(), model);
    }

    @Override
    public PurchaseValidation validate(DeliveryCreationForm form) {
        if (!purchaseAvailable()) {
            throw new IllegalStateException(provider);
        }
        return supplierPurchase.validate(storeId, form);
    }

    @Override
    public OperationResult<PurchaseSubmission> submit(DeliveryCreationForm form, String purchaseRef) {
        return supplierPurchase.submitPurchase(storeId, form, purchaseRef);
    }

    @Override
    public OperationResult<String> save(DeliveryCreationForm form) {
        return OperationResult.success(deliveryCreation.run(storeId, form));
    }
}
