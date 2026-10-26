package pl.commercelink.web.clientreturn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.orders.ProviderCommandTimeout;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentPickupWindow;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.stores.AuthorizedCarrier;
import pl.commercelink.stores.PackageTemplate;
import pl.commercelink.stores.RMAConfiguration;
import pl.commercelink.stores.ShippingConfiguration;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ClientReturnViewTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);
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
        shipment.setCreation(ShipmentCreationState.pending("cmd", NOW));
        return shipment;
    }

    private static Shipment failed(Shipment shipment) {
        shipment.setCreation(ShipmentCreationState.pending("cmd", NOW).failed("refused"));
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
    void aReturnTemplateWithoutAnIdDoesNotMakeTheStoreBookable() {
        // given
        Store store = store(Config.CONFIGURED);
        store.getShippingConfiguration().getPackageTemplates().get(0).setId(null);

        // when
        ClientReturnView view = ClientReturnView.of(rma(RMAStatus.Approved, false), List.of(), store, NOW);

        // then
        assertThat(view.state()).isEqualTo(ClientReturnState.BOOK_UNAVAILABLE);
        assertThat(view.missingConfiguration()).isEqualTo("templates");
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
        // no address card, so the lead must not say the address is saved
        assertThat(view.leadKey()).isEqualTo("client.return.lead.storeBooks.noAddress");
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

        // when / then: the main column (progress and products first, then the card the state asks to act on) and the side column
        assertColumns(view(rma(RMAStatus.Approved, false)), List.of("progress", "items", "form"), List.of());
        assertColumns(view(rma(RMAStatus.Approved, false), Config.NO_CARRIER), List.of("progress", "items", "form"), List.of());
        assertColumns(view(rma(RMAStatus.Approved, true)), List.of("progress", "items"), List.of("address"));
        assertColumns(view(rma(RMAStatus.Approved, true, parcel(Parcel.RETURN_CREATING))),
                List.of("progress", "items", "parcel"), List.of("address"));
        assertColumns(view(rma(RMAStatus.WaitingForItems, false)), List.of("progress", "items"), List.of());
        assertColumns(view(rma(RMAStatus.WaitingForItems, true, returned)), List.of("progress", "items", "parcel"), List.of("address"));
        assertColumns(view(rma(RMAStatus.ItemsReceived, true, parcel(Parcel.RETURN_CREATED))), List.of("progress", "items"), List.of("parcel"));
        assertColumns(view(rma(RMAStatus.Processing, true, parcel(Parcel.RETURN_CREATED))), List.of("progress", "items"), List.of("parcel"));
        assertColumns(view(rma(RMAStatus.Completed, true, parcel(Parcel.RETURN_CREATED))), List.of("progress", "items"), List.of("parcel"));
        assertColumns(view(rma(RMAStatus.Completed, true)), List.of("progress", "items"), List.of());
        assertColumns(view(rma(RMAStatus.Rejected, true, parcel(Parcel.OPERATOR_CREATED))), List.of("reason", "items"), List.of("parcel"));
        assertColumns(view(rma(RMAStatus.Rejected, true)), List.of("reason", "items"), List.of());
    }

    /** The page shows the main column, then the side column: cards() is both, in that order. */
    private static void assertColumns(ClientReturnView view, List<String> main, List<String> side) {
        assertThat(view.mainCards()).containsExactlyElementsOf(main);
        assertThat(view.sideCards()).containsExactlyElementsOf(side);
        List<String> all = new ArrayList<>(main);
        all.addAll(side);
        assertThat(view.cards()).containsExactlyElementsOf(all);
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
    void trackedWaitingLeadDoesNotPointToAnAddressCardThatIsNotShown() {
        // when
        ClientReturnView withAddress = view(rma(RMAStatus.WaitingForItems, true, parcel(Parcel.RETURN_CREATED)));
        ClientReturnView withoutAddress = view(rma(RMAStatus.Approved, false, parcel(Parcel.RETURN_CREATED)));

        // then
        assertThat(withAddress.leadKey()).isEqualTo("client.return.lead.waiting.tracked");
        assertThat(withoutAddress.state()).isEqualTo(ClientReturnState.WAITING);
        assertThat(withoutAddress.leadKey()).isEqualTo("client.return.lead.waiting.trackedNoAddress");
    }

    @Test
    void bookLeadDoesNotOfferAChoiceWhenThereIsOneTemplate() {
        // given
        Store twoTemplates = store(Config.CONFIGURED);
        PackageTemplate second = new PackageTemplate("RMA - Karton L", List.of());
        second.setId("t2");
        twoTemplates.getShippingConfiguration().getPackageTemplates().add(second);

        // when
        ClientReturnView one = view(rma(RMAStatus.Approved, false));
        ClientReturnView many = ClientReturnView.of(rma(RMAStatus.Approved, false), List.of(new RMAItem()), twoTemplates, NOW);

        // then
        assertThat(one.leadKey()).isEqualTo("client.return.lead.bookOne");
        assertThat(many.leadKey()).isEqualTo("client.return.lead.book");
    }

    @Test
    void leadKeysOfTheOtherStates() {
        // when / then
        assertThat(view(rma(RMAStatus.Approved, false)).leadKey()).isEqualTo("client.return.lead.bookOne");
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
        assertThat(marketplaceView.leadKey()).isEqualTo("client.return.lead.waiting.trackedNoAddress");
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

    @Test
    void shipmentRowsKeepEveryCreatedShipment() {
        // given
        Shipment returned = tracked(customerReturn());
        returned.setCarrier("dhl");
        Shipment fromStore = tracked(operatorShipment());
        fromStore.setCarrier("inpost");
        Shipment manualEntry = new Shipment(ShipmentType.Courier);
        manualEntry.setCarrier("Kurier sklepu");
        manualEntry.setTrackingNo("MANUAL1");

        // when
        ClientReturnView view = view(rma(RMAStatus.WaitingForItems, true, returned, fromStore, manualEntry));

        // then
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::carrier)
                .containsExactly("DHL", "InPost", "Kurier sklepu");
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::labelKey)
                .containsExactly("client.return.shipment.label.customer", "client.return.shipment.label.store", null);
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::typeKey).containsOnlyNulls();
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::stateKey).containsOnlyNulls();
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::trackingNo)
                .containsExactly("TRACK123", "TRACK123", "MANUAL1");
    }

    @Test
    void aShipmentWithoutCarrierIsNamedByItsType() {
        // given
        Shipment manualEntry = new Shipment(ShipmentType.PickupPoint);
        manualEntry.setTrackingNo("MANUAL1");

        // when
        ReturnShipmentRow row = view(rma(RMAStatus.WaitingForItems, true, manualEntry)).shipmentRows().get(0);

        // then
        assertThat(row.carrier()).isNull();
        assertThat(row.typeKey()).isEqualTo("ShipmentType.PickupPoint");
    }

    @Test
    void aShipmentWithoutCarrierNorTypeHasNoTypeKey() {
        // given
        Shipment manualEntry = new Shipment();
        manualEntry.setType(null);
        manualEntry.setTrackingNo("MANUAL1");

        // when
        ReturnShipmentRow row = view(rma(RMAStatus.WaitingForItems, true, manualEntry)).shipmentRows().get(0);

        // then
        assertThat(row.carrier()).isNull();
        assertThat(row.typeKey()).isNull();
    }

    @Test
    void aShipmentBeingCreatedHidesTheNumberItAlreadyHas() {
        // given
        Shipment returned = creating(customerReturn());
        returned.setTrackingNo("EARLY1");
        returned.setTrackingUrl("https://tracking.example/EARLY1");

        // when
        ReturnShipmentRow row = view(rma(RMAStatus.Approved, true, returned)).shipmentRows().get(0);

        // then
        assertThat(row.stateKey()).isEqualTo("client.return.shipment.state.booking");
        assertThat(row.trackingNo()).isNull();
        assertThat(row.trackingUrl()).isNull();
    }

    @Test
    void addressLinesLeaveOutWhatIsMissing() {
        // given
        RMA full = rma(RMAStatus.WaitingForItems, true);
        full.getShippingDetails().setSurname("Kowalski");
        full.getShippingDetails().setPostalCode("00-950");
        full.getShippingDetails().setCountry("PL");
        RMA surnameOnly = rma(RMAStatus.WaitingForItems, true);
        surnameOnly.getShippingDetails().setName(null);
        surnameOnly.getShippingDetails().setSurname("Kowalski");
        RMA companyOnly = rma(RMAStatus.WaitingForItems, true);
        companyOnly.getShippingDetails().setName(" ");
        companyOnly.getShippingDetails().setCompanyName("Nova Tech");
        companyOnly.getShippingDetails().setCity(null);
        companyOnly.getShippingDetails().setCountry("PL");

        // when
        ClientReturnView fullView = view(full);
        ClientReturnView surnameView = view(surnameOnly);
        ClientReturnView companyView = view(companyOnly);

        // then
        assertThat(fullView.addressName()).isEqualTo("Jan Kowalski");
        assertThat(fullView.addressPlace()).isEqualTo("00-950 Warszawa, PL");
        assertThat(surnameView.addressName()).isEqualTo("Kowalski");
        assertThat(surnameView.addressPlace()).isEqualTo("Warszawa");
        assertThat(companyView.addressName()).isEqualTo("Nova Tech");
        assertThat(companyView.addressPlace()).isEqualTo("PL");
        assertThat(view(rma(RMAStatus.Completed, true)).addressName()).isNull();
    }

    @Test
    void blankStreetPhoneAndEmailAreLeftOut() {
        // given
        RMA blank = rma(RMAStatus.WaitingForItems, true);
        blank.getShippingDetails().setStreetAndNumber(" ");
        blank.getShippingDetails().setPhone("");
        blank.getShippingDetails().setEmail("  jan@poczta ");

        // when
        ClientReturnView view = view(blank);

        // then
        assertThat(view.addressStreet()).isNull();
        assertThat(view.addressPhone()).isNull();
        assertThat(view.addressEmail()).isEqualTo("jan@poczta");
        assertThat(view(rma(RMAStatus.Completed, true)).addressEmail()).isNull();
    }

    @Test
    void onlyABookingOrACheckedParcelCarriesAHelpKey() {
        // given
        Shipment booking = creating(customerReturn());
        Shipment preparing = creating(operatorShipment());
        Shipment unknown = customerReturn();
        unknown.setCreation(ShipmentCreationState.pending("cmd", NOW).failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));
        Shipment tracked = tracked(customerReturn());

        // when
        ClientReturnView view = view(rma(RMAStatus.WaitingForItems, true, booking, preparing, unknown, tracked));

        // then
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::helpKey).containsExactly(
                "client.return.shipment.booking.help", null, "client.return.shipment.checking.help", null);
    }

    @Test
    void aCustomerReturnBeingCreatedSaysSo() {
        // given
        Shipment returned = creating(customerReturn());
        Shipment fromStore = creating(operatorShipment());

        // when
        ClientReturnView view = view(rma(RMAStatus.Approved, true, returned, fromStore));

        // then
        assertThat(view.shipmentRows()).hasSize(2);
        ReturnShipmentRow booking = view.shipmentRows().get(0);
        assertThat(booking.stateKey()).isEqualTo("client.return.shipment.state.booking");
        assertThat(booking.stateTone()).isEqualTo("info");
        assertThat(booking.trackingNo()).isNull();
        assertThat(booking.trackingUrl()).isNull();
        ReturnShipmentRow preparing = view.shipmentRows().get(1);
        assertThat(preparing.stateKey()).isEqualTo("client.return.shipment.state.preparing");
        assertThat(preparing.stateTone()).isEqualTo("info");
    }

    @Test
    void aFailedOrUnknownCustomerReturnIsCheckedNotExplained() {
        // given
        Shipment refused = customerReturn();
        refused.setCreation(ShipmentCreationState.pending("cmd", NOW).failed("401 Unauthorized"));
        refused.setCarrier("dhl");
        Shipment unknown = customerReturn();
        unknown.setCreation(ShipmentCreationState.pending("cmd", NOW).failedWithKey(ShipmentCreationState.UNCONFIRMED_KEY));

        // when
        ClientReturnView view = view(rma(RMAStatus.WaitingForItems, true, refused, unknown));

        // then
        assertThat(view.shipmentRows()).hasSize(2).allSatisfy(row -> {
            assertThat(row.stateKey()).isEqualTo("client.return.shipment.state.checking");
            assertThat(row.stateTone()).isEqualTo("warn");
            assertThat(row.toString()).doesNotContain("401").doesNotContain("Unauthorized")
                    .doesNotContain(ShipmentCreationState.UNCONFIRMED_KEY);
        });
    }

    @Test
    void aCustomerReturnPendingPastTheTimeoutIsChecked() {
        // given
        Shipment overdue = customerReturn();
        overdue.setCreation(ShipmentCreationState.pending("cmd",
                NOW.minus(ProviderCommandTimeout.UNCONFIRMED_AFTER).minusMinutes(1)));

        // when
        ClientReturnView view = view(rma(RMAStatus.Approved, true, overdue));

        // then
        assertThat(view.state()).isEqualTo(ClientReturnState.WAITING);
        assertThat(view.leadKey()).isEqualTo("client.return.lead.waiting.checking");
        assertThat(view.shipmentRows()).singleElement().satisfies(row -> {
            assertThat(row.stateKey()).isEqualTo("client.return.shipment.state.checking");
            assertThat(row.stateTone()).isEqualTo("warn");
        });
    }

    @Test
    void aFailedOperatorShipmentIsHidden() {
        // given
        Shipment failedFromStore = failed(operatorShipment());
        Shipment fromStore = tracked(operatorShipment());

        // when
        ClientReturnView view = view(rma(RMAStatus.WaitingForItems, false, failedFromStore, fromStore));
        ClientReturnView onlyFailed = view(rma(RMAStatus.WaitingForItems, false, failed(operatorShipment())));

        // then
        assertThat(view.shipmentRows()).singleElement()
                .satisfies(row -> assertThat(row.trackingNo()).isEqualTo("TRACK123"));
        assertThat(view.cards()).contains("parcel");
        assertThat(onlyFailed.shipmentRows()).isEmpty();
        assertThat(onlyFailed.cards()).doesNotContain("parcel");
    }

    @Test
    void trackingLinkOnlyForWebAddresses() {
        // given
        Shipment script = tracked(customerReturn());
        script.setTrackingUrl("javascript:alert(1)");
        Shipment web = tracked(operatorShipment());
        web.setTrackingUrl(" https://tracking.example/TRACK123 ");

        // when
        ClientReturnView view = view(rma(RMAStatus.WaitingForItems, true, script, web));

        // then
        assertThat(view.shipmentRows()).extracting(ReturnShipmentRow::trackingUrl)
                .containsExactly(null, "https://tracking.example/TRACK123");
    }

    @Test
    void aShipmentWithoutNumberKeepsCarrierAndDate() {
        // given
        Shipment shipment = operatorShipment();
        shipment.setCarrier("dpd");
        shipment.setShippedAt(NOW.minusDays(2));
        shipment.setDeliveredAt(NOW.minusDays(1));

        // when
        ReturnShipmentRow row = view(rma(RMAStatus.Completed, false, shipment)).shipmentRows().get(0);

        // then
        assertThat(row.carrier()).isEqualTo("DPD");
        assertThat(row.trackingNo()).isNull();
        assertThat(row.shippedAt()).isEqualTo(NOW.minusDays(2));
        assertThat(row.deliveredAt()).isEqualTo(NOW.minusDays(1));
    }

    @Test
    void pickupWindowComesFromThePickup() {
        // given
        Shipment withWindow = tracked(customerReturn());
        withWindow.setPickup(ShipmentPickup.pending("p1", NOW, LocalDate.of(2026, 10, 24), LocalTime.of(9, 0), LocalTime.of(15, 0))
                .ordered("PICK1"));
        Shipment bookedByCarrier = tracked(customerReturn());
        bookedByCarrier.setPickup(ShipmentPickup.bookedByCarrier("PICK2"));
        Shipment noPickup = tracked(operatorShipment());

        // when
        List<ReturnShipmentRow> rows = view(rma(RMAStatus.WaitingForItems, true, withWindow, bookedByCarrier, noPickup))
                .shipmentRows();

        // then
        ShipmentPickupWindow window = rows.get(0).pickupWindow();
        assertThat(window.getDate()).isEqualTo("2026-10-24");
        assertThat(window.formatFrom()).isEqualTo("9:00");
        assertThat(window.formatTo()).isEqualTo("15:00");
        assertThat(rows.get(1).pickupWindow()).isNull();
        assertThat(rows.get(2).pickupWindow()).isNull();
    }

    private static RMAItem item(String name, RMAItemStatus status, RMAResolutionType actual) {
        RMAItem item = new RMAItem();
        item.setName(name);
        item.setQty(2);
        item.setDesiredResolution(RMAResolutionType.Repair);
        item.setActualResolution(actual);
        item.setReason("  Nie działa  ");
        item.setStatus(status);
        return item;
    }

    @Test
    void itemRowsMapStatusesToCustomerWords() {
        // given
        List<RMAItem> items = List.of(
                item("A", RMAItemStatus.New, null),
                item("B", RMAItemStatus.Received, null),
                item("C", RMAItemStatus.SentForRepair, RMAResolutionType.Repair),
                item("D", RMAItemStatus.ReturnedToClient, RMAResolutionType.Repair),
                item("E", RMAItemStatus.MovedToWarehouse, RMAResolutionType.Return));

        // when
        List<ReturnItemRow> rows = ClientReturnView.of(rma(RMAStatus.Processing, false), items, store(Config.CONFIGURED), NOW)
                .itemRows();

        // then
        assertThat(rows).extracting(ReturnItemRow::statusKey).containsExactly(null,
                "client.return.item.status.received", "client.return.item.status.sentForRepair",
                "client.return.item.status.returnedToClient", "client.return.item.status.movedToWarehouse");
        assertThat(rows).extracting(ReturnItemRow::statusTone).containsExactly(null, "info", "info", "ok", "ok");
        assertThat(rows.get(4)).isEqualTo(new ReturnItemRow("E", 2, RMAResolutionType.Repair, RMAResolutionType.Return,
                "Nie działa", "client.return.item.status.movedToWarehouse", "ok"));
    }

    @Test
    void rejectedItemsHaveNoStatusNorResolution() {
        // given
        List<RMAItem> items = List.of(item("A", RMAItemStatus.Received, RMAResolutionType.Return));

        // when
        List<ReturnItemRow> rows = ClientReturnView.of(rma(RMAStatus.Rejected, false), items, store(Config.CONFIGURED), NOW)
                .itemRows();

        // then
        assertThat(rows).singleElement().isEqualTo(
                new ReturnItemRow("A", 2, RMAResolutionType.Repair, null, "Nie działa", null, null));
    }

    @Test
    void packageOptionDescribesTheChosenTemplate() {
        // given
        Store store = store(Config.CONFIGURED);
        PackageTemplate unprefixed = new PackageTemplate("duży karton", List.of(new pl.commercelink.stores.Parcel(80, 40, 60, 12, 0, null)));
        unprefixed.setId("t2");
        store.getShippingConfiguration().getPackageTemplates().add(unprefixed);
        RMA chosen = rma(RMAStatus.WaitingForItems, true);
        chosen.setReturnPackageTemplateId("t2");
        RMA removed = rma(RMAStatus.WaitingForItems, true);
        removed.setReturnPackageTemplateId("gone");
        RMA none = rma(RMAStatus.WaitingForItems, true);

        // when
        ReturnPackageOption option = ClientReturnView.of(chosen, List.of(new RMAItem()), store, NOW).packageOption();

        // then
        assertThat(option.id()).isEqualTo("t2");
        assertThat(option.title()).isEqualTo("Duży karton");
        assertThat(option.parcels()).isEqualTo("80 × 40 × 60 cm · 12 kg");
        assertThat(ClientReturnView.of(removed, List.of(new RMAItem()), store, NOW).packageOption()).isNull();
        assertThat(ClientReturnView.of(none, List.of(new RMAItem()), store, NOW).packageOption()).isNull();
    }
}
