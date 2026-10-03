package pl.commercelink.warehouse.builtin;

import pl.commercelink.orders.FulfilmentStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static pl.commercelink.orders.FulfilmentStatus.*;

/**
 * Every bulk action of the warehouse list (spec §4.5): where it posts, which statuses it accepts, which window it opens
 * and where the list goes afterwards. The page menu, the server guard and the messages all read this one table.
 */
public enum WarehouseBulkAction {
    RESERVE("markAsReserved", "reserve", List.of(Delivered), true, false, false, false, Reserved),
    RELEASE("markAsAvailable", "release", List.of(Reserved, InRMA), true, false, false, false, Delivered),
    RMA("markAsInRMA", "rma", List.of(Delivered, Reserved), true, false, false, false, InRMA),
    ALLOCATE("markAsInAllocation", "allocate", List.of(New), false, true, false, false, Allocation),
    EXTERNAL_SERVICE("markAsInExternalService", "externalService", List.of(InRMA), false, true, true, false, InExternalService),
    RECEIVE("markAsReceivedFromExternalService", "received", List.of(InExternalService), false, true, false, false, Delivered),
    SHIP("shipping", "ship", List.of(InRMA), false, false, true, false, null),
    DESTROY("markAsDestroyed", "destroy", List.of(Delivered, InRMA, InExternalService), true, false, false, true, Delivered);

    private final String segment;
    private final String key;
    private final List<FulfilmentStatus> allowed;
    private final boolean needsQuantity;
    private final boolean needsConfirm;
    private final boolean sameSource;
    private final boolean danger;
    private final FulfilmentStatus after;

    WarehouseBulkAction(String segment, String key, List<FulfilmentStatus> allowed, boolean needsQuantity, boolean needsConfirm,
                        boolean sameSource, boolean danger, FulfilmentStatus after) {
        this.segment = segment;
        this.key = key;
        this.allowed = allowed;
        this.needsQuantity = needsQuantity;
        this.needsConfirm = needsConfirm;
        this.sameSource = sameSource;
        this.danger = danger;
        this.after = after;
    }

    public String path() { return "/dashboard/warehouse/" + segment; }
    public String key() { return key; }
    public List<FulfilmentStatus> allowed() { return allowed; }
    public boolean needsQuantity() { return needsQuantity; }
    public boolean needsConfirm() { return needsConfirm; }
    public boolean sameSource() { return sameSource; }
    public boolean danger() { return danger; }
    public FulfilmentStatus after() { return after; }

    public boolean allows(FulfilmentStatus status) { return allowed.contains(status); }

    public Optional<WarehouseItem> firstRefused(List<WarehouseItem> items) {
        return items.stream().filter(item -> !allows(item.getStatus())).findFirst();
    }

    /** The "Zmień stan" menu; "Zniszcz" stands alone in the selection row. */
    public static List<WarehouseBulkAction> menu() {
        return Arrays.stream(values()).filter(a -> a != DESTROY).toList();
    }
}
