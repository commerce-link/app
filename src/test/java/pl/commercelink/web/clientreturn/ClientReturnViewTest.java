package pl.commercelink.web.clientreturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ClientReturnViewTest {

    private static final LocalDateTime NOW = LocalDateTime.now();
    private static final String RMA_ID = "1a2b3c4d-0000-0000-0000-000000000000";
    private static final String ORDER_ID = "9f8e7d6c-1111-1111-1111-111111111111";

    enum Config { CONFIGURED, NO_CARRIER, NO_TEMPLATES }

    enum Parcel { NONE, RETURN_CREATING, RETURN_CREATED, RETURN_FAILED, OPERATOR_CREATING, OPERATOR_CREATED, OPERATOR_FAILED }

    private static Store store(Config config) {
        Store store = new Store();
        if (config != Config.NO_CARRIER) {
            RMAConfiguration rmaConfiguration = new RMAConfiguration();
            rmaConfiguration.setCarrier(new AuthorizedCarrier("c1", "Carrier", "Carrier"));
            store.setRmaConfiguration(rmaConfiguration);
        }
        ShippingConfiguration shipping = new ShippingConfiguration();
        String name = config == Config.NO_TEMPLATES ? "Karton S" : "RMA - Karton S";
        PackageTemplate template = new PackageTemplate(name, List.of());
        template.setId("t1");
        shipping.setPackageTemplates(new ArrayList<>(List.of(template)));
        store.setShippingConfiguration(shipping);
        return store;
    }

    private static ShippingDetails address() {
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setCity("Warszawa");
        return details;
    }

    private static Shipment customerReturn() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        return shipment;
    }

    private static Shipment operatorShipment() {
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.setProvider("furgonetka");
        shipment.setPickUpAddressId("warehouse");
        return shipment;
    }

    private static Shipment creating(Shipment shipment) {
        shipment.setCreation(ShipmentCreationState.pending("cmd", LocalDateTime.now()));
        return shipment;
    }

    private static Shipment failed(Shipment shipment) {
        shipment.setCreation(ShipmentCreationState.pending("cmd", LocalDateTime.now()).failed("refused"));
        return shipment;
    }

    private static Shipment tracked(Shipment shipment) {
        shipment.setTrackingNo("TRACK123");
        return shipment;
    }

    private static Shipment parcel(Parcel parcel) {
        return switch (parcel) {
            case NONE -> null;
            case RETURN_CREATING -> creating(customerReturn());
            case RETURN_CREATED -> tracked(customerReturn());
            case RETURN_FAILED -> failed(customerReturn());
            case OPERATOR_CREATING -> creating(operatorShipment());
            case OPERATOR_CREATED -> tracked(operatorShipment());
            case OPERATOR_FAILED -> failed(operatorShipment());
        };
    }

    private static RMA rma(RMAStatus status, boolean withAddress, Shipment... shipments) {
        RMA rma = new RMA("store");
        rma.setRmaId(RMA_ID);
        rma.setOrderId(ORDER_ID);
        rma.setStatus(status);
        rma.setShippingDetails(withAddress ? address() : null);
        List<Shipment> list = new ArrayList<>();
        for (Shipment shipment : shipments) {
            if (shipment != null) {
                list.add(shipment);
            }
        }
        rma.setShipments(list);
        return rma;
    }

    private static ClientReturnView view(RMA rma, Config config) {
        return ClientReturnView.of(rma, List.of(new RMAItem()), store(config), NOW);
    }

    private static ClientReturnView view(RMA rma) {
        return view(rma, Config.CONFIGURED);
    }

    static Stream<Arguments> specCases() {
        return Stream.of(
                arguments(RMAStatus.Approved, false, Parcel.NONE, Config.CONFIGURED, ClientReturnState.BOOK),
                arguments(RMAStatus.Approved, false, Parcel.NONE, Config.NO_CARRIER, ClientReturnState.BOOK_UNAVAILABLE),
                arguments(RMAStatus.Approved, false, Parcel.NONE, Config.NO_TEMPLATES, ClientReturnState.BOOK_UNAVAILABLE),
                arguments(RMAStatus.Approved, false, Parcel.OPERATOR_CREATED, Config.CONFIGURED, ClientReturnState.BOOK),
                arguments(RMAStatus.Approved, true, Parcel.NONE, Config.CONFIGURED, ClientReturnState.STORE_BOOKS),
                arguments(RMAStatus.Approved, true, Parcel.NONE, Config.NO_CARRIER, ClientReturnState.STORE_BOOKS),
                arguments(RMAStatus.Approved, false, Parcel.OPERATOR_CREATING, Config.CONFIGURED, ClientReturnState.STORE_BOOKS),
                arguments(RMAStatus.Approved, true, Parcel.RETURN_CREATING, Config.CONFIGURED, ClientReturnState.BOOKING),
                arguments(RMAStatus.Approved, false, Parcel.RETURN_CREATING, Config.CONFIGURED, ClientReturnState.BOOKING),
                arguments(RMAStatus.WaitingForItems, true, Parcel.RETURN_CREATING, Config.CONFIGURED, ClientReturnState.BOOKING),
                arguments(RMAStatus.Approved, false, Parcel.RETURN_CREATED, Config.CONFIGURED, ClientReturnState.WAITING),
                arguments(RMAStatus.Approved, false, Parcel.RETURN_FAILED, Config.CONFIGURED, ClientReturnState.WAITING),
                arguments(RMAStatus.Approved, true, Parcel.RETURN_FAILED, Config.CONFIGURED, ClientReturnState.WAITING),
                arguments(RMAStatus.WaitingForItems, false, Parcel.NONE, Config.CONFIGURED, ClientReturnState.WAITING),
                arguments(RMAStatus.WaitingForItems, true, Parcel.RETURN_CREATED, Config.CONFIGURED, ClientReturnState.WAITING),
                arguments(RMAStatus.ItemsReceived, true, Parcel.RETURN_CREATING, Config.CONFIGURED, ClientReturnState.ARRIVED),
                arguments(RMAStatus.Processing, true, Parcel.RETURN_CREATED, Config.CONFIGURED, ClientReturnState.REVIEWING),
                arguments(RMAStatus.Completed, false, Parcel.NONE, Config.CONFIGURED, ClientReturnState.COMPLETED),
                arguments(RMAStatus.Rejected, false, Parcel.RETURN_CREATING, Config.CONFIGURED, ClientReturnState.REJECTED));
    }

    @ParameterizedTest
    @MethodSource("specCases")
    void stateFollowsTheSpecOrder(RMAStatus status, boolean withAddress, Parcel parcel, Config config, ClientReturnState expected) {
        // given
        RMA rma = rma(status, withAddress, parcel(parcel));

        // when
        ClientReturnView view = view(rma, config);

        // then
        assertThat(view.state()).isEqualTo(expected);
    }

    @Test
    void missingConfigurationNamesWhatIsMissing() {
        // given
        RMA rma = rma(RMAStatus.Approved, false);

        // when
        ClientReturnView noCarrier = view(rma, Config.NO_CARRIER);
        ClientReturnView noTemplates = view(rma, Config.NO_TEMPLATES);
        ClientReturnView configured = view(rma, Config.CONFIGURED);
        ClientReturnView otherState = view(rma(RMAStatus.WaitingForItems, false), Config.NO_CARRIER);

        // then
        assertThat(noCarrier.missingConfiguration()).isEqualTo("carrier");
        assertThat(noTemplates.missingConfiguration()).isEqualTo("templates");
        assertThat(configured.missingConfiguration()).isNull();
        assertThat(otherState.missingConfiguration()).isNull();
    }

    @Test
    void storeBooksWithoutAddressWhenAnotherShipmentIsBeingCreated() {
        // given
        RMA rma = rma(RMAStatus.Approved, false, parcel(Parcel.OPERATOR_CREATING));

        // when
        ClientReturnView view = view(rma);

        // then
        assertThat(view.state()).isEqualTo(ClientReturnState.STORE_BOOKS);
        assertThat(view.address()).isNull();
        assertThat(view.cards()).doesNotContain("address");
    }

    @Test
    void everyStatusWithAPageHasExactlyOneState() {
        // given
        List<RMAStatus> statuses = List.of(RMAStatus.Approved, RMAStatus.WaitingForItems, RMAStatus.ItemsReceived,
                RMAStatus.Processing, RMAStatus.Rejected, RMAStatus.Completed);
        List<Parcel> parcels = List.of(Parcel.NONE, Parcel.RETURN_CREATING, Parcel.RETURN_CREATED, Parcel.RETURN_FAILED,
                Parcel.OPERATOR_CREATING, Parcel.OPERATOR_CREATED, Parcel.OPERATOR_FAILED);
        List<Supplier<ClientReturnView>> views = new ArrayList<>();
        for (RMAStatus status : statuses) {
            for (boolean withAddress : List.of(true, false)) {
                for (Parcel parcel : parcels) {
                    for (Config config : Config.values()) {
                        views.add(() -> view(rma(status, withAddress, parcel(parcel)), config));
                    }
                }
            }
        }

        // when
        List<ClientReturnView> built = views.stream().map(Supplier::get).toList();

        // then
        assertThat(built).hasSize(statuses.size() * 2 * parcels.size() * Config.values().length);
        assertThat(built).allSatisfy(view -> {
            assertThat(view.state()).isNotNull();
            assertThat(view.leadKey()).startsWith("client.return.lead.");
            assertThat(view.cards()).isNotEmpty().contains("items").doesNotHaveDuplicates();
        });
    }

    @Test
    void headlineKeyIsTheLowerCamelName() {
        // when / then
        assertThat(ClientReturnState.BOOK_UNAVAILABLE.headlineKey()).isEqualTo("client.return.headline.bookUnavailable");
        assertThat(ClientReturnState.STORE_BOOKS.headlineKey()).isEqualTo("client.return.headline.storeBooks");
        assertThat(ClientReturnState.BOOK.headlineKey()).isEqualTo("client.return.headline.book");
        assertThat(ClientReturnState.REJECTED.headlineKey()).isEqualTo("client.return.headline.rejected");
    }

    @Test
    void cardsFollowTheStateTable() {
        // given
        Shipment returned = parcel(Parcel.RETURN_CREATED);

        // when / then
        assertThat(view(rma(RMAStatus.Approved, false)).cards()).containsExactly("form", "progress", "items");
        assertThat(view(rma(RMAStatus.Approved, false), Config.NO_CARRIER).cards()).containsExactly("form", "progress", "items");
        assertThat(view(rma(RMAStatus.Approved, true)).cards()).containsExactly("progress", "address", "items");
        assertThat(view(rma(RMAStatus.Approved, true, parcel(Parcel.RETURN_CREATING))).cards())
                .containsExactly("parcel", "progress", "address", "items");
        assertThat(view(rma(RMAStatus.WaitingForItems, false)).cards()).containsExactly("progress", "items");
        assertThat(view(rma(RMAStatus.WaitingForItems, true, returned)).cards())
                .containsExactly("parcel", "progress", "address", "items");
        assertThat(view(rma(RMAStatus.ItemsReceived, true, parcel(Parcel.RETURN_CREATED))).cards())
                .containsExactly("progress", "parcel", "items");
        assertThat(view(rma(RMAStatus.Processing, true, parcel(Parcel.RETURN_CREATED))).cards())
                .containsExactly("progress", "items", "parcel");
        assertThat(view(rma(RMAStatus.Completed, true, parcel(Parcel.RETURN_CREATED))).cards())
                .containsExactly("progress", "items", "parcel");
        assertThat(view(rma(RMAStatus.Completed, true)).cards()).containsExactly("progress", "items");
        assertThat(view(rma(RMAStatus.Rejected, true, parcel(Parcel.OPERATOR_CREATED))).cards())
                .containsExactly("reason", "parcel", "items");
        assertThat(view(rma(RMAStatus.Rejected, true)).cards()).containsExactly("reason", "items");
    }

    @Test
    void failedOperatorShipmentShowsNoParcelCard() {
        // given
        RMA rma = rma(RMAStatus.WaitingForItems, false, parcel(Parcel.OPERATOR_FAILED));

        // when
        ClientReturnView view = view(rma);

        // then
        assertThat(view.cards()).containsExactly("progress", "items");
    }

    @Test
    void addressOnlyWhileTheParcelIsOnItsWay() {
        // when / then
        assertThat(view(rma(RMAStatus.Approved, true)).address()).isNotNull();
        assertThat(view(rma(RMAStatus.WaitingForItems, true)).address()).isNotNull();
        assertThat(view(rma(RMAStatus.Approved, true, parcel(Parcel.RETURN_CREATING))).address()).isNotNull();
        assertThat(view(rma(RMAStatus.ItemsReceived, true)).address()).isNull();
        assertThat(view(rma(RMAStatus.Completed, true)).address()).isNull();
        assertThat(view(rma(RMAStatus.Rejected, true)).address()).isNull();
    }

    @Test
    void stagesAreDoneUpToTheCurrentOne() {
        // when
        ClientReturnView received = view(rma(RMAStatus.ItemsReceived, true));
        ClientReturnView completed = view(rma(RMAStatus.Completed, true));
        ClientReturnView approved = view(rma(RMAStatus.Approved, false));
        ClientReturnView rejected = view(rma(RMAStatus.Rejected, true));

        // then
        assertThat(received.stageIndex()).isEqualTo(2);
        assertThat(received.stageDone(0)).isTrue();
        assertThat(received.stageDone(1)).isTrue();
        assertThat(received.stageDone(2)).isFalse();
        assertThat(received.stageCurrent(2)).isTrue();
        assertThat(received.stageCurrent(1)).isFalse();
        assertThat(received.stageDone(3)).isFalse();

        assertThat(completed.stageIndex()).isEqualTo(4);
        for (int i = 0; i < 5; i++) {
            assertThat(completed.stageDone(i)).isTrue();
            assertThat(completed.stageCurrent(i)).isFalse();
        }

        assertThat(approved.stageIndex()).isZero();
        assertThat(approved.stageCurrent(0)).isTrue();
        assertThat(approved.stageDone(0)).isFalse();

        assertThat(rejected.stageIndex()).isEqualTo(-1);
        for (int i = 0; i < 5; i++) {
            assertThat(rejected.stageDone(i)).isFalse();
            assertThat(rejected.stageCurrent(i)).isFalse();
        }
    }

    @Test
    void rejectionReasonOnlyWhenRejected() {
        // given
        RMA rejected = rma(RMAStatus.Rejected, false);
        rejected.setRejectionReason("  Produkt nosi ślady użytkowania  ");
        RMA blank = rma(RMAStatus.Rejected, false);
        blank.setRejectionReason("   ");
        RMA completed = rma(RMAStatus.Completed, false);
        completed.setRejectionReason("Stary powód");

        // when
        ClientReturnView rejectedView = view(rejected);
        ClientReturnView blankView = view(blank);
        ClientReturnView completedView = view(completed);

        // then
        assertThat(rejectedView.rejectionReason()).isEqualTo("Produkt nosi ślady użytkowania");
        assertThat(rejectedView.leadKey()).isEqualTo("client.return.lead.rejected.withReason");
        assertThat(blankView.rejectionReason()).isNull();
        assertThat(blankView.leadKey()).isEqualTo("client.return.lead.rejected.noReason");
        assertThat(completedView.rejectionReason()).isNull();
    }

    @Test
    void waitingLeadDependsOnTheReturnParcel() {
        // when
        ClientReturnView tracked = view(rma(RMAStatus.WaitingForItems, true, parcel(Parcel.RETURN_CREATED)));
        ClientReturnView checking = view(rma(RMAStatus.WaitingForItems, true, parcel(Parcel.RETURN_FAILED)));
        ClientReturnView noParcel = view(rma(RMAStatus.WaitingForItems, false));
        ClientReturnView onlyOperator = view(rma(RMAStatus.WaitingForItems, false, parcel(Parcel.OPERATOR_CREATED)));

        // then
        assertThat(tracked.leadKey()).isEqualTo("client.return.lead.waiting.tracked");
        assertThat(checking.leadKey()).isEqualTo("client.return.lead.waiting.checking");
        assertThat(noParcel.leadKey()).isEqualTo("client.return.lead.waiting.noParcel");
        assertThat(onlyOperator.leadKey()).isEqualTo("client.return.lead.waiting.noParcel");
    }

    @Test
    void leadKeysOfTheOtherStates() {
        // when / then
        assertThat(view(rma(RMAStatus.Approved, false)).leadKey()).isEqualTo("client.return.lead.book");
        assertThat(view(rma(RMAStatus.Approved, false), Config.NO_CARRIER).leadKey()).isEqualTo("client.return.lead.bookUnavailable");
        assertThat(view(rma(RMAStatus.Approved, true)).leadKey()).isEqualTo("client.return.lead.storeBooks");
        assertThat(view(rma(RMAStatus.Approved, true, parcel(Parcel.RETURN_CREATING))).leadKey()).isEqualTo("client.return.lead.booking");
        assertThat(view(rma(RMAStatus.ItemsReceived, true)).leadKey()).isEqualTo("client.return.lead.arrived");
        assertThat(view(rma(RMAStatus.Processing, true)).leadKey()).isEqualTo("client.return.lead.reviewing");
        assertThat(view(rma(RMAStatus.Completed, true)).leadKey()).isEqualTo("client.return.lead.completed");
    }

    @Test
    void marketplaceParcelWithoutProviderIsTheCustomersReturn() {
        // given
        Shipment allegroParcel = new Shipment(ShipmentType.Courier);
        allegroParcel.setTrackingNo("ALLEGRO1");
        allegroParcel.setDeliveredAt(NOW.minusDays(1));
        RMA marketplace = rma(RMAStatus.Approved, false, allegroParcel);
        marketplace.setExternalReturnId("allegro-return-1");
        Shipment manualEntry = new Shipment(ShipmentType.Courier);
        manualEntry.setTrackingNo("MANUAL1");
        manualEntry.setDeliveredAt(NOW.minusDays(1));
        RMA manual = rma(RMAStatus.Approved, false, manualEntry);

        // when
        ClientReturnView marketplaceView = view(marketplace);
        ClientReturnView manualView = view(manual);

        // then
        assertThat(marketplaceView.state()).isEqualTo(ClientReturnState.WAITING);
        assertThat(marketplaceView.leadKey()).isEqualTo("client.return.lead.waiting.tracked");
        assertThat(marketplaceView.arrivedAt()).isEqualTo(NOW.minusDays(1));
        assertThat(manualView.state()).isEqualTo(ClientReturnState.BOOK);
        assertThat(manualView.arrivedAt()).isNull();
    }

    @Test
    void arrivedAtIsTheDeliveryOfTheCustomersReturnParcel() {
        // given
        Shipment toCustomer = tracked(operatorShipment());
        toCustomer.setDeliveredAt(NOW.minusDays(5));
        Shipment returned = tracked(customerReturn());
        returned.setDeliveredAt(NOW.minusDays(2));

        // when
        ClientReturnView view = view(rma(RMAStatus.ItemsReceived, true, toCustomer, returned));

        // then
        assertThat(view.arrivedAt()).isEqualTo(NOW.minusDays(2));
    }

    @Test
    void headerIdentifiers() {
        // given
        RMA rma = rma(RMAStatus.Approved, false);
        RMA withoutOrder = rma(RMAStatus.Approved, false);
        withoutOrder.setOrderId(null);

        // when
        ClientReturnView view = view(rma);
        ClientReturnView noOrder = view(withoutOrder);

        // then
        assertThat(view.shortRmaId()).isEqualTo("1a2b3c4d");
        assertThat(view.shortOrderId()).isEqualTo("9f8e7d6c");
        assertThat(view.createdAt()).isEqualTo(rma.getCreatedAt());
        assertThat(noOrder.shortOrderId()).isNull();
    }
}
