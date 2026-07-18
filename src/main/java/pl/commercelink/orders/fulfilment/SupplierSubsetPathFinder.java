package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Exact path finder based on enumerating supplier subsets instead of candidate combinations.
 *
 * Key insight: once the set of suppliers to order from is fixed, the optimal assignment is trivial —
 * every demand (allocation group) simply takes the cheapest candidate offered by that set, and shipping
 * follows mechanically from the per-supplier value sums. So instead of exploring the combinatorial space
 * of candidate-per-demand choices (which explodes and requires pruning), this finder walks ALL subsets of
 * suppliers appearing among the candidates. With at most ~14 suppliers that is at most 2^14 subsets, each
 * evaluated in O(demands x suppliers) against a precomputed cheapest-offer table — milliseconds total,
 * no heuristics, guaranteed optimum per supplier count.
 *
 * Demands coverable from the internal warehouse are assigned to it upfront and excluded from the search,
 * mirroring {@link FulfilmentPathFinder}. Subsets where some supplier wins nothing are skipped as
 * dominated by a smaller subset. The result is the cheapest path for every feasible supplier count,
 * which is exactly what {@link FulfilmentVariant#listFrom} consumes.
 *
 * Known simplification: within a chosen subset each demand takes the supplier's cheapest offer, ignoring
 * the rare case where a pricier offer would push that supplier's sum over a free-shipping threshold with
 * a net gain.
 */
class SupplierSubsetPathFinder {

    private final SupplierRegistry supplierRegistry;

    SupplierSubsetPathFinder(SupplierRegistry supplierRegistry) {
        this.supplierRegistry = supplierRegistry;
    }

    List<FulfilmentPath> resolve(List<FulfilmentGroup> groups) {
        Map<String, List<FulfilmentGroup>> groupsByAllocationGroupId = groups.stream()
                .collect(Collectors.groupingBy(FulfilmentGroup::getAllocationGroupId));

        List<FulfilmentGroup> warehouseGroups = new ArrayList<>();
        List<FulfilmentAllocationGroup> openAllocationGroups = new ArrayList<>();
        groupsByAllocationGroupId.forEach((id, allocationGroups) -> {
            FulfilmentAllocationGroup ag = new FulfilmentAllocationGroup(id, allocationGroups);
            if (ag.canBeFulfilledByWarehouse()) {
                warehouseGroups.add(ag.getWarehouseFulfilmentGroup());
            } else {
                openAllocationGroups.add(ag);
            }
        });

        if (openAllocationGroups.isEmpty()) {
            return warehouseGroups.isEmpty() ? List.of() : List.of(pathOf(warehouseGroups, List.of()));
        }

        List<String> providers = openAllocationGroups.stream()
                .flatMap(ag -> ag.getFulfilmentGroups().stream())
                .map(g -> g.getSource().getProvider())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int providerCount = providers.size();
        int allocationGroupCount = openAllocationGroups.size();

        double[][] values = new double[allocationGroupCount][providerCount];
        FulfilmentGroup[][] candidates = new FulfilmentGroup[allocationGroupCount][providerCount];
        for (double[] row : values) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < allocationGroupCount; i++) {
            for (FulfilmentGroup group : openAllocationGroups.get(i).getFulfilmentGroups()) {
                int p = providers.indexOf(group.getSource().getProvider());
                if (group.getSourceValue() < values[i][p]) {
                    values[i][p] = group.getSourceValue();
                    candidates[i][p] = group;
                }
            }
        }

        Map<Integer, Assignment> bestBySize = new TreeMap<>();
        double[] providerSums = new double[providerCount];
        boolean[] providerUsed = new boolean[providerCount];
        int[] chosenProvider = new int[allocationGroupCount];

        for (long mask = 1; mask < (1L << providerCount); mask++) {
            Arrays.fill(providerSums, 0);
            Arrays.fill(providerUsed, false);
            double itemsTotal = 0;
            boolean feasible = true;

            for (int i = 0; i < allocationGroupCount; i++) {
                double best = Double.POSITIVE_INFINITY;
                int bestProvider = -1;
                for (int p = 0; p < providerCount; p++) {
                    if ((mask & (1L << p)) != 0 && values[i][p] < best) {
                        best = values[i][p];
                        bestProvider = p;
                    }
                }
                if (bestProvider < 0) {
                    feasible = false;
                    break;
                }
                chosenProvider[i] = bestProvider;
                providerSums[bestProvider] += best;
                providerUsed[bestProvider] = true;
                itemsTotal += best;
            }
            if (!feasible) {
                continue;
            }

            double shipping = 0;
            boolean allProvidersUsed = true;
            for (int p = 0; p < providerCount; p++) {
                if ((mask & (1L << p)) == 0) {
                    continue;
                }
                if (!providerUsed[p]) {
                    allProvidersUsed = false;
                    break;
                }
                shipping += supplierRegistry.get(providers.get(p)).shippingTermsFor("PL").costPolicy().calculate(providerSums[p]);
            }
            if (!allProvidersUsed) {
                continue;
            }

            double total = itemsTotal + shipping;
            int size = Long.bitCount(mask);
            Assignment current = bestBySize.get(size);
            if (current == null || total < current.total()) {
                List<FulfilmentGroup> chosen = new ArrayList<>(allocationGroupCount);
                for (int i = 0; i < allocationGroupCount; i++) {
                    chosen.add(candidates[i][chosenProvider[i]]);
                }
                bestBySize.put(size, new Assignment(total, chosen));
            }
        }

        return bestBySize.values().stream()
                .map(a -> pathOf(warehouseGroups, a.groups()))
                .sorted(Comparator.comparingDouble(FulfilmentPath::getEstimatedTotalValue))
                .collect(Collectors.toList());
    }

    private FulfilmentPath pathOf(List<FulfilmentGroup> warehouseGroups, List<FulfilmentGroup> chosen) {
        FulfilmentPath path = new FulfilmentPath(supplierRegistry);
        warehouseGroups.forEach(path::add);
        chosen.forEach(path::add);
        return path;
    }

    private record Assignment(double total, List<FulfilmentGroup> groups) {
    }
}
