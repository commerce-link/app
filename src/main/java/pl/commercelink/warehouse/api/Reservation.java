package pl.commercelink.warehouse.api;

import pl.commercelink.documents.Document;
import pl.commercelink.orders.BillingDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Reservation {

    private final String storeId;
    private final List<ReservationItem> items;
    private final List<ReservationRemovalItem> removalItems;

    private String orderId;
    private BillingDetails party;
    private Document document;
    private boolean rma = false;

    private Reservation(String storeId, List<ReservationItem> items, List<ReservationRemovalItem> removalItems, boolean rma) {
        this.storeId = storeId;
        this.items = items;
        this.removalItems = removalItems;
        this.rma = rma;
    }

    private Reservation(String storeId, String orderId, BillingDetails party, Optional<Document> document, List<ReservationItem> reservationItems) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.party = party;
        this.items = reservationItems;
        this.removalItems = new ArrayList<>();

        document.ifPresent(value -> this.document = value);
    }

    public static Reservation orderFulfilment(String storeId, String orderId, BillingDetails party, Optional<Document> document, List<ReservationItem> reservationItems) {
        return new Reservation(storeId, orderId, party, document, reservationItems);
    }

    public static Reservation internalUse(String storeId, List<ReservationItem> items) {
        return new Reservation(storeId, items, new ArrayList<>(), false);
    }

    public static Reservation internalRMA(String storeId, List<ReservationItem> items) {
        return new Reservation(storeId, items, new ArrayList<>(), true);
    }

    public static Reservation orderFulfilmentToRMA(String storeId, ReservationRemovalItem item) {
        return new Reservation(storeId, new ArrayList<>(), Collections.singletonList(item), true);
    }

    public static Reservation orderFulfilmentToStock(String storeId, ReservationRemovalItem item) {
        return new Reservation(storeId, new ArrayList<>(), Collections.singletonList(item), false);
    }

    public void add(Document document) {
        this.document = document;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public BillingDetails getParty() {
        return party;
    }

    public Document getDocument() {
        return document;
    }

    public boolean isRma() {
        return rma;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public ReservationItem findItemById(String itemId) {
        return items.stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No reservation item for itemId: " + itemId));
    }

    public List<ReservationItem> getItems() {
        return items;
    }

    public List<ReservationRemovalItem> getRemovalItems() {
        return removalItems;
    }

    public List<ReservationItem> getUnfulfilledItems() {
        return items.stream()
                .filter(i -> i.getRemainingQty() > 0)
                .collect(Collectors.toList());
    }

}
