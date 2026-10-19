package pl.commercelink.orders.history;

import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;

import java.util.EnumSet;
import java.util.Set;

/** An RMA item of this store that carries the serial number, with its RMA. */
public record RmaLine(RMA rma, RMAItem item) {

    private static final Set<RMAItemStatus> IN_PROGRESS =
            EnumSet.of(RMAItemStatus.New, RMAItemStatus.Received, RMAItemStatus.SentForRepair);

    public boolean inProgress() {
        return item.getStatus() != null && IN_PROGRESS.contains(item.getStatus());
    }
}
