package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.util.*;
import java.util.stream.Collectors;

class FulfilmentPathFinder {

    private final SupplierRegistry supplierRegistry;

    FulfilmentPathFinder(SupplierRegistry supplierRegistry) {
        this.supplierRegistry = supplierRegistry;
    }

    List<FulfilmentPath> resolve(List<FulfilmentGroup> groups) {
        // group fulfilment groups by their unique allocation group id (combination of all allocation keys they cover)
        Map<String, List<FulfilmentGroup>> fulfilmentGroupsByAllocationGroupId = groups.stream()
                .collect(Collectors.groupingBy(FulfilmentGroup::getAllocationGroupId));

        // sort allocation groups by their target value descending
        List<FulfilmentAllocationGroup> sortedFulfilmentAllocationGroups = fulfilmentGroupsByAllocationGroupId.entrySet().stream()
                .map(entry -> new FulfilmentAllocationGroup(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Double.compare(b.getAllocationGroupValue(), a.getAllocationGroupValue()))
                .collect(Collectors.toList());

        return generatePathCombinations(sortedFulfilmentAllocationGroups);
    }

    private List<FulfilmentPath> generatePathCombinations(List<FulfilmentAllocationGroup> sortedFulfilmentAllocationGroups) {

        int allocationGroupIdx = 0;
        int fulfilmentGroupIdx = 0;
        double fulfilmentGroupSourceValueLimit = 0;

        Set<FulfilmentPath> previousPaths = new HashSet<>();
        Set<FulfilmentPath> currentPaths = new HashSet<>();

        while (sortedFulfilmentAllocationGroups.size() - allocationGroupIdx > 0) {

            // reduce the number of paths if we are deep in the allocation groups and have too many paths already
            if (allocationGroupIdx > 5 && fulfilmentGroupIdx == 0 && previousPaths.size() > 10000) {

                long minPathSize = previousPaths.stream().mapToLong(FulfilmentPath::size).min().orElse(0);
                long maxPathSize = previousPaths.stream().mapToLong(FulfilmentPath::size).max().orElse(0);

                // best by localization
                Set<FulfilmentPath> bestByLocalization = previousPaths.stream()
                        .filter(FulfilmentPath::hasOnlyLocalProviders)
                        .sorted(Comparator.comparing(FulfilmentPath::getEstimatedTotalValue))
                        .limit(500)
                        .collect(Collectors.toSet());

                // best by path size
                Set<FulfilmentPath> bestByPathSize = new HashSet<>();
                for (long i = minPathSize; i < maxPathSize; i++) {
                    long finalI = i;
                    Set<FulfilmentPath> bestBySize = previousPaths.stream()
                            .filter(p -> p.size() == finalI)
                            .sorted(Comparator.comparing(FulfilmentPath::getEstimatedTotalValue))
                            .limit(100)
                            .collect(Collectors.toSet());
                    bestByPathSize.addAll(bestBySize);
                }

                // best overall
                Set<FulfilmentPath> bestOverall = previousPaths.stream()
                        .sorted(Comparator.comparing(FulfilmentPath::getEstimatedTotalValue))
                        .limit(1000)
                        .collect(Collectors.toSet());

                previousPaths = new HashSet<>();
                previousPaths.addAll(bestByLocalization);
                previousPaths.addAll(bestByPathSize);
                previousPaths.addAll(bestOverall);
            }

            FulfilmentAllocationGroup ag = sortedFulfilmentAllocationGroups.get(allocationGroupIdx);

            // replace a fulfilment group with warehouse fg if possible
            FulfilmentGroup fg;
            if (ag.canBeFulfilledByWarehouse()) {
                fg = ag.getWarehouseFulfilmentGroup();
            } else {
                fg = ag.getFulfilmentGroups().size() > fulfilmentGroupIdx
                        ? ag.getFulfilmentGroups().get(fulfilmentGroupIdx)
                        : null;
            }

            if (fulfilmentGroupIdx == 0) {
                int targetQty = fg != null ? fg.getTargetQty() : 0;
                double sourceValue = fg != null ? fg.getSourceValue() : 0;
                double fulfilmentGroupSpreadMultiplier;
                if (sourceValue < 250 * targetQty) {
                    fulfilmentGroupSpreadMultiplier = 0.15;
                } else if (sourceValue < 500 * targetQty) {
                    fulfilmentGroupSpreadMultiplier = 0.10;
                } else if (sourceValue < 1000 * targetQty) {
                    fulfilmentGroupSpreadMultiplier = 0.075;
                } else {
                    fulfilmentGroupSpreadMultiplier = 0.05;
                }

                fulfilmentGroupSourceValueLimit = sourceValue + sourceValue * fulfilmentGroupSpreadMultiplier;
            }

            // no more fgs or we exceeded the price limit for that allocation group assuming min no of fgs per allocation group
            if (fg == null || (fulfilmentGroupIdx > 2 && fg.getSourceValue() > fulfilmentGroupSourceValueLimit)) {
                allocationGroupIdx++;
                fulfilmentGroupIdx = 0;
                fulfilmentGroupSourceValueLimit = 0;

                previousPaths = currentPaths;
                currentPaths = new HashSet<>(); // probably no reset here

                continue;
            }

            // skip groups that are already fulfilled in previous paths
            if (!previousPaths.isEmpty() && previousPaths.stream().allMatch(p -> p.alreadyFulfills(fg))) {
                allocationGroupIdx++;
                fulfilmentGroupIdx = 0;
                fulfilmentGroupSourceValueLimit = 0;

                continue;
            }

            if (previousPaths.isEmpty()) {
                FulfilmentPath fp = new FulfilmentPath(supplierRegistry);
                fp.add(fg);
                currentPaths.add(fp);
            } else {
                for (FulfilmentPath previousPath : previousPaths) {
                    FulfilmentPath fp = previousPath.copy();
                    fp.add(fg);
                    currentPaths.add(fp);
                }
            }

            if (SupplierRegistry.WAREHOUSE.equalsIgnoreCase(fg.getSource().getProvider())) {
                // if we used warehouse fg, move to next allocation group
                allocationGroupIdx++;
                fulfilmentGroupIdx = 0;
                fulfilmentGroupSourceValueLimit = 0;

                previousPaths = currentPaths;
                currentPaths = new HashSet<>();
            } else {
                fulfilmentGroupIdx++;
            }
        }

        return previousPaths.stream().sorted(Comparator.comparing(FulfilmentPath::getEstimatedTotalValue)).collect(Collectors.toList());
    }


}
