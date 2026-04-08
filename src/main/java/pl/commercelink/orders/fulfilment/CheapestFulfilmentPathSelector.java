package pl.commercelink.orders.fulfilment;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CheapestFulfilmentPathSelector implements FulfilmentPathSelector {
    @Override
    public Optional<FulfilmentPath> select(List<FulfilmentPath> paths) {
        return paths.stream().min(Comparator.comparingDouble(FulfilmentPath::getEstimatedTotalValue));
    }
}
