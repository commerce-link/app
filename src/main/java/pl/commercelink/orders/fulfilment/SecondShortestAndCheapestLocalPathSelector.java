package pl.commercelink.orders.fulfilment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SecondShortestAndCheapestLocalPathSelector implements FulfilmentPathSelector {
    @Override
    public Optional<FulfilmentPath> select(List<FulfilmentPath> paths) {
        Long shortestPathSize = paths.stream()
                .filter(FulfilmentPath::hasOnlyLocalProviders)
                .min(Comparator.comparing(FulfilmentPath::size))
                .map(FulfilmentPath::size)
                .orElse(0L);

        return paths.stream()
                .filter(FulfilmentPath::hasOnlyLocalProviders)
                .filter(p -> p.size() <= shortestPathSize + 1)
                .findFirst();
    }
}
