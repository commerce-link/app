package pl.commercelink.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrdersImportEventListenerTest {

    private static final String MARKETPLACE = "Allegro";

    @Mock private StoresRepository storesRepository;
    @Mock private MarketplaceOrderImporter marketplaceOrderImporter;
    @Mock private MarketplaceReturnImporter marketplaceReturnImporter;
    @Mock private MarketplaceProviderFactory providerFactory;
    @Mock private Store store;
    @Mock private MarketplaceProvider provider;
    @Mock private MarketplaceReturns returns;

    @InjectMocks
    private MarketplaceOrdersImportEventListener listener;

    private final MarketplaceReturn aReturn = new MarketplaceReturn("r-1", "cf-1", null,
            MarketplaceReturnStatus.DECLARED, LocalDateTime.now(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(store.hasActiveMarketplaceIntegration(MARKETPLACE)).thenReturn(true);
        when(providerFactory.get(store, MARKETPLACE)).thenReturn(provider);
        when(provider.fetchOrders()).thenReturn(List.of(mock(MarketplaceOrder.class)));
        when(provider.returns()).thenReturn(Optional.of(returns));
        when(returns.fetchReturns()).thenReturn(List.of(aReturn));
    }

    // Deserialized through the real Jackson ObjectMapper (the same path the SQS/scheduler
    // message travels in production), not ReflectionTestUtils: that only proves the branch
    // logic below, never that `scope` actually arrives from real JSON input.
    private static MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload(String scope) throws Exception {
        String json = scope == null
                ? "{\"marketplace\":\"" + MARKETPLACE + "\"}"
                : "{\"marketplace\":\"" + MARKETPLACE + "\",\"scope\":\"" + scope + "\"}";
        return new ObjectMapper().readValue(json, MarketplaceOrdersImportEventListener.MarketplaceOrderPayload.class);
    }

    @Test
    void schedulerPayloadCarriesTheReturnsScope() throws Exception {
        // given: exactly the input configured on the EventBridge schedule
        String input = "{\"marketplace\":\"Allegro\",\"scope\":\"returns\"}";

        // when
        MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload =
                new ObjectMapper().readValue(input, MarketplaceOrdersImportEventListener.MarketplaceOrderPayload.class);

        // then
        assertEquals("Allegro", payload.getMarketplace());
        assertEquals("returns", payload.getScope());
    }

    @Test
    void payloadWithoutScopeImportsOrdersOnly() throws Exception {
        // when
        listener.handleMessage(payload(null));

        // then
        verify(marketplaceOrderImporter).importOrder(eq(store), eq(MARKETPLACE), any());
        verify(store).updateLastFetchedAt(MARKETPLACE);
        verifyNoInteractions(marketplaceReturnImporter);
        verify(provider, never()).returns();
    }

    @Test
    void returnsScopeImportsReturnsOnly() throws Exception {
        // when
        listener.handleMessage(payload("returns"));

        // then
        verify(marketplaceReturnImporter).importReturn(store, MARKETPLACE, aReturn);
        verifyNoInteractions(marketplaceOrderImporter);
        verify(store, never()).updateLastFetchedAt(any());
    }

    @Test
    void returnsScopeIsSkippedWhenProviderHasNoReturns() throws Exception {
        // given
        when(provider.returns()).thenReturn(Optional.empty());

        // when
        listener.handleMessage(payload("returns"));

        // then
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void ordersScopeImportsOrdersOnly() throws Exception {
        // when
        listener.handleMessage(payload("orders"));

        // then
        verify(marketplaceOrderImporter).importOrder(eq(store), eq(MARKETPLACE), any());
        verifyNoInteractions(marketplaceReturnImporter);
    }

    @Test
    void unknownScopeIsIgnoredRatherThanFallingBackToAFullOrdersImport() throws Exception {
        // when: a rollback might send a scope this build no longer recognises
        listener.handleMessage(payload("legacy-unknown-scope"));

        // then: fail closed instead of silently re-importing all orders
        verifyNoInteractions(marketplaceOrderImporter);
        verifyNoInteractions(marketplaceReturnImporter);
        verify(store, never()).updateLastFetchedAt(any());
    }

    @Test
    void returnsScopeIsSkippedWhenReturnsAreDisabled() throws Exception {
        // given
        ReflectionTestUtils.setField(listener, "returnsEnabled", false);
        MarketplaceOrdersImportEventListener.MarketplaceOrderPayload payload = new ObjectMapper().readValue(
                "{\"marketplace\":\"Allegro\",\"scope\":\"returns\"}",
                MarketplaceOrdersImportEventListener.MarketplaceOrderPayload.class);

        // when
        listener.handleMessage(payload);

        // then
        verify(returns, never()).fetchReturns();
        verifyNoInteractions(marketplaceReturnImporter);
    }
}
