package pl.commercelink.web.deliveries.create;

import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.*;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.OrderOptionsModel;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.List;

/** One order's lines at one supplier, accepted by DropshipEligibility; the supplier ships straight to the customer. */
final class DropshipDeliveryScope implements DeliveryScope {

    private final String storeId;
    private final String provider;
    private final Order order;
    private final List<OrderItem> orderItems;
    private final DeliveryTaxResolver taxResolver;
    private final SupplierPurchaseService supplierPurchase;
    private final DropshipPurchaseService dropshipPurchase;

    DropshipDeliveryScope(String storeId, String provider, Order order, List<OrderItem> orderItems,
                          DeliveryTaxResolver taxResolver, SupplierPurchaseService supplierPurchase,
                          DropshipPurchaseService dropshipPurchase) {
        this.storeId = storeId;
        this.provider = provider;
        this.order = order;
        this.orderItems = orderItems;
        this.taxResolver = taxResolver;
        this.supplierPurchase = supplierPurchase;
        this.dropshipPurchase = dropshipPurchase;
    }

    @Override public String storeId() { return storeId; }
    @Override public String provider() { return provider; }
    @Override public Order order() { return order; }

    @Override
    public DeliveryCreationForm plannedForm() {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setStoreId(storeId);
        form.setProvider(provider);
        form.setTax(defaultTax());
        List<Allocation> allocations = orderItems.stream()
                .filter(OrderItem::isInAllocation)
                .filter(item -> provider.equals(item.getDeliveryId()))
                .map(item -> Allocation.fromOrderItem(order, item))
                .toList();
        form.setItems(DeliveryItem.groupAndUnify(allocations));
        form.getItems().forEach(item -> item.getAllocations().forEach(allocation -> allocation.setSelected(true)));
        return form;
    }

    @Override public double defaultTax() { return taxResolver.resolveFor(provider); }
    @Override public boolean purchaseAvailable() { return true; }
    @Override public String purchaseBlockedReason() { return dropshipPurchase.purchaseBlockedReason(storeId, order, provider); }
    @Override public boolean requiresApproval() { return supplierPurchase.requiresApproval(storeId, provider); }
    @Override public boolean requiresOrderIdentity() { return false; }

    @Override
    public void addPurchaseModel(DeliveryCreationForm form, Model model) {
        boolean requiresApproval = requiresApproval();
        model.addAttribute("requiresApproval", requiresApproval);
        if (!requiresApproval) {
            OrderOptionsModel.addOrderOptions(supplierPurchase, storeId, provider,
                    DropshipPurchaseService.optionsContext(order), form.getSupplierOrderChoices(), model);
        }
    }

    @Override
    public PurchaseValidation validate(DeliveryCreationForm form) {
        if (!dropshipPurchase.isDropshipAvailable(storeId, provider)) {
            throw new IllegalStateException(provider);
        }
        return supplierPurchase.validate(storeId, form);
    }

    @Override
    public OperationResult<PurchaseSubmission> submit(DeliveryCreationForm form, String purchaseRef) {
        return dropshipPurchase.submitDropship(storeId, order, form, purchaseRef);
    }

    @Override
    public OperationResult<String> save(DeliveryCreationForm form) {
        return dropshipPurchase.createManualDropship(storeId, order, form);
    }

    @Override
    public void releaseUnselected(DeliveryCreationForm form) {
        dropshipPurchase.releaseUnselected(storeId, form);
    }
}
