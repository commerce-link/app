package pl.commercelink.orders.fulfilment;

import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.inventory.deliveries.AllocationKey;

import java.util.List;
import java.util.stream.Collectors;

public class FulfilmentGroup {

    private String id;
    private boolean accepted;
    private FulfilmentSource source;
    private List<FulfilmentAllocation> allocations;

    public FulfilmentGroup() {

    }

    public FulfilmentGroup(FulfilmentSource source, List<FulfilmentAllocation> allocations, boolean accepted) {
        this.id = UniqueIdentifierGenerator.generate();
        this.source = source;
        this.allocations = allocations;
        this.accepted = accepted;
    }

    public FulfilmentSource getSource() {
        return source;
    }

    public List<FulfilmentAllocation> getAllocations() {
        return allocations;
    }

    public String getAllocationGroupId() {
        String allocationGroupId = allocations.stream()
                .map(FulfilmentAllocation::getKey)
                .map(AllocationKey::getId)
                .distinct()
                .collect(Collectors.joining("-"));

        int hash = allocationGroupId.hashCode();
        return Integer.toHexString(hash);
    }

    public double getTargetPrice() {
        return allocations.stream()
                .mapToDouble(FulfilmentAllocation::getOrderItemPrice)
                .average()
                .orElse(0.0);
    }

    public int getTargetQty() {
        return allocations.stream()
                .mapToInt(FulfilmentAllocation::getOrderItemQty)
                .sum();
    }

    public double getSourceValue() {
        return source.getPriceNet() * getTargetQty();
    }

    public boolean fulfills(FulfilmentGroup other) {
        return other.allocations.stream()
                .map(FulfilmentAllocation::getKey)
                .allMatch(key -> this.allocations.stream()
                        .map(FulfilmentAllocation::getKey)
                        .anyMatch(k -> k.equals(key)));
    }

    public int getFirstSortNumber() {
        return source.getCategory().ordinal();
    }

    public int getSecondSortNumber() {
        return (int) source.getPriceGross();
    }

    public List<FulfilmentItem> getFulfilmentItems() {
        return allocations.stream()
                .map(a -> new FulfilmentItem(a, source, accepted))
                .collect(Collectors.toList());
    }

    // used by UI

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public void setSource(FulfilmentSource source) {
        this.source = source;
    }

    public void setAllocations(List<FulfilmentAllocation> allocations) {
        this.allocations = allocations;
    }
}