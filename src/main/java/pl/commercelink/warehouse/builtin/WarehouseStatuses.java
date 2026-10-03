package pl.commercelink.warehouse.builtin;

import pl.commercelink.orders.FulfilmentStatus;

import java.util.List;

import static pl.commercelink.orders.FulfilmentStatus.*;

/** The statuses the warehouse list shows, in list order, and how each one looks (spec §4.3–§4.4). */
public final class WarehouseStatuses {

    private static final List<FulfilmentStatus> OWN = List.of(Delivered, Reserved, InRMA, InExternalService, New, Allocation, Ordered);
    private static final List<FulfilmentStatus> EXTERNAL_WMS = List.of(New, Allocation, Ordered);

    private WarehouseStatuses() {
    }

    public static List<FulfilmentStatus> visible(boolean wms) {
        return wms ? EXTERNAL_WMS : OWN;
    }

    public static List<FulfilmentStatus> defaults(boolean wms) {
        return List.of(wms ? Ordered : Delivered);
    }

    public static String labelKey(FulfilmentStatus status) {
        return "warehouse.status." + status.name();
    }

    public static String tone(FulfilmentStatus status) {
        return switch (status) {
            case Delivered -> "is-ok";
            case InRMA, InExternalService -> "is-warn";
            case New -> "is-neutral";
            default -> "is-info";
        };
    }

    /** Ordered and Allocation items have no bulk action (they are on their way or in the purchase pool). */
    public static boolean selectable(FulfilmentStatus status) {
        return status != Ordered && status != Allocation;
    }
}
