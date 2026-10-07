package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.notifications.StoreNotificationService;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.warehouse.builtin.WarehouseGoodsOutService;
import pl.commercelink.warehouse.builtin.WarehouseShippingReservations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseShipmentOwnerTest {

    @Mock private StoreNotificationService notifications;
    @Mock private WarehouseGoodsOutService goodsOutService;
    @Mock private MessageSource messageSource;
    @Mock private WarehouseShippingReservations reservations;

    private WarehouseShipmentOwner owner;
    private final ShippingDetails receiver = new ShippingDetails();

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(goodsOutService.issueGoodsOutForExternalService(any(), any(), any(), any()))
                .thenReturn(OperationResult.success());
        owner = new WarehouseShipmentOwner(notifications, goodsOutService, reservations, messageSource);
    }

    private ShipmentCreationCheckRequest request() {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.WAREHOUSE)
                .commandId("cmd-1").externalId("21480003").provider("furgonetka").itemIds(List.of("w-1", "w-2"))
                .receiver(receiver).issuedBy("anna").build();
    }

    private static Shipment created() {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId("21480003");
        s.setTrackingNo("A");
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    private static PickupTarget target() {
        return new PickupTarget(ShipmentOwnerType.WAREHOUSE, null, "21480003", "A");
    }

    private static ShipmentPickup pending() {
        return ShipmentPickup.pending("pick-1", LocalDateTime.now(), LocalDate.of(2026, 10, 6), LocalTime.of(14, 0),
                LocalTime.of(17, 0));
    }

    @Test
    void aCreatedShipmentIssuesTheGoodsOutAndTellsTheStore() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(true);

        // when
        boolean settled = owner.succeeded(request(), List.of(created()));

        // then
        assertThat(settled).isTrue();
        verify(goodsOutService).issueGoodsOutForExternalService("store-1", List.of("w-1", "w-2"), receiver, "anna");
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.WAREHOUSE_SHIPMENT_CREATED
                        && "furgonetka:21480003".equals(n.getObject())
                        && n.getSeverity() == StoreNotificationSeverity.INFO
                        && "shipping.notification.warehouse.created".equals(n.getMessage())));
    }

    @Test
    void duplicateSuccessDoesNotIssueGoodsOutTwice() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(false);

        // when
        boolean settled = owner.succeeded(request(), List.of(created()));

        // then
        assertThat(settled).isFalse();
        verifyNoInteractions(goodsOutService);
    }

    @Test
    void aShipmentWithoutAPackageIdIsGuardedByItsCommand() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(true);

        // when
        owner.succeeded(request().withExternalId(null), List.of(created()));

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.WAREHOUSE_SHIPMENT_CREATED && "cmd-1".equals(n.getObject())));
    }

    @Test
    void aRefusedGoodsOutIsAnErrorForTheOperator() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(true);
        when(goodsOutService.issueGoodsOutForExternalService(any(), any(), any(), any()))
                .thenReturn(OperationResult.failure("Failed to fetch delivery with id: d-1"));

        // when
        boolean settled;
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(WarehouseShipmentOwner.class)) {
            settled = owner.succeeded(request(), List.of(created()));
            errors = logs.errors();
        }

        // then
        assertThat(settled).isTrue();
        assertThat(errors).singleElement().asString()
                .contains("21480003", "store-1", "goods-out failed", "Failed to fetch delivery with id: d-1");
    }

    @Test
    void aGoodsOutThatThrowsIsAnErrorForTheOperator() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(true);
        when(goodsOutService.issueGoodsOutForExternalService(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("dynamo down"));

        // when
        boolean settled;
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(WarehouseShipmentOwner.class)) {
            settled = owner.succeeded(request(), List.of(created()));
            errors = logs.errors();
        }

        // then
        assertThat(settled).isTrue();
        assertThat(errors).singleElement().asString().contains("21480003", "store-1", "goods-out failed");
    }

    @Test
    void aFailedCreationIsReported() {
        // when
        owner.failed(request(), "Nieprawidłowy kod pocztowy", null);

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.WAREHOUSE_SHIPMENT_FAILED && "cmd-1".equals(n.getObject())
                        && n.getSeverity() == StoreNotificationSeverity.WARNING));
        verify(messageSource).getMessage(eq("shipping.notification.warehouse.failed"),
                argThat(args -> "Nieprawidłowy kod pocztowy".equals(args[0])), any());
    }

    @Test
    void aFailureOfOurOwnIsReportedInTheOperatorsWords() {
        // when
        owner.failed(request(), null, "shipping.creation.unconfirmed");

        // then
        verify(messageSource).getMessage(eq("shipping.notification.warehouse.failed"),
                argThat(args -> "shipping.creation.unconfirmed".equals(args[0])), any());
    }

    @Test
    void anOrderedPickupIsReportedUnderThePackage() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), pending().ordered("p-1"));

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getType() == StoreNotificationType.WAREHOUSE_SHIPMENT_PICKUP
                        && "furgonetka:21480003".equals(n.getObject())
                        && n.getSeverity() == StoreNotificationSeverity.INFO
                        && "shipping.notification.warehouse.pickup.ordered".equals(n.getMessage())));
    }

    @Test
    void noPickupMeansHandingInAtAPoint() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.notRequired());

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                "shipping.notification.warehouse.pickup.point".equals(n.getMessage())));
    }

    @Test
    void aFailedPickupIsAWarning() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), pending().failed("Brak kuriera"));

        // then
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getSeverity() == StoreNotificationSeverity.WARNING
                        && "shipping.notification.warehouse.pickup.failed".equals(n.getMessage())));
    }

    @Test
    void aFailedPickupTellsTheOperatorHowToSendTheParcelInBothLanguagesWithTheReasonLast() {
        // given: a warehouse shipment is stored nowhere in the app, so its pickup cannot be ordered again here; the
        // provider's reason may come without a closing period, so nothing may follow it
        ResourceBundleMessageSource bundles = new ResourceBundleMessageSource();
        bundles.setBasename("messages");
        bundles.setDefaultEncoding("UTF-8");
        Object[] args = {"A", null, null, null, "Brak kuriera w rejonie", null};

        // when
        String pl = bundles.getMessage("shipping.notification.warehouse.pickup.failed", args, Locale.forLanguageTag("pl"));
        String en = bundles.getMessage("shipping.notification.warehouse.pickup.failed", args, Locale.ENGLISH);

        // then
        assertThat(pl).isEqualTo("Nie udało się zamówić odbioru przesyłki A. Zamów kuriera w panelu integracji wysyłki "
                + "(np. Furgonetki) albo nadaj paczkę w punkcie przewoźnika. Powód: Brak kuriera w rejonie");
        assertThat(en).isEqualTo("The pickup of shipment A could not be ordered. Book a courier in the shipping "
                + "integration's panel (e.g. Furgonetka) or hand the parcel in at a carrier point. Reason: Brak kuriera w rejonie");
    }

    @Test
    void aCourierTheCarrierBookedIsReportedAsOrderedWithItsNumber() {
        // when
        owner.onPickupSettled("store-1", "furgonetka", target(), ShipmentPickup.bookedByCarrier("APP/CRIN/13023761"));

        // then
        verify(messageSource).getMessage(eq("shipping.notification.warehouse.pickup.carrier"),
                argThat(args -> args.length == 6 && "APP/CRIN/13023761".equals(args[5])), any());
        verify(notifications).publish(eq("store-1"), argThat((StoreNotification n) ->
                n.getSeverity() == StoreNotificationSeverity.INFO
                        && "shipping.notification.warehouse.pickup.carrier".equals(n.getMessage())));
    }

    @Test
    void aNewShipmentHoldsItsItemsForItsCommand() {
        // given
        when(reservations.hold("store-1", List.of("w-1", "w-2"), "cmd-1")).thenReturn(true);

        // when / then
        assertThat(owner.markCreating(request(), new Shipment(ShipmentType.Courier))).isTrue();
    }

    @Test
    void itemsAnotherShipmentHoldsRefuseTheNewOne() {
        // given: a second tab or operator ships the same items before the first goods-out
        when(reservations.hold("store-1", List.of("w-1", "w-2"), "cmd-1")).thenReturn(false);

        // when / then: no command is sent, so no second label is paid for
        assertThat(owner.markCreating(request(), new Shipment(ShipmentType.Courier))).isFalse();
    }

    @Test
    void aRefusedOrFailedCreationLetsTheItemsGo() {
        // when
        owner.refused(request(), "Nieprawidłowy kod pocztowy", null);
        owner.failed(request(), "Brak odpowiedzi", null);

        // then
        verify(reservations, org.mockito.Mockito.times(2)).release("store-1", List.of("w-1", "w-2"), "cmd-1");
    }

    @Test
    void theItemsAreLetGoOnlyAfterTheirGoodsOut() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(true);

        // when
        owner.succeeded(request(), List.of(created()));

        // then
        InOrder order = inOrder(goodsOutService, reservations);
        order.verify(goodsOutService).issueGoodsOutForExternalService("store-1", List.of("w-1", "w-2"), receiver, "anna");
        order.verify(reservations).release("store-1", List.of("w-1", "w-2"), "cmd-1");
    }

    @Test
    void aRepeatedSuccessLeavesTheItemsAlone() {
        // given
        when(notifications.publish(eq("store-1"), any())).thenReturn(false);

        // when
        owner.succeeded(request(), List.of(created()));

        // then
        verify(reservations, never()).release(any(), any(), any());
    }
}
