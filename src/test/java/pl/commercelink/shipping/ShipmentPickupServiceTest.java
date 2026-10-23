package pl.commercelink.shipping;

import pl.commercelink.shipping.api.ShipmentAddress;
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

    @Mock private PickupCandidates candidates;
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
        when(shippingService.providerNamed(store, "furgonetka")).thenReturn(java.util.Optional.of(provider));
        when(shippingService.providerNamed(store, "allegro")).thenReturn(java.util.Optional.empty());
        when(store.getStoreId()).thenReturn("store-1");
        when(provider.supportsPickups()).thenReturn(true);
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any())).thenReturn(1);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(i -> i.getArgument(0));
        service = new ShipmentPickupService(candidates, owners, shippingService, publisher, messageSource,
                ShippingIntegrationNamesFixture.names());
    }

    private static PickupCandidate candidate(String externalId, String carrier, String ownerId) {
        return new PickupCandidate(ShipmentOwnerType.ORDER, ownerId, externalId, "TRK-" + externalId, "furgonetka",
                carrier, "addr-1");
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
    void groupsAreByProviderCarrierAndAddress() {
        // given
        when(candidates.of("store-1")).thenReturn(List.of(candidate("1", "dpd", "o-1"), candidate("2", "dpd", "o-2"),
                candidate("3", "dhl", "o-3")));

        // when
        List<PickupGroup> groups = service.groups("store-1");

        // then
        assertThat(groups).extracting(PickupGroup::carrier).containsExactly("dhl", "dpd");
        assertThat(groups.get(1).entries()).extracting(PickupCandidate::externalId).containsExactly("1", "2");
        assertThat(groups.get(1).key()).isEqualTo("furgonetka|dpd|addr-1");
    }

    @Test
    void windowsAreAskedFromTodayForTheGivenNumberOfDays() {
        // given
        when(provider.pickupWindows(eq(List.of("1")), any(), eq(LocalDate.now()), eq(3))).thenReturn(List.of(WINDOW));

        // when
        List<PickupWindow> windows = service.windows(store, "furgonetka", null, List.of("1"), 3);

        // then
        assertThat(windows).containsExactly(WINDOW);
    }

    @Test
    void windowsOfAnIntegrationTheStoreNoLongerHasAreNotAsked() {
        // when
        List<PickupWindow> windows = service.windows(store, "allegro", null, List.of("1"), 4);

        // then
        assertThat(windows).isEmpty();
        verify(provider, never()).pickupWindows(anyList(), any(), any(), any(Integer.class));
    }

    @Test
    void orderingMarksTheShipmentsPendingThenSendsOneCommand() {
        // given
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(3)));

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1"), target("2")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.STARTED);
        List<UnaryOperator<ShipmentPickup>> marks = changesApplied(2);
        assertThat(marks.get(0).apply(ShipmentPickup.awaiting()).isPending()).isTrue();
        verify(provider).orderPickup(eq(List.of("1", "2")), any(), eq(WINDOW), anyString());
        verify(publisher).publish(argThat(r -> r.getTargets().size() == 2 && "h".equals(r.getToken())
                && r.getAttempt() == 1 && "furgonetka".equals(r.getProvider())));
    }

    @Test
    void aPickupAlreadyPendingOrOrderedIsNotMarkedAgain() {
        // given
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(3)));
        service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

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
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Termin niedostępny\"}]}")));

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Termin niedostępny");
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(2);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommand().getCommandId();
        ShipmentPickup failed = changes.get(1).apply(pending(commandId));
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.getCommand().getError()).isEqualTo("Termin niedostępny");
        verify(publisher, never()).publish(any());
    }

    @Test
    void aFailedCommandResultMarksThePickupsFailed() {
        // given
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.failed(i.getArgument(3), "Brak podjazdu"));

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("Brak podjazdu");
        changesApplied(2);
        verify(publisher, never()).publish(any());
    }

    @Test
    void unknownOutcomeStaysPendingAndIsChecked() {
        // given
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString())).thenThrow(new RuntimeException("HTTP request failed"));

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.STARTED);
        changesApplied(1);
        verify(publisher).publish(any());
    }

    @Test
    void aCheckThatCannotBeSentSettlesThePickupsUnconfirmed() {
        // given: the courier may be ordered, but nothing would ever check it
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString()))
                .thenAnswer(i -> PickupOrder.pending(i.getArgument(3)));
        doThrow(new RuntimeException("SQS down")).when(publisher).publish(any());

        // when
        PickupStart start;
        List<String> errors;
        try (CapturedLogs logs = CapturedLogs.of(ShipmentPickupService.class)) {
            start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);
            errors = logs.errors();
        }

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.REFUSED);
        assertThat(start.error()).isEqualTo("shipping.pickup.unconfirmed");
        verify(messageSource).getMessage(eq("shipping.pickup.unconfirmed"),
                argThat(args -> args.length == 1 && ShippingIntegrationNamesFixture.DISPLAY_NAME.equals(args[0])), any());
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(2);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommand().getCommandId();
        ShipmentPickup settled = changes.get(1).apply(pending(commandId));
        assertThat(settled.isFailed()).isTrue();
        assertThat(settled.getCommand().getErrorKey()).isEqualTo("shipping.pickup.unconfirmed");
        assertThat(errors).singleElement().satisfies(m -> assertThat(m).contains(commandId, "store-1", "1"));
    }

    @Test
    void aRefusalThatCannotBeRecordedIsLeftToTheCheck() {
        // given: without the check the pickups would stay pending for good
        when(provider.orderPickup(anyList(), any(), eq(WINDOW), anyString())).thenThrow(new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"Termin niedostępny\"}]}")));
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(1).thenThrow(new RuntimeException("DynamoDB down"));

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

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
        assertThatThrownBy(() -> service.order(store, "furgonetka", null, List.of(target("1"), target("2")), WINDOW))
                .hasMessage("DynamoDB down");
        verifyNoInteractions(provider);
        verify(publisher, never()).publish(any());
        List<UnaryOperator<ShipmentPickup>> changes = changesApplied(4);
        String commandId = changes.get(0).apply(ShipmentPickup.awaiting()).getCommand().getCommandId();
        assertThat(changes.get(2).apply(pending(commandId)).getCommand().getErrorKey()).isEqualTo("shipping.pickup.not.sent");
    }

    @Test
    void nothingStillWaitingIsGoneAndNotSent() {
        // given
        when(orderOwner.applyPickup(anyString(), anyString(), anyCollection(), any())).thenReturn(0);

        // when
        PickupStart start = service.order(store, "furgonetka", null, List.of(target("1")), WINDOW);

        // then
        assertThat(start.outcome()).isEqualTo(PickupStart.Outcome.GONE);
        verify(provider, never()).orderPickup(anyList(), any(), any(), anyString());
    }

    @Test
    void aPickupOfAnIntegrationTheStoreNoLongerHasIsRejected() {
        // when / then
        assertThat(service.order(store, "allegro", null, List.of(target("1")), WINDOW).outcome())
                .isEqualTo(PickupStart.Outcome.GONE);
        verifyNoInteractions(provider);
        verifyNoInteractions(orderOwner);
    }

    private static ShippingException refusal(String message) {
        return new ShippingException("HTTP 400",
                new HttpClientException(400, "{\"errors\":[{\"message\":\"" + message + "\",\"code\":\"sent\"}]}"));
    }

    @Test
    void aPackageTheCarrierRefusesIsLeftOutAndTheOthersGetTheirWindows() {
        // given: the provider refuses the whole request because of package 2 alone
        when(provider.pickupWindows(eq(List.of("1", "2", "3")), any(), any(), eq(4))).thenThrow(refusal("Przesyłka została już zamówiona"));
        when(provider.pickupWindows(eq(List.of("1")), any(), any(), eq(4))).thenReturn(List.of(WINDOW));
        when(provider.pickupWindows(eq(List.of("2")), any(), any(), eq(4))).thenThrow(refusal("Przesyłka została już zamówiona"));
        when(provider.pickupWindows(eq(List.of("3")), any(), any(), eq(4))).thenReturn(List.of(WINDOW));
        when(provider.pickupWindows(eq(List.of("1", "3")), any(), any(), eq(4))).thenReturn(List.of(WINDOW));

        // when
        ShipmentPickupService.PageWindows result = service.pageWindows(store, "furgonetka", null, List.of("1", "2", "3"), 4);

        // then
        assertThat(result.windows()).containsExactly(WINDOW);
        assertThat(result.refused()).containsOnlyKeys("2").containsValue("Przesyłka została już zamówiona");
    }

    @Test
    void anErrorThatIsNotARefusalIsNotBlamedOnAPackage() {
        // given: the provider is down
        when(provider.pickupWindows(anyList(), any(), any(), eq(4))).thenThrow(new ShippingException("HTTP 503",
                new HttpClientException(503, "unavailable")));

        // when / then: one call, the error goes to the page as before
        assertThatThrownBy(() -> service.pageWindows(store, "furgonetka", null, List.of("1", "2"), 4))
                .isInstanceOf(ShippingException.class);
        verify(provider, times(1)).pickupWindows(anyList(), any(), any(), eq(4));
    }

    @Test
    void aRefusalNoSinglePackageExplainsIsShownAsBefore() {
        // given: the packages are fine alone, only the pair is refused
        when(provider.pickupWindows(eq(List.of("1", "2")), any(), any(), eq(4))).thenThrow(refusal("Różne adresy"));
        when(provider.pickupWindows(eq(List.of("1")), any(), any(), eq(4))).thenReturn(List.of(WINDOW));
        when(provider.pickupWindows(eq(List.of("2")), any(), any(), eq(4))).thenReturn(List.of(WINDOW));

        // when / then
        assertThatThrownBy(() -> service.pageWindows(store, "furgonetka", null, List.of("1", "2"), 4))
                .isInstanceOf(ShippingException.class);
    }

    @Test
    void windowsAndTheOrderCarryTheAddressTheGroupLeavesFrom() {
        // given
        ShipmentAddress warehouse = new ShipmentAddress("Magazyn", null, "Testowa 1", "00-001", "Warszawa", "PL",
                "magazyn@example.com", "+48123123123");
        when(shippingService.pickupAddress(store, "addr-1")).thenReturn(warehouse);
        when(provider.pickupWindows(eq(List.of("1")), eq(warehouse), any(LocalDate.class), eq(4))).thenReturn(List.of(WINDOW));

        // when
        List<PickupWindow> windows = service.windows(store, "furgonetka", "addr-1", List.of("1"), 4);

        // then
        assertThat(windows).containsExactly(WINDOW);
        verify(provider).pickupWindows(eq(List.of("1")), eq(warehouse), any(LocalDate.class), eq(4));
    }

    @Test
    void aGroupOfAnIntegrationTheStoreNoLongerHasGetsNoWindows() {
        // when / then
        assertThat(service.windows(store, "allegro", "addr-1", List.of("1"), 4)).isEmpty();
    }
}
