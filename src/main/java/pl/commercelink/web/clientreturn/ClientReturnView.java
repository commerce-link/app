package pl.commercelink.web.clientreturn;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.CustomerReturnRetry;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Everything the public return page shows, resolved once so the template carries no logic. The state decides the
 * headline, the lead and which cards appear in which order; it is evaluated in the order fixed by the design
 * (rejected, completed, processing, received, a return being booked, a return parcel or waiting, then approved).
 */
public final class ClientReturnView {

    public static final int STAGE_COUNT = 5;

    private final RMA rma;
    private final List<RMAItem> items;
    private final LocalDateTime now;
    private final ClientReturnState state;
    private final String missingConfiguration;

    private ClientReturnView(RMA rma, List<RMAItem> items, Store store, LocalDateTime now) {
        this.rma = rma;
        this.items = items;
        this.now = now;
        String missing = missingConfigurationOf(store);
        this.state = stateOf(missing);
        this.missingConfiguration = state == ClientReturnState.BOOK_UNAVAILABLE ? missing : null;
    }

    /** Only for an RMA that has a page: a New RMA has none, the controller answers it with the unavailable page. */
    public static ClientReturnView of(RMA rma, List<RMAItem> items, Store store, LocalDateTime now) {
        if (rma.getStatus() == null || rma.getStatus() == RMAStatus.New) {
            throw new IllegalArgumentException("RMA " + rma.getRmaId() + " in status " + rma.getStatus() + " has no client page");
        }
        return new ClientReturnView(rma, items, store, now);
    }

    private ClientReturnState stateOf(String missing) {
        switch (rma.getStatus()) {
            case Rejected:
                return ClientReturnState.REJECTED;
            case Completed:
                return ClientReturnState.COMPLETED;
            case Processing:
                return ClientReturnState.REVIEWING;
            case ItemsReceived:
                return ClientReturnState.ARRIVED;
            default:
                break;
        }
        if (anyShipment(s -> isCustomersReturn(s) && isCreating(s))) {
            return ClientReturnState.BOOKING;
        }
        // An Approved RMA with a return parcel but no address: its save after the booking failed. The form would end in
        // GONE, so the page waits for the parcel instead of offering another booking.
        if (anyShipment(this::isCustomersReturn) || rma.getStatus() == RMAStatus.WaitingForItems) {
            return ClientReturnState.WAITING;
        }
        // Another shipment being created makes the booking POST answer GONE, so no form either.
        if (rma.getShippingDetails() != null || anyShipment(this::isCreating)) {
            return ClientReturnState.STORE_BOOKS;
        }
        return missing == null ? ClientReturnState.BOOK : ClientReturnState.BOOK_UNAVAILABLE;
    }

    /** Mirrors RMAShippingService.validateStoreReturnConfiguration plus the template list the form offers. */
    private static String missingConfigurationOf(Store store) {
        if (store.getRmaConfiguration() == null || store.getRmaConfiguration().getCarrier() == null) {
            return "carrier";
        }
        if (ReturnPackageOptions.of(store).isEmpty()) {
            return "templates";
        }
        return null;
    }

    public ClientReturnState state() {
        return state;
    }

    public String shortRmaId() {
        return ConversionUtil.getShortenedId(rma.getRmaId());
    }

    public String shortOrderId() {
        return StringUtils.isBlank(rma.getOrderId()) ? null : ConversionUtil.getShortenedId(rma.getOrderId());
    }

    public LocalDateTime createdAt() {
        return rma.getCreatedAt();
    }

    public List<RMAItem> items() {
        return items;
    }

    /** 0-4 from the RMA status; Completed has every stage done and none current, Rejected shows no stages. */
    public int stageIndex() {
        return switch (rma.getStatus()) {
            case Approved -> 0;
            case WaitingForItems -> 1;
            case ItemsReceived -> 2;
            case Processing -> 3;
            case Completed -> 4;
            case New, Rejected -> -1;
        };
    }

