package pl.commercelink.orders.fulfilment;

import lombok.Value;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Value
public class FulfilmentVariant {

    List<String> groupIds;
    List<String> providers;
    long supplierCount;
    double estimatedTotal;
    boolean cheapest;
    boolean fewestSuppliers;

    static List<FulfilmentVariant> listFrom(List<FulfilmentPath> paths) {
        if (paths.isEmpty()) {
            return List.of();
        }

        Map<Long, FulfilmentPath> bestBySize = new TreeMap<>();
        for (FulfilmentPath path : paths) {
            bestBySize.merge(path.size(), path, (a, b) -> a.getEstimatedTotalValue() <= b.getEstimatedTotalValue() ? a : b);
        }
        long minSize = bestBySize.keySet().iterator().next();
        FulfilmentPath cheapestPath = paths.stream().min(Comparator.comparingDouble(FulfilmentPath::getEstimatedTotalValue)).get();

        Map<List<String>, FulfilmentPath> selected = new LinkedHashMap<>();
        bestBySize.forEach((size, path) -> {
            if (size <= minSize + 2) {
                selected.put(path.getGroupIds(), path);
            }
        });
        selected.put(cheapestPath.getGroupIds(), cheapestPath);

        double cheapestTotal = cheapestPath.getEstimatedTotalValue();
        return selected.values().stream()
                .sorted(Comparator.comparingLong(FulfilmentPath::size))
                .map(p -> new FulfilmentVariant(
                        p.getGroupIds(),
                        p.getProviders(),
                        p.size(),
                        p.getEstimatedTotalValue(),
                        p.getEstimatedTotalValue() == cheapestTotal,
                        p.size() == minSize))
                .toList();
    }

    void applyTo(List<FulfilmentGroup> groups) {
        groups.forEach(g -> g.setAccepted(groupIds.contains(g.getId())));
    }
}
