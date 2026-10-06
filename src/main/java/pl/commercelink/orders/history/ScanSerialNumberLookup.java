package pl.commercelink.orders.history;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.SerialNumbers;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemsRepository;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
class ScanSerialNumberLookup implements SerialNumberLookup {

    private final OrderItemsRepository orderItemsRepository;
    private final RMAItemsRepository rmaItemsRepository;
    private final Warehouse warehouse;

    @Override
    public SerialNumberMatches find(String storeId, String serialNo) {
        String wanted = serialNo.trim();
        return new SerialNumberMatches(
                exact(orderItemsRepository.findBySerialNo(wanted), OrderItem::getSerialNo, wanted),
                exact(rmaItemsRepository.findBySerialNo(wanted), RMAItem::getSerialNo, wanted),
                warehouse.stockQueryService(storeId).findAllBySerialNo(storeId, wanted));
    }

    // The scans filter with contains(), which also matches a fragment of a longer number.
    private static <T> List<T> exact(List<T> candidates, Function<T, String> serials, String wanted) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream().filter(c -> SerialNumbers.contains(serials.apply(c), wanted)).toList();
    }
}
