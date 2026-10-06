package pl.commercelink.shipping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.shipping.api.PickupOrder;
import pl.commercelink.shipping.api.ShippingException;
import pl.commercelink.shipping.api.ShippingProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShipmentPickupCheckerTest {

    @Mock private StoresRepository storesRepository;
    @Mock private ShippingProviderFactory shippingProviderFactory;
    @Mock private ShipmentPickupEventPublisher publisher;
    @Mock private ShipmentPickupSettler settler;
    @Mock private Store store;
    @Mock private ShippingProvider provider;

    private ShipmentPickupChecker checker;

    @BeforeEach
    void setUp() {
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(shippingProviderFactory.get(store)).thenReturn(provider);
        checker = new ShipmentPickupChecker(storesRepository, shippingProviderFactory, publisher, settler);
    }

    private static ShipmentPickupCheckRequest request(int attempt) {
        return ShipmentPickupCheckRequest.builder().storeId("store-1").provider("furgonetka").commandId("cmd-1")
                .targets(List.of(new PickupTarget(ShipmentOwnerType.ORDER, "order-1", "1", "TRK-1")))
                .date("2026-10-07").from("09:00").to("17:00").token("h").attempt(attempt).build();
    }

    @Test
    void pendingIsAskedAgainWithTheNextAttempt() {
        // given
        when(provider.checkPickupOrder("cmd-1")).thenReturn(PickupOrder.pending("cmd-1", List.of(), null));

        // when
        checker.check(request(1));

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2 && "cmd-1".equals(r.getCommandId())));
        verifyNoInteractions(settler);
    }

    @Test
    void theLastPendingAttemptFailsAsUnconfirmed() {
        // given
        when(provider.checkPickupOrder("cmd-1")).thenReturn(PickupOrder.pending("cmd-1", List.of(), null));

        // when
        checker.check(request(ShipmentPickupChecker.MAX_ATTEMPTS));

        // then
        verify(settler).failedWithKey(any(), eq("shipping.pickup.unconfirmed"));
        verify(publisher, never()).publish(any());
    }

    @Test
    void aSuccessIsSettledWithThePickupId() {
        // given
        when(provider.checkPickupOrder("cmd-1"))
                .thenReturn(PickupOrder.succeeded("cmd-1", "20261006800071", null, List.of("1")));

        // when
        checker.check(request(1));

        // then
        verify(settler).ordered(any(), eq("20261006800071"), eq(List.of("1")));
    }

    @Test
    void aSuccessNamingNoPackagesCoversAllOfThem() {
        // given
        when(provider.checkPickupOrder("cmd-1"))
                .thenReturn(PickupOrder.succeeded("cmd-1", "20261006800071", null, List.of()));

        // when
        checker.check(request(1));

        // then
        verify(settler).ordered(any(), eq("20261006800071"));
    }

    @Test
    void aFailureIsSettledWithItsReason() {
        // given
        when(provider.checkPickupOrder("cmd-1"))
                .thenReturn(PickupOrder.failed("cmd-1", List.of(), "Brak możliwości podjazdu"));

        // when
        checker.check(request(1));

        // then
        verify(settler).failed(any(), eq("Brak możliwości podjazdu"));
    }

    @Test
    void anErrorWhileCheckingCountsAsPending() {
        // given
        when(provider.checkPickupOrder("cmd-1")).thenThrow(new ShippingException("HTTP 502"));

        // when
        checker.check(request(1));

        // then
        verify(publisher).publish(argThat(r -> r.getAttempt() == 2));
    }

    @Test
    void missingProviderFailsThePickup() {
        // given
        when(shippingProviderFactory.get(store)).thenReturn(null);

        // when
        checker.check(request(1));

        // then
        verify(settler).failedWithKey(any(), eq("shipping.pickup.no.provider"));
        verify(publisher, never()).publish(any());
    }

    @Test
    void aDeletedStoreFailsThePickup() {
        // given
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when
        checker.check(request(1));

        // then
        verify(settler).failedWithKey(any(), eq("shipping.pickup.no.provider"));
    }
}
