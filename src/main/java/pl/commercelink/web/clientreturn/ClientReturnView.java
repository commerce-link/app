package pl.commercelink.web.clientreturn;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.CustomerReturnRetry;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.shipping.CarrierNames;
import pl.commercelink.starter.util.ConversionUtil;
import pl.commercelink.stores.Store;
import pl.commercelink.web.orders.OrderPageModelFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final List<ReturnShipmentRow> shipmentRows;
    private final List<ReturnItemRow> itemRows;
    private final ReturnPackageOption packageOption;
    private final boolean singleReturnOption;

    private ClientReturnView(RMA rma, List<RMAItem> items, Store store, LocalDateTime now) {
        this.rma = rma;
        this.items = items;
        this.now = now;
        String missing = missingConfiguration(store);
        this.state = stateOf(missing);
        this.missingConfiguration = state == ClientReturnState.BOOK_UNAVAILABLE ? missing : null;
        this.shipmentRows = shipments().stream().filter(this::isShown).map(this::toRow).toList();
        this.itemRows = items.stream().map(this::toRow).toList();
        this.singleReturnOption = ReturnPackageOptions.of(store).size() == 1;
        this.packageOption = rma.getReturnPackageTemplateId() == null ? null
                : ReturnPackageOptions.find(store, rma.getReturnPackageTemplateId()).orElse(null);
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
        if (anyShipment(s -> isCustomersReturn(s) && s.isCreating(now))) {
            return ClientReturnState.BOOKING;
        }
        // An Approved RMA with a return parcel but no address: its save after the booking failed. The form would end in
        // GONE, so the page waits for the parcel instead of offering another booking.
        if (anyShipment(this::isCustomersReturn) || rma.getStatus() == RMAStatus.WaitingForItems) {
            return ClientReturnState.WAITING;
        }
        // Another shipment being created makes the booking POST answer GONE, so no form either.
        if (rma.getShippingDetails() != null || anyShipment(s -> s.isCreating(now))) {
            return ClientReturnState.STORE_BOOKS;
        }
        return missing == null ? ClientReturnState.BOOK : ClientReturnState.BOOK_UNAVAILABLE;
    }

    /**
     * What the store lacks to book a customer's return: "carrier", "templates" or null. Mirrors
     * RMAShippingService.validateStoreReturnConfiguration plus the template list the form offers.
     */
    public static String missingConfiguration(Store store) {
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
            case BOOK -> singleReturnOption ? "bookOne" : "book";
            // another shipment being created, no address card: the lead must not say the address is saved
            case STORE_BOOKS -> address() == null ? "storeBooks.noAddress" : "storeBooks";
            case REJECTED -> rejectionReason() == null ? "rejected.noReason" : "rejected.withReason";
            default -> state.lowerCamelName();
        };
        return "client.return.lead." + suffix;
    }

    // Only the customer's own return parcel tells where the goods are; a shipment from the store says nothing about it.
    private String waitingLead() {
        List<Shipment> returns = customersReturns();
        if (returns.stream().anyMatch(s -> StringUtils.isNotBlank(s.getTrackingNo()) && !s.creationFailed(now))) {
            // The lead must not send the customer to an address card that is not shown.
            return address() == null ? "trackedNoAddress" : "tracked";
        }
        if (returns.stream().anyMatch(s -> s.creationFailed(now))) {
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

    /**
     * Who the courier collects from, without the "null" a missing first name or surname would leave in
     * ShippingDetails.getDisplayName; null without an address card.
     */
    public String addressName() {
        ShippingDetails details = address();
        if (details == null) {
            return null;
        }
        String person = joinPresent(" ", details.getName(), details.getSurname());
        return person != null ? person : StringUtils.trimToNull(details.getCompanyName());
    }

    /** "00-950 Warszawa, PL" from the parts the address has; null without an address card. */
    public String addressPlace() {
        ShippingDetails details = address();
        if (details == null) {
            return null;
        }
        return joinPresent(", ", joinPresent(" ", details.getPostalCode(), details.getCity()), details.getCountry());
    }

    /** The street and number, null when blank or without an address card. */
    public String addressStreet() {
        ShippingDetails details = address();
        return details == null ? null : StringUtils.trimToNull(details.getStreetAndNumber());
    }

    /** The phone number, null when blank or without an address card. */
    public String addressPhone() {
        ShippingDetails details = address();
        return details == null ? null : StringUtils.trimToNull(details.getPhone());
    }

    /** The e-mail address, null when blank or without an address card. */
    public String addressEmail() {
        ShippingDetails details = address();
        return details == null ? null : StringUtils.trimToNull(details.getEmail());
    }

    private static String joinPresent(String separator, String... parts) {
        String joined = Stream.of(parts).map(StringUtils::trimToNull).filter(Objects::nonNull)
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
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
        return shipmentRows.isEmpty() ? null : "parcel";
    }

    /** Every shipment of the RMA in the order it was saved, in the customer's words. */
    public List<ReturnShipmentRow> shipmentRows() {
        return shipmentRows;
    }

    public List<ReturnItemRow> itemRows() {
        return itemRows;
    }

    /** The template the return was booked with, or null when none was chosen or the store has removed it. */
    public ReturnPackageOption packageOption() {
        return packageOption;
    }

    // A failed creation of the operator's shipment is not a shipment: the operator books it again or removes it.
    private boolean isShown(Shipment shipment) {
        return isCustomersReturn(shipment) || !shipment.creationFailed(now);
    }

    // The page is public and its link gets forwarded: the reason of a failed creation (provider or operator words)
    // never reaches it, and a number from a creation in doubt is not one the customer can follow.
    private ReturnShipmentRow toRow(Shipment shipment) {
        boolean customers = isCustomersReturn(shipment);
        String stateKey = null;
        String stateTone = null;
        String helpKey = null;
        if (shipment.isCreating(now)) {
            stateKey = customers ? "client.return.shipment.state.booking" : "client.return.shipment.state.preparing";
            stateTone = "info";
            helpKey = customers ? "client.return.shipment.booking.help" : null;
        } else if (customers && shipment.creationFailed(now)) {
            stateKey = "client.return.shipment.state.checking";
            stateTone = "warn";
            helpKey = "client.return.shipment.checking.help";
        }
        boolean settled = stateKey == null;
        String carrier = CarrierNames.forCustomer(shipment);
        return new ReturnShipmentRow(carrier,
                carrier == null && shipment.getType() != null ? "ShipmentType." + shipment.getType().name() : null,
                labelKeyOf(shipment, customers),
                settled ? StringUtils.trimToNull(shipment.getTrackingNo()) : null,
                settled ? OrderPageModelFactory.safeWebUrl(shipment.getTrackingUrl()) : null,
                shipment.getShippedAt(), shipment.getDeliveredAt(),
                shipment.getPickup() == null ? null : shipment.getPickup().getWindow(),
                stateKey, stateTone, helpKey);
    }

    // A shipment typed by hand in the dashboard says nothing about who sent it, so it carries no label.
    private static String labelKeyOf(Shipment shipment, boolean customers) {
        if (customers) {
            return "client.return.shipment.label.customer";
        }
        return shipment.getPickUpAddressId() != null ? "client.return.shipment.label.store" : null;
    }

    private ReturnItemRow toRow(RMAItem item) {
        boolean rejected = state == ClientReturnState.REJECTED;
        RMAItemStatus status = rejected ? null : item.getStatus();
        return new ReturnItemRow(item.getName(), item.getQty(), item.getDesiredResolution(),
                rejected ? null : item.getActualResolution(), StringUtils.trimToNull(item.getReason()),
                statusKeyOf(status), statusToneOf(status));
    }

    private static String statusKeyOf(RMAItemStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case New -> null;
            case Received -> "client.return.item.status.received";
            case SentForRepair -> "client.return.item.status.sentForRepair";
            case ReturnedToClient -> "client.return.item.status.returnedToClient";
            case MovedToWarehouse -> "client.return.item.status.movedToWarehouse";
        };
    }

    private static String statusToneOf(RMAItemStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case New -> null;
            case Received, SentForRepair -> "info";
            case ReturnedToClient, MovedToWarehouse -> "ok";
        };
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
}
