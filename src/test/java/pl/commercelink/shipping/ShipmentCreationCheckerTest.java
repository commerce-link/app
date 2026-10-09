package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.shipping.api.*;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentCreationCheckerTest {

    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviders shippingProviders;
    @Mock private ShipmentOwners owners;
    @Mock private ShipmentOwner owner;
    @Mock private ShipmentCreationEventPublisher publisher;
    @Mock private ShipmentCreationSettler settler;
    @Mock private Store store;
    @Mock private ShippingProvider provider;

    private ShipmentCreationChecker checker;

    @BeforeEach
    void setUp() {
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingProviders.forCommand(eq(store), any())).thenReturn(java.util.Optional.of(provider));
        when(owners.get(ShipmentOwnerType.ORDER)).thenReturn(owner);
        when(owner.awaits(any())).thenReturn(true);
        checker = new ShipmentCreationChecker(storesRepository, shippingProviders, owners, publisher, settler);
    }

    private static ShipmentCreationCheckRequest request(int attempt) {
        return ShipmentCreationCheckRequest.builder().storeId("store-1").ownerType(ShipmentOwnerType.ORDER)
                .ownerId("order-1").commandId("cmd-1").externalId("21480003").attempt(attempt).build();
    }

    @Test
    void aMessageNobodyWaitsForIsDropped() {
        // given
        when(owner.awaits(any())).thenReturn(false);

        // when
        checker.check(request(1));

        // then
        verifyNoInteractions(provider, settler, publisher);
    }

    @Test
    void pendingIsAskedAgainWithTheNextAttempt() {
        // given
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.pending("cmd-1", "21480003"));

        // when
        checker.check(request(1));

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2));
        verifyNoInteractions(settler);
    }

    @Test
    void theLastPendingAttemptFailsAsUnconfirmed() {
        // given
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.pending("cmd-1", "21480003"));

        // when
        checker.check(request(ShipmentCreationChecker.MAX_ATTEMPTS));

        // then
        verify(settler).failedWithKey(any(), eq("shipping.creation.unconfirmed"));
        verify(publisher, never()).publish(any());
    }

    @Test
    void aSuccessIsSettled() {
        // given
        ShipmentResult result = new ShipmentResult("21480003",
                List.of(new ShipmentResult.ShipmentParcelResult("A", "dpd", null, true, null)), null);
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.succeeded("cmd-1", result));

        // when
        checker.check(request(1));

        // then
        verify(settler).succeeded(any(), eq(result));
    }

    @Test
    void anErrorWhileCheckingCountsAsPending() {
        // given
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenThrow(new ShippingException("HTTP 502"));

        // when
        checker.check(request(1));

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2));
    }

    @Test
    void missingProviderIsAskedAgainWhileAttemptsRemain() {
        // given: the integration may be reconnected before the checks run out
        when(shippingProviders.forCommand(eq(store), any())).thenReturn(java.util.Optional.empty());

        // when
        checker.check(request(1));

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2 && "21480003".equals(r.getExternalId())));
        verifyNoInteractions(settler);
    }

    @Test
    void checkerWithDisconnectedIntegrationSettlesTheCreationAsUnconfirmedOnTheLastAttempt() {
        // given: the store dropped Wysyłam z Allegro while the command was queued and never reconnected it
        when(shippingProviders.forCommand(store, "allegro")).thenReturn(java.util.Optional.empty());

        // when
        checker.check(request(ShipmentCreationChecker.MAX_ATTEMPTS).toBuilder().provider("allegro").build());

        // then: Allegro may have created (and charged) the shipment, so the outcome is unconfirmed, not a refusal;
        // settled once, nothing re-queued, nothing thrown (no DLQ loop)
        verify(settler).failedWithKey(any(), eq(ShipmentCreationState.UNCONFIRMED_DISCONNECTED_KEY));
        verifyNoInteractions(publisher, provider);
    }

    @Test
    void anIntegrationBackBeforeTheChecksRunOutSettlesTheCommand() {
        // given: disconnected at the first attempt, reconnected for the second
        ShipmentResult result = new ShipmentResult("21480003",
                List.of(new ShipmentResult.ShipmentParcelResult("A", "dpd", null, true, null)), null);
        when(shippingProviders.forCommand(eq(store), any()))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.of(provider));
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.succeeded("cmd-1", result));
        checker.check(request(1));

        // when
        checker.check(request(2));

        // then
        verify(settler).succeeded(any(), eq(result));
        verify(settler, never()).failedWithKey(any(), any());
    }

    @Test
    void aFailureIsSettledWithTheProvidersWords() {
        // given
        when(provider.checkShipmentCreation("cmd-1", "21480003"))
                .thenReturn(ShipmentCreation.failed("cmd-1", "21480003", "Nieprawidłowy kod pocztowy"));

        // when
        checker.check(request(1));

        // then
        verify(settler).failed(any(), eq("Nieprawidłowy kod pocztowy"));
        verify(publisher, never()).publish(any());
    }

    @Test
    void aSuccessFoundByTheCommandCarriesThePackageIdIntoSettling() {
        // given: the provider answered the command without a package id, the check finds it
        ShipmentResult result = new ShipmentResult("21480009",
                List.of(new ShipmentResult.ShipmentParcelResult("A", "dpd", null, true, null)), null);
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.succeeded("cmd-1", result));

        // when
        checker.check(request(1));

        // then
        verify(settler).succeeded(argThat(r -> "21480009".equals(r.getExternalId())), eq(result));
    }

    @Test
    void aCheckWithoutProviderUsesDefaultIntegration() {
        // given: a message queued by the previous version has no provider field
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.pending("cmd-1", "21480003"));

        // when
        checker.check(request(1));

        // then
        verify(shippingProviders).forCommand(store, null);
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2));
    }

    @Test
    void aCheckGoesToTheIntegrationNamedInTheMessage() {
        // given
        ShippingProvider allegro = mock(ShippingProvider.class);
        ShipmentCreationCheckRequest request = request(1).toBuilder().provider("allegro").externalId(null).build();
        when(shippingProviders.forCommand(store, "allegro")).thenReturn(java.util.Optional.of(allegro));
        when(allegro.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.pending("cmd-1", null));

        // when
        checker.check(request);

        // then
        verify(allegro).checkShipmentCreation("cmd-1", null);
        verifyNoInteractions(provider);
    }

    @Test
    void checkWithoutExternalIdSettlesWithTheShipmentIdOfTheResult() {
        // given: Wysyłam z Allegro names its shipment only once the command succeeded
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder().storeId("store-1")
                .ownerType(ShipmentOwnerType.ORDER).ownerId("order-1").commandId("cmd-1").attempt(1).build();
        ShipmentResult result = new ShipmentResult("shp-9", List.of(
                new ShipmentResult.ShipmentParcelResult("WB-1", "DPD", null, true, null, false)), null);
        when(provider.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.succeeded("cmd-1", result));

        // when
        checker.check(request);

        // then
        verify(settler).succeeded(argThat(r -> "shp-9".equals(r.getExternalId())), eq(result));
    }

    @Test
    void pendingWithoutExternalIdIsAskedAgainWithoutOne() {
        // given
        ShipmentCreationCheckRequest request = ShipmentCreationCheckRequest.builder().storeId("store-1")
                .ownerType(ShipmentOwnerType.ORDER).ownerId("order-1").commandId("cmd-1").attempt(1).build();
        when(provider.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.pending("cmd-1", null));

        // when
        checker.check(request);

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2 && r.getExternalId() == null));
    }

    @Test
    void aPackageIdNamedWhilePendingIsRecordedAndCarriedToTheNextAttempt() {
        // given: Allegro answered SUCCESS before the waybill: the shipment exists, its number does not yet
        ShipmentCreationCheckRequest request = request(1).toBuilder().provider("allegro").externalId(null).build();
        when(shippingProviders.forCommand(store, "allegro")).thenReturn(java.util.Optional.of(provider));
        when(provider.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.pending("cmd-1", "shp-9"));

        // when
        checker.check(request);

        // then
        verify(owner).recordExternalId(argThat(r -> "shp-9".equals(r.getExternalId()) && "cmd-1".equals(r.getCommandId())));
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2 && "shp-9".equals(r.getExternalId())));
    }

    @Test
    void anUnconfirmedCommandKeepsThePackageIdTheProviderNamed() {
        // given: still no waybill at the last attempt
        ShipmentCreationCheckRequest request = request(ShipmentCreationChecker.MAX_ATTEMPTS).toBuilder()
                .provider("allegro").externalId(null).build();
        when(shippingProviders.forCommand(store, "allegro")).thenReturn(java.util.Optional.of(provider));
        when(provider.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.pending("cmd-1", "shp-9"));

        // when
        checker.check(request);

        // then: the row ends unconfirmed with the id of the shipment Allegro holds
        verify(owner).recordExternalId(argThat(r -> "shp-9".equals(r.getExternalId())));
        verify(settler).failedWithKey(argThat(r -> "shp-9".equals(r.getExternalId())), eq(ShipmentCreationState.UNCONFIRMED_KEY));
    }

    @Test
    void aPackageIdAlreadyKnownIsNotRecordedAgain() {
        // given
        when(provider.checkShipmentCreation("cmd-1", "21480003")).thenReturn(ShipmentCreation.pending("cmd-1", "21480003"));

        // when
        checker.check(request(2));

        // then
        verify(owner, never()).recordExternalId(any());
        verify(publisher).publish(argThat(r -> r.getAttempt() == 3));
    }

    @Test
    void aPackageIdThatCouldNotBeRecordedIsAskedForAgain() {
        // given
        ShipmentCreationCheckRequest request = request(1).toBuilder().externalId(null).build();
        when(provider.checkShipmentCreation("cmd-1", null)).thenReturn(ShipmentCreation.pending("cmd-1", "shp-9"));
        doThrow(new IllegalStateException("throttled")).when(owner).recordExternalId(any());

        // when
        checker.check(request);

        // then: the next attempt gets the id from the provider again and records it then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2 && r.getExternalId() == null));
    }
}
