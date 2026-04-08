package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FulfilmentPath {

    private final SupplierRegistry supplierRegistry;
    private List<FulfilmentGroup> selectedGroups = new LinkedList<>();

    FulfilmentPath(SupplierRegistry supplierRegistry) {
        this.supplierRegistry = supplierRegistry;
    }

    void add(FulfilmentGroup fulfilmentGroup) {
        this.selectedGroups.add(fulfilmentGroup);
    }

    boolean alreadyFulfills(FulfilmentGroup other) {
        return selectedGroups.stream().anyMatch(fg -> fg.fulfills(other));
    }

    long size() {
        return selectedGroups.stream().map(sg -> sg.getSource().getProvider()).distinct().count();
    }

    boolean hasOnlyLocalProviders() {
        return selectedGroups.stream().allMatch(sg -> supplierRegistry.get(sg.getSource().getProvider()).isLocalFor("PL"));
    }

    double getEstimatedTotalValue() {
        Map<String, List<FulfilmentGroup>> groupsByProvider = selectedGroups.stream()
            .collect(Collectors.groupingBy(sg -> sg.getSource().getProvider()));

        double estimatedShippingCost = 0;
        for (String provider : groupsByProvider.keySet()) {
            double providerSourceValue = groupsByProvider.get(provider).stream().mapToDouble(FulfilmentGroup::getSourceValue).sum();
            estimatedShippingCost += supplierRegistry.get(provider).shippingTermsFor("PL").costPolicy().calculate(providerSourceValue);
        }

        return selectedGroups.stream().mapToDouble(FulfilmentGroup::getSourceValue).sum() + estimatedShippingCost;
    }

    void accept(List<FulfilmentGroup> fulfilmentGroups) {
        fulfilmentGroups.forEach(g -> g.setAccepted(false));

        for (FulfilmentGroup selectedGroup : selectedGroups) {
            FulfilmentGroup fulfilmentGroup = fulfilmentGroups.stream().filter(fg -> fg.getId().equalsIgnoreCase(selectedGroup.getId())).findFirst().get();
            fulfilmentGroup.setAccepted(true);
        }
    }

    FulfilmentPath copy() {
        FulfilmentPath copy = new FulfilmentPath(supplierRegistry);
        copy.selectedGroups = new LinkedList<>(this.selectedGroups);
        return copy;
    }
}
