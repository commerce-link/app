package pl.commercelink.orders.fulfilment;

import java.util.List;
import java.util.Optional;

public interface FulfilmentPathSelector {

    Optional<FulfilmentPath> select(List<FulfilmentPath> paths);

    default boolean requiresPathCalculation() {
        return true;
    }
}
