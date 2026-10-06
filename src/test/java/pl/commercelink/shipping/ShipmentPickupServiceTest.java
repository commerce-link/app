package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.orders.ShipmentPickup;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.PickupWindow;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupServiceTest {

    private static final PickupWindow WINDOW =
            new PickupWindow(LocalDate.of(2026, 10, 7), LocalTime.of(9, 0), LocalTime.of(17, 0), "h");

    @Mock private AwaitingPickupIndex index;
    @Mock private ShipmentOwners owners;
    @Mock private ShipmentOwner orderOwner;
    @Mock private ShippingService shippingService;
    @Mock private ShipmentPickupEventPublisher publisher;
    @Mock private MessageSource messageSource;
    @Mock private ShippingProvider provider;
    @Mock private Store store;

    private ShipmentPickupService service;

    @BeforeEach
    void setUp() {
        when(owners.get(ShipmentOwnerType.ORDER)).thenReturn(orderOwner);
        when(shippingService.providerFor(store)).thenReturn(provider);
        when(shippingService.providerName(store)).thenReturn("furgonetka");
        when(store.getStoreId()).thenReturn("store-1");
        when(provider.supportsPickups()).thenReturn(true);
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any())).thenReturn(1);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        service = new ShipmentPickupService(index, owners, shippingService, publisher, messageSource);
    }

    private static AwaitingPickup entry(String externalId, String carrier, String ownerId) {
        AwaitingPickup e = new AwaitingPickup();
        e.setStoreId("store-1");
        e.setExternalId(externalId);
        e.setProvider("furgonetka");
        e.setCarrier(carrier);
        e.setPickUpAddressId("addr-1");
        e.setOwnerType(ShipmentOwnerType.ORDER);
        e.setOwnerId(ownerId);
        return e;
    }

    private static PickupTarget target(String externalId) {
        return new PickupTarget(ShipmentOwnerType.ORDER, "order-1", externalId, "TRK-" + externalId);
    }

    private static ShipmentPickup pending(String commandId) {
        return ShipmentPickup.pending(commandId, LocalDateTime.now(), WINDOW.date(), WINDOW.from(), WINDOW.to());
    }

    @SuppressWarnings("unchecked")
    private List<UnaryOperator<ShipmentPickup>> changesApplied(int times) {
        ArgumentCaptor<UnaryOperator<ShipmentPickup>> changes = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(orderOwner, times(times)).applyPickup(eq("store-1"), eq("order-1"), anyCollection(), changes.capture());
        return changes.getAllValues();
    }

    @Test
    void groupsAreByProviderCarrierAndAddressAndStaleEntriesGo() {
        // given
        when(index.list("store-1")).thenReturn(List.of(entry("1", "dpd", "o-1"), entry("2", "dpd", "o-2"),
                entry("3", "dhl", "o-3"), entry("4", "dpd", "o-4")));
        when(orderOwner.pickupStanding(eq("store-1"), anyString(), anyString())).thenReturn(PickupStanding.ORDERABLE);
        when(orderOwner.pickupStanding("store-1", "o-4", "4")).thenReturn(PickupStanding.GONE);

        // when
        List<PickupGroup> groups = service.groups("store-1");

        // then
        assertThat(groups).extracting(PickupGroup::carrier).containsExactly("dhl", "dpd");
        assertThat(groups.get(1).entries()).extracting(AwaitingPickup::getExternalId).containsExactly("1", "2");
        assertThat(groups.get(1).key()).isEqualTo("furgonetka|dpd|addr-1");
        verify(index).remove("store-1", List.of("4"));
    }

    @Test
    void aPackageWhosePickupIsBeingOrderedIsSkippedButStaysIndexed() {
        // given
        when(index.list("store-1")).thenReturn(List.of(entry("1", "dpd", "o-1"), entry("2", "dpd", "o-2")));
        when(orderOwner.pickupStanding("store-1", "o-1", "1")).thenReturn(PickupStanding.ORDERABLE);
        when(orderOwner.pickupStanding("store-1", "o-2", "2")).thenReturn(PickupStanding.IN_FLIGHT);

        // when
        List<PickupGroup> groups = service.groups("store-1");

        // then
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).entries()).extracting(AwaitingPickup::getExternalId).containsExactly("1");
        verify(index, never()).remove(anyString(), anyList());
    }

    @Test
    void windowsAreAskedFromTodayForTheGivenNumberOfDays() {
        // given
        when(provider.pickupWindows(List.of("1"), LocalDate.now(), 3)).thenReturn(List.of(WINDOW));

        // when
        List<PickupWindow> windows = service.windows(store, "furgonetka", List.of("1"), 3);

        // then
        assertThat(windows).containsExactly(WINDOW);
    }

    @Test
    void windowsOfAnotherIntegrationThanTheStoresAreNotAsked() {
        // when
        List<PickupWindow> windows = service.windows(store, "allegro", List.of("1"), 4);

        // then
        assertThat(windows).isEmpty();
        verify(provider, never()).pickupWindows(anyList(), any(), any(Integer.class));
    }

    @Test
    void orderingMarksTheShipmentsPendingThenSendsOneCommand() {
        // given
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(2), i.getArgument(0), WINDOW));

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1"), target("2")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.STARTED);
        List<UnaryOperator<ShipmentPickup>> marks = changesApplied(2);
        assertThat(marks.get(0).apply(ShipmentPickup.awaiting()).isPending()).isTrue();
        verify(provider).orderPickup(eq(List.of("1", "2")), eq(WINDOW), anyString());
        verify(publisher).publish(argThat(r -> r.getTargets().size() == 2 && "h".equals(r.getToken())
                && r.getAttempt() == 1 && "furgonetka".equals(r.getProvider())));
    }

    @Test
    void aPickupAlreadyPendingOrOrderedIsNotMarkedAgain() {
        // given
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(2), i.getArgument(0), WINDOW));
        service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // when
        UnaryOperator<ShipmentPickup> mark = changesApplied(1).get(0);

        // then: a second operator's command next to one in flight would order a second courier
        ShipmentPickup inFlight = pending("other");
        ShipmentPickup ordered = pending("other").ordered("P-1");
        assertThat(mark.apply(inFlight)).isSameAs(inFlight);
        assertThat(mark.apply(ordered)).isSameAs(ordered);
    }

    @Test
    void aRefusalMarksThePickupsFailed() {
        // given
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Termin niedostępny\"}]}")));

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Termin niedostępny");
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(2);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommandId();
        ShipmentPickup failed = changes.get(1).apply(pending(commandId));
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.getError()).isEqualTo("Termin niedostępny");
        verify(publisher, never()).publish(any());
    }

    @Test
    void aFailedCommandResultMarksThePickupsFailed() {
        // given
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.failed(i.getArgument(2), i.getArgument(0), "Brak podjazdu"));

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Brak podjazdu");
        changesApplied(2);
        verify(publisher, never()).publish(any());
    }

    @Test
    void unknownOutcomeStaysPendingAndIsChecked() {
        // given
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString())).thenThrow(new RuntimeException("HTTP request failed"));

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.STARTED);
        changesApplied(1);
        verify(publisher).publish(any());
    }

    @Test
    void aCheckThatCannotBeSentSettlesThePickupsUnconfirmed() {
        // given: the courier may be ordered, but nothing would ever check it
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(2), i.getArgument(0), WINDOW));
        doThrow(new RuntimeException("SQS down")).when(publisher).publish(any());

        // when
        PickupStart start;
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentPickupService.class)) {
            start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);
            errors = logs.errors();
        }

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("shipping.pickup.unconfirmed");
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(2);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommandId();
        ShipmentPickup settled = changes.get(1).apply(pending(commandId));
        assertThat(settled.isFailed()).isTrue();
        assertThat(settled.getErrorKey()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(errors).singleElement().satisfies(m -> assertThat(m).contains(commandId, "store-1", "1"));
    }

    @Test
    void aRefusalThatCannotBeRecordedIsLeftToTheCheck() {
        // given: without the check the pickups would stay pending for good
        when(provider.orderPickup(anyList(), eq(WINDOW), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Termin niedostępny\"}]}")));
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(1).thenThrow(new RuntimeException("DynamoDB down"));

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        verify(publisher).publish(any());
    }

    @Test
    void aMarkThatBreaksOffSendsNothingAndFailsWhatItMarked() {
        // given
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(1).thenThrow(new RuntimeException("DynamoDB down")).thenReturn(1);

        // when / then
        assertThatThrownBy(() -> service.order(store, "furgonetka", List.of(target("1"), target("2")), WINDOW))
                .hasMessage("DynamoDB down");
        verifyNoInteractions(provider);
        verify(publisher, never()).publish(any());
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(4);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommandId();
        assertThat(changes.get(2).apply(pending(commandId)).getErrorKey()).isEqualTo("shipping.pickup.not.sent");
    }

    @Test
    void nothingStillWaitingIsGoneAndNotSent() {
        // given
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any())).thenReturn(0);

        // when
        PickupStart start = service.order(store, "furgonetka", List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.GONE);
        verify(provider, never()).orderPickup(anyList(), any(), anyString());
    }

    @Test
    void anotherProviderThanTheStoresIsRejected() {
        // when / then
        assertThat(service.order(store, "allegro", List.of(target("1")), WINDOW).outcome())
                .isEqualTo(PickupStart.Outcome.GONE);
        verifyNoInteractions(provider);
        verifyNoInteractions(orderOwner);
    }
}
