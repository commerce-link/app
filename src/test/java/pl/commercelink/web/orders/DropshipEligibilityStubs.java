package pl.commercelink.web.orders;

import pl.commercelink.inventory.deliveries.DropshipAssessment;
import pl.commercelink.inventory.deliveries.DropshipEligibility;
import pl.commercelink.orders.OrderItem;

import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test doubles of DropshipEligibility for the order page model, which asks it about every direct-to-consumer order. */
public final class DropshipEligibilityStubs {

    private DropshipEligibilityStubs() {
    }

    /**
     * Accepts every supplier the order's items name, as when each of them can ship straight to the customer; the
     * factory still offers the dropship page only for an item waiting in Allocation, so which items count stays its
     * own rule under test.
     */
    public static DropshipEligibility acceptingEverySupplier() {
        DropshipEligibility eligibility = mock(DropshipEligibility.class);
        when(eligibility.assess(any(), any())).thenAnswer(invocation -> {
            List<OrderItem> items = invocation.getArgument(1);
            return DropshipAssessment.of(items.stream().map(OrderItem::getDeliveryId).filter(Objects::nonNull)
                    .distinct().toList());
        });
        return eligibility;
    }
}
