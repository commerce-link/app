package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class FulfilmentGroupsGenerator {

    private final InventoryView inventory;
    private final List<String> acceptedSuppliers;

    private final boolean enforceFulfilmentUnderCost;
    private final boolean enforceCompleteFulfilment;

    private FulfilmentGroupsGenerator(InventoryView inventory, List<String> acceptedSuppliers, boolean enforceFulfilmentUnderCost, boolean enforceCompleteFulfilment) {
        this.inventory = inventory;
        this.acceptedSuppliers = acceptedSuppliers;
        this.enforceFulfilmentUnderCost = enforceFulfilmentUnderCost;
        this.enforceCompleteFulfilment = enforceCompleteFulfilment;
    }

    /*
        Group candidates by source while aggregating allocations
     */
    public List<FulfilmentGroup> runWithGrouping(List<OrderItem> orderItems) {
        List<FulfilmentItem> fulfilmentItems = run(orderItems);

        return fulfilmentItems.stream()
                .collect(Collectors.groupingBy(FulfilmentItem::getSource))
                .entrySet().stream()
                .map(item -> new FulfilmentGroup(
                        item.getKey(),
                        item.getValue().stream()
                                .map(FulfilmentItem::getAllocation)
                                .collect(Collectors.toList()),
                        item.getValue().stream().allMatch(FulfilmentItem::isAccepted)
                ))
                .filter(g -> !enforceFulfilmentUnderCost || g.getTargetPrice() >= g.getSource().getPriceGross())
                .filter(g -> !enforceCompleteFulfilment || g.getSource().getQty() >= g.getTargetQty())
                .sorted(
                        Comparator.comparing(FulfilmentGroup::getFirstSortNumber).thenComparing(FulfilmentGroup::getSecondSortNumber)
                )
                .collect(Collectors.toList());
    }

    /**
     * Generates a list of fulfilment candidates for the provided order items while filtering items that are not qualified for fulfilment.
     */
     public List<FulfilmentItem> run(List<OrderItem> orderItems) {
        return orderItems.stream()
                .filter(i -> !i.isInAllocation())
                .filter(i -> !i.isAllocated())
                .map(this::createFulfilments)
                .flatMap(List::stream)
                .filter(FulfilmentItem::hasProvider)
                .filter(c -> acceptedSuppliers.isEmpty() || acceptedSuppliers.contains(c.getSource().getProvider()))
                .filter(c -> ProductCategory.Services == c.getSource().getCategory() || isNotBlank(c.getSource().getEan()))
                .filter(c -> ProductCategory.Services == c.getSource().getCategory() || isNotBlank(c.getSource().getMfn()))
                .filter(c -> !enforceFulfilmentUnderCost || c.getSource().getPriceGross() > 0)
                .filter(c -> !enforceFulfilmentUnderCost || c.getAllocation().getOrderItemPrice() >= c.getSource().getPriceGross())
                .filter(c -> !enforceCompleteFulfilment || c.getSource().getQty() >= c.getAllocation().getOrderItemQty())
                .sorted(
                        Comparator.comparing(FulfilmentItem::getFirstSortNumber).thenComparing(FulfilmentItem::getSecondSortNumber)
                )
                .collect(Collectors.toList());
    }

    private List<FulfilmentItem> createFulfilments(OrderItem orderItem) {
        if (orderItem.hasCategory(ProductCategory.Services)) {
            return Collections.singletonList(FulfilmentItem.internal(orderItem));
        }

        if (orderItem.hasSKU()) {
            // this is a hack to support multiple manufacturer codes for single product used in PreBuilds definition
            return Arrays.stream(orderItem.getSku().split(":"))
                    .map(String::trim)
                    .map(c -> createFulfilmentBasedOnSku(orderItem, c))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

        } else {
            return Collections.singletonList(FulfilmentItem.manual(orderItem));
        }
    }

    private List<FulfilmentItem> createFulfilmentBasedOnSku(OrderItem orderItem, String mfn) {
        Collection<InventoryItem> offers = inventory.findByProductCode(mfn).getInventoryItems();
        return FulfilmentItem.fromInventory(orderItem, offers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InventoryView inventory;
        private List<String> acceptedSuppliers = new ArrayList<>();
        private boolean enforceFulfilmentUnderCost;
        private boolean enforceCompleteFulfilment;

        public Builder withInventory(InventoryView inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder withAcceptedSupplier(String supplier) {
            this.acceptedSuppliers.add(supplier);
            return this;
        }

        public Builder withAcceptedSuppliers(List<String> acceptedSuppliers) {
            this.acceptedSuppliers = acceptedSuppliers;
            return this;
        }

        public Builder withFulfilmentUnderCost() {
            this.enforceFulfilmentUnderCost = true;
            return this;
        }

        public Builder withCompleteFulfilmentOnly() {
            this.enforceCompleteFulfilment = true;
            return this;
        }

        public FulfilmentGroupsGenerator build() {
            return new FulfilmentGroupsGenerator(this.inventory, this.acceptedSuppliers, this.enforceFulfilmentUnderCost, this.enforceCompleteFulfilment);
        }
    }

}
