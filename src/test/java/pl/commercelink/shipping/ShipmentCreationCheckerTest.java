package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
    void missingProviderFailsTheCreation() {
        // given
        when(shippingProviders.forCommand(eq(store), any())).thenReturn(java.util.Optional.empty());

        // when
        checker.check(request(1));

        // then
        verify(settler).failedWithKey(any(), eq("shipping.creation.no.provider"));
        verifyNoInteractions(publisher);
    }

    @Test
    void checkerWithDisconnectedIntegrationFailsTheCreationReadably() {
        // given: the store dropped Wysyłam z Allegro while the command was queued
        when(shippingProviders.forCommand(store, "allegro")).thenReturn(java.util.Optional.empty());

        // when
        checker.check(request(3).toBuilder().provider("allegro").build());

        // then: settled once with the readable key, nothing re-queued, nothing thrown (no DLQ loop)
        verify(settler).failedWithKey(any(), eq(ShipmentCreationChecker.NO_PROVIDER_KEY));
        verifyNoInteractions(publisher, provider);
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
}