    public boolean stageDone(int i) {
        if (state == ClientReturnState.COMPLETED) {
            return i >= 0 && i < STAGE_COUNT;
        }
        return i >= 0 && i < stageIndex();
    }

    public boolean stageCurrent(int i) {
        return state != ClientReturnState.COMPLETED && i >= 0 && i == stageIndex();
    }

    public LocalDateTime arrivedAt() {
        return customersReturns().stream()
                .map(Shipment::getDeliveredAt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public String leadKey() {
        String suffix = switch (state) {
            case WAITING -> "waiting." + waitingLead();
            case REJECTED -> rejectionReason() == null ? "rejected.noReason" : "rejected.withReason";
            default -> state.lowerCamelName();
        };
        return "client.return.lead." + suffix;
    }

    // Only the customer's own return parcel tells where the goods are; a shipment from the store says nothing about it.
    private String waitingLead() {
        List<Shipment> returns = customersReturns();
        if (returns.stream().anyMatch(s -> StringUtils.isNotBlank(s.getTrackingNo()) && !creationFailed(s))) {
            return "tracked";
        }
        if (returns.stream().anyMatch(this::creationFailed)) {
            return "checking";
        }
        return "noParcel";
    }

    /** "carrier", "templates" or null, for the error log of a page that cannot offer the booking. */
    public String missingConfiguration() {
        return missingConfiguration;
    }

    /** Where the courier picks the parcel up; irrelevant once the parcel has arrived. */
    public ShippingDetails address() {
        return switch (state) {
            case STORE_BOOKS, BOOKING, WAITING -> rma.getShippingDetails();
            default -> null;
        };
    }

    public String rejectionReason() {
        return state == ClientReturnState.REJECTED ? StringUtils.trimToNull(rma.getRejectionReason()) : null;
    }

    public List<String> cards() {
        List<String> cards = new ArrayList<>();
        switch (state) {
            case BOOK, BOOK_UNAVAILABLE -> add(cards, "form", "progress", "items");
            case STORE_BOOKS -> add(cards, "progress", addressCard(), "items");
            case BOOKING, WAITING -> add(cards, parcelCard(), "progress", addressCard(), "items");
            case ARRIVED -> add(cards, "progress", parcelCard(), "items");
            case REVIEWING, COMPLETED -> add(cards, "progress", "items", parcelCard());
            case REJECTED -> add(cards, "reason", parcelCard(), "items");
        }
        return cards;
    }

    private static void add(List<String> cards, String... ids) {
        for (String id : ids) {
            if (id != null) {
                cards.add(id);
            }
        }
    }

    private String addressCard() {
        return address() != null ? "address" : null;
    }

    private String parcelCard() {
        return hasShownShipments() ? "parcel" : null;
    }

    // A failed creation of the operator's shipment is not a shipment: the operator books it again or removes it.
    private boolean hasShownShipments() {
        return anyShipment(s -> isCustomersReturn(s) || !creationFailed(s));
    }

    /** The customer's return parcel: booked from this page, or imported with a marketplace return (no provider). */
    private boolean isCustomersReturn(Shipment shipment) {
        return CustomerReturnRetry.isCustomerReturn(shipment)
                || (rma.isMarketplaceReturn() && shipment.getProvider() == null);
    }

    private List<Shipment> customersReturns() {
        return shipments().stream().filter(this::isCustomersReturn).toList();
    }

    private boolean anyShipment(Predicate<Shipment> predicate) {
        return shipments().stream().anyMatch(predicate);
    }

    private List<Shipment> shipments() {
        return rma.getShipments() == null ? List.of() : rma.getShipments();
    }

    // Same rules as Shipment.isCreating/creationFailed, but against the page's clock.
    private boolean isCreating(Shipment shipment) {
        return shipment.getCreation() != null && shipment.getCreation().isInProgress(now);
    }

    private boolean creationFailed(Shipment shipment) {
        return shipment.getCreation() != null
                && (shipment.getCreation().isFailed() || shipment.getCreation().isUnconfirmed(now));
    }
}
