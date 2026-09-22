package pl.commercelink.orders;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.ClientPreferredShippingDateException.Reason;
import pl.commercelink.stores.Store;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ClientPreferredShippingDateService {

    private final OrdersRepository ordersRepository;

    public boolean isEditable(Order order, Store store) {
        return store.isClientPreferredShippingDateEnabled() && order.canClientSetPreferredShippingAt();
    }

    public void change(Order order, LocalDate requested, Store store) {
        if (!isEditable(order, store)) {
            throw new ClientPreferredShippingDateException(Reason.NOT_EDITABLE);
        }
        if (requested != null && !order.isWithinPreferredShippingWindow(requested)) {
            throw new ClientPreferredShippingDateException(Reason.INVALID_DATE);
        }

        order.setPreferredShippingAt(requested);
        ordersRepository.save(order);
    }
}
