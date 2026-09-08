package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.orders.rma.MarketplaceReturnAction;
import pl.commercelink.orders.rma.ReturnLifecycleEvent;
import pl.commercelink.orders.rma.ReturnLifecycleEventType;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceReturnLifecycleEventListenerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String EXTERNAL_ORDER_ID = "ext-1";
    private static final String MARKETPLACE = "Allegro";

    @Mock private StoresRepository storesRepository;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private Store store;
    @Mock private MarketplaceProvider provider;
    @Mock private MarketplaceReturns returns;

    @InjectMocks
    private MarketplaceReturnLifecycleEventListener listener;

    @BeforeEach
    void setUp() {
        MarketplaceIntegration integration = new MarketplaceIntegration(MARKETPLACE);
        integration.setLoggedIn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(integration);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        when(provider.returns()).thenReturn(Optional.of(returns));
    }

    private static ReturnLifecycleEvent event(ReturnLifecycleEventType type, MarketplaceReturnAction action) {
        return new ReturnLifecycleEvent(STORE_ID, ORDER_ID, EXTERNAL_ORDER_ID, MARKETPLACE, type, action);
    }

    private static MarketplaceReturnAction refundAction() {
        return new MarketplaceReturnAction("rma-1", "r-1",
                List.of(new MarketplaceReturnAction.Item("SKU-1", 2)), true, "cmd-1", null);
    }

    @Test
    void anAcceptedReturnIsRefundedThroughTheProvidersReturnsApi() {
        // when
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, refundAction()));

        // then
        ArgumentCaptor<ReturnRefund> captor = ArgumentCaptor.forClass(ReturnRefund.class);
        verify(returns).refundReturn(eq(EXTERNAL_ORDER_ID), eq("r-1"), captor.capture());
        assertEquals("cmd-1", captor.getValue().idempotencyKey());
        assertTrue(captor.getValue().refundDelivery());
        assertEquals("SKU-1", captor.getValue().items().get(0).offerKey());
        assertEquals(2, captor.getValue().items().get(0).quantity());
    }

    @Test
    void aRedeliveredRefundKeepsTheOriginalCommandId() {
        // given: SQS is at-least-once, so the same message can arrive twice
        ReturnLifecycleEvent redelivered = event(ReturnLifecycleEventType.ReturnAccepted, refundAction());

        // when
        listener.handleMessage(redelivered);
        listener.handleMessage(redelivered);

        // then: the payload's commandId is forwarded, never regenerated - Allegro deduplicates on it
        ArgumentCaptor<ReturnRefund> captor = ArgumentCaptor.forClass(ReturnRefund.class);
        verify(returns, times(2)).refundReturn(any(), any(), captor.capture());
        assertEquals("cmd-1", captor.getAllValues().get(0).idempotencyKey());
        assertEquals("cmd-1", captor.getAllValues().get(1).idempotencyKey());
    }

    @Test
    void aRejectedReturnIsRejectedWithItsReason() {
        // when
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnRejected,
                new MarketplaceReturnAction("rma-1", "r-1", List.of(), false, null, "Damaged")));

        // then
        ArgumentCaptor<ReturnRejection> captor = ArgumentCaptor.forClass(ReturnRejection.class);
        verify(returns).rejectReturn(eq("r-1"), captor.capture());
        assertEquals("Damaged", captor.getValue().reason());
    }

    @Test
    void theOrderIsNeverLoadedSoADeletedOrderStillRefunds() {
        // when: nothing in the flow reaches an orders repository; the event is self-describing
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, refundAction()));

        // then
        verify(returns).refundReturn(eq(EXTERNAL_ORDER_ID), eq("r-1"), any());
    }

    @Test
    void anIncompleteDecisionIsDroppedBeforeAnyMarketplaceCall() {
        // when
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, null));
        listener.handleMessage(event(null, refundAction()));
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted,
                new MarketplaceReturnAction("rma-1", null, List.of(), false, "cmd-1", null)));

        // then
        verifyNoInteractions(returns);
    }

    @Test
    void anAcceptedReturnWithNoExternalOrderIdIsDroppedInsteadOfRefundingWithANullOrderId() {
        // given: a decision recorded for a non-marketplace order (or one deserialized from a stale/corrupt
        // payload) carries no externalOrderId - refunding with a null or whitespace-only one must never
        // reach the marketplace
        ReturnLifecycleEvent nullExternalOrderId = new ReturnLifecycleEvent(STORE_ID, ORDER_ID, null, MARKETPLACE,
                ReturnLifecycleEventType.ReturnAccepted, refundAction());
        ReturnLifecycleEvent blankExternalOrderId = new ReturnLifecycleEvent(STORE_ID, ORDER_ID, "   ", MARKETPLACE,
                ReturnLifecycleEventType.ReturnAccepted, refundAction());

        // when
        listener.handleMessage(nullExternalOrderId);
        listener.handleMessage(blankExternalOrderId);

        // then
        verifyNoInteractions(returns);
    }

    @Test
    void aMarketplaceWithoutAReturnsApiDropsTheDecisionInsteadOfRetrying() {
        // given: retrying cannot fix a missing adapter capability, so this must not fill the DLQ
        when(provider.returns()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, refundAction()));

        // then
        verifyNoInteractions(returns);
    }

    @Test
    void aLoggedOutIntegrationThrowsSoSqsRetriesUntilTheStoreReauthenticates() {
        // given
        MarketplaceIntegration loggedOut = new MarketplaceIntegration(MARKETPLACE);
        loggedOut.setLoggedIn(false);
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(loggedOut);

        // when / then
        assertThrows(IllegalStateException.class,
                () -> listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, refundAction())));
    }

    @Test
    void aStoreWithoutThatIntegrationIsSkipped() {
        // given
        when(store.getMarketplaceIntegration(MARKETPLACE)).thenReturn(null);

        // when
        listener.handleMessage(event(ReturnLifecycleEventType.ReturnAccepted, refundAction()));

        // then
        verifyNoInteractions(returns);
    }
}
