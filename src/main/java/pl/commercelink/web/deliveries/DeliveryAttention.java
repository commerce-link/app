package pl.commercelink.web.deliveries;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListSortKey;
import pl.commercelink.inventory.deliveries.DeliveryListState;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The tiles above the deliveries list (spec §5); a tile narrows the list to exactly what it counts. */
public enum DeliveryAttention {
    OVERDUE("overdue"), TODAY("today"), PROBLEM("problem"), INVOICE("invoice"), APPROVAL("approval");

    private final String param;

    DeliveryAttention(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public DeliveryListQuery.Scope scope() {
        return this == INVOICE ? DeliveryListQuery.Scope.RECEIVED : DeliveryListQuery.Scope.TRANSIT;
    }

    public boolean matches(Delivery delivery, DeliveryListState state, LocalDate today) {
        LocalDate planned = delivery.getEstimatedDeliveryAt();
        return switch (this) {
            case OVERDUE -> state.expectsArrival() && planned != null && planned.isBefore(today);
            case TODAY -> state.expectsArrival() && today.equals(planned);
            case PROBLEM -> state.isProblem();
            case INVOICE -> DeliveryListSortKey.of(delivery).startsWith(DeliveryListSortKey.TO_SETTLE);
            case APPROVAL -> state == DeliveryListState.AWAITING_APPROVAL;
        };
    }

    public static List<DeliveryAttention> forStore() {
        return List.of(OVERDUE, TODAY, PROBLEM, INVOICE);
    }

    public static List<DeliveryAttention> forSuperAdmin() {
        return List.of(APPROVAL, OVERDUE, TODAY, PROBLEM);
    }

    public static Optional<DeliveryAttention> parse(String value) {
        return value == null ? Optional.empty()
                : Arrays.stream(values()).filter(a -> a.param.equalsIgnoreCase(value.trim())).findFirst();
    }
}
