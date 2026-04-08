package pl.commercelink.orders.fulfilment;

import java.util.List;
import java.util.Optional;

public class ShortestAndCheapestPathSelector implements FulfilmentPathSelector {
    @Override
    public Optional<FulfilmentPath> select(List<FulfilmentPath> paths) {
        long minPathSize = paths.stream().mapToLong(FulfilmentPath::size).min().orElse(0);
        return paths.stream().filter(p -> p.size() == minPathSize).findFirst();
    }
}
