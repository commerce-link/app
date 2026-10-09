package pl.commercelink.orders.fulfilment;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.*;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ManualOrderFulfilment extends OrderFulfilment {

    private final Inventory inventory;
    private final SupplierRegistry supplierRegistry;
    private final StoresRepository storesRepository;

    public ManualOrderFulfilment(Inventory inventory, OrdersRepository ordersRepository, OrderLifecycle orderLifecycle, OrderItemsRepository orderItemsRepository, WarehouseFulfilmentService warehouseFulfilmentService, SupplierRegistry supplierRegistry, StoresRepository storesRepository) {
        super(ordersRepository, orderItemsRepository, orderLifecycle, warehouseFulfilmentService);
        this.inventory = inventory;
        this.supplierRegistry = supplierRegistry;
        this.storesRepository = storesRepository;
    }

    public FulfilmentForm init(String storeId, List<String> selectedOrders, String pathSelector, boolean onlyWithProfit, boolean onlyMultiOrder, boolean onlyLocalSuppliers) {
        String redirectUrl = "redirect:/dashboard/fulfilment/queue";

        List<OrderItem> orderItems = selectedOrders.stream()
                .flatMap(orderId -> orderItemsRepository
                        .findByOrderIdAndStatus(orderId, FulfilmentStatus.New).stream()
                )
                .collect(Collectors.toList());

        if (orderItems.isEmpty()) {
            return new FulfilmentForm("orders", redirectUrl, new LinkedList<>());
        }

        FulfilmentGroupsGenerator.Builder builder = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory.withEnabledSuppliersAndWarehouseData(storeId, SupplierScope.FULFILMENT))
                .withCandidateFilter(ExternalSupplierBinding.of(storesRepository.findById(storeId), ordersOf(storeId, selectedOrders)));
        if (onlyWithProfit) {
            builder.withFulfilmentUnderCost();
        }
        if (onlyMultiOrder) {
            builder.withMultiOrderFulfilmentOnly();
        }
        if (onlyLocalSuppliers) {
            builder.withSupplierFilter(supplier -> supplierRegistry.get(supplier).isLocalFor("PL"));
        }
        if (orderItems.stream().map(OrderItem::getOrderId).filter(StringUtils::isNotBlank).distinct().count() > 1) {
            // in the case of multiple orders show only options that can satisfy demand
            builder.withCompleteFulfilmentOnly();
        }
        List<FulfilmentGroup> entries = builder.build().runWithGrouping(orderItems);

        FulfilmentForm form = new FulfilmentForm("orders", redirectUrl, selectedOrders, entries);
        form.setUnmatched(unmatched(orderItems, entries, onlyWithProfit || onlyMultiOrder || onlyLocalSuppliers));
        List<FulfilmentPath> paths = resolvePaths(pathSelector, entries);
        if (paths != null) {
            List<FulfilmentVariant> variants = FulfilmentVariant.listFrom(paths);
            variants.stream().filter(FulfilmentVariant::isCheapest).findFirst().ifPresent(v -> v.applyTo(entries));
            form.setVariants(variants);
        }
        return form;
    }

    private static List<UnmatchedItem> unmatched(List<OrderItem> orderItems, List<FulfilmentGroup> entries, boolean narrowed) {
        Set<String> covered = entries.stream()
                .flatMap(group -> group.getAllocations().stream())
                .map(allocation -> allocation.getKey().getId())
                .collect(Collectors.toSet());
        UnmatchedItem.Reason reason = narrowed ? UnmatchedItem.Reason.NARROWED : UnmatchedItem.Reason.NO_OFFER;
        return orderItems.stream()
                .filter(item -> !covered.contains(new AllocationKey(item.getOrderId(), item.getItemId(), null).getId()))
                .map(item -> new UnmatchedItem(item.getOrderId(), item.getItemId(), item.getName(), item.getQty(), item.getPrice(), reason))
                .toList();
    }

    private List<Order> ordersOf(String storeId, List<String> orderIds) {
        return orderIds.stream()
                .distinct()
                .map(orderId -> ordersRepository.findById(storeId, orderId))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<FulfilmentPath> resolvePaths(String pathSelector, List<FulfilmentGroup> entries) {
        if ("suggest".equals(pathSelector)) {
            return new FulfilmentPathFinder(supplierRegistry).resolve(entries);
        }
        if ("suggest-exact".equals(pathSelector)) {
            return new SupplierSubsetPathFinder(supplierRegistry).resolve(entries);
        }
        return null;
    }

    public FulfilmentCommit commit(String storeId, FulfilmentForm form) {
        Map<String, List<FulfilmentItem>> entriesByOrderId = form.getAcceptedFulfilmentItemsGroupedByOrderId();
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storesRepository.findById(storeId), ordersOf(storeId, new ArrayList<>(entriesByOrderId.keySet())));

        List<OrderItem> saved = new ArrayList<>();
        for (String orderId : entriesByOrderId.keySet()) {
            List<FulfilmentItem> permitted = entriesByOrderId.get(orderId).stream().filter(binding).toList();
            if (permitted.isEmpty()) {
                continue;
            }
            List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId)
                    .stream()
                    .map(i -> accept(i, permitted))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            saved.addAll(super.commit(storeId, orderItems));
        }
        return FulfilmentCommit.of(saved);
    }
}
