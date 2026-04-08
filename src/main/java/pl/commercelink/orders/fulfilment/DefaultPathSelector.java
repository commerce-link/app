package pl.commercelink.orders.fulfilment;

import java.util.List;
import java.util.Optional;

public class DefaultPathSelector implements FulfilmentPathSelector {
    @Override
    public Optional<FulfilmentPath> select(List<FulfilmentPath> paths) {
        return Optional.empty();
    }

    @Override
    public boolean requiresPathCalculation() {
        return false;
    }
}
