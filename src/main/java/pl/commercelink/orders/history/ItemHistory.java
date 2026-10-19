package pl.commercelink.orders.history;

import java.util.List;

/** Everything the store knows about one serial number; events are the newest EVENT_LIMIT, totalEvents counts all. */
public record ItemHistory(String serialNo, boolean found, ItemIdentity identity, ItemAmbiguity ambiguity, ItemNow now,
                          List<ItemHistoryEvent> events, int totalEvents) {

    public static final int EVENT_LIMIT = 100;
}
