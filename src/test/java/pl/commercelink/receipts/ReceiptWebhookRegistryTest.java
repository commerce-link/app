package pl.commercelink.receipts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.ProviderCallLimiter;
import pl.commercelink.receipts.api.Receipt;
import pl.commercelink.receipts.api.ReceiptState;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptWebhookRegistryTest {

    private static final String STORE_ID = "s1";

    @Mock private ReceiptProviderFactory providerFactory;
    @Mock private StoresRepository storesRepository;
    @Mock private ReceiptStatusUpdates updates;
    @Mock private Store store;

    private final List<HttpMessageConverter<?>> messageConverters = List.of(new StringHttpMessageConverter());
    private final ProviderCallLimiter limiter = ProviderCallLimiter.unlimited();

    private RouterFunction<ServerResponse> routes;

    @BeforeEach
    void setUp() {
        when(providerFactory.availableProviders()).thenReturn(List.of(new FakeReceiptProviderDescriptor()));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(providerFactory.loadConfiguration(store, FakeReceiptProviderDescriptor.NAME))
                .thenReturn(Map.of("token", FakeReceiptProviderDescriptor.TOKEN));

        ReceiptWebhookRegistry registry = new ReceiptWebhookRegistry(providerFactory, storesRepository, updates, limiter);
        routes = registry.receiptWebhookRoutes();
    }

    private ServerResponse post(String path, String body) throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest("POST", path);
        http.setContent(body.getBytes());
        ServerRequest request = ServerRequest.create(http, messageConverters);
        return routes.route(request).orElseThrow().handle(request);
    }

    @Test
    void authenticCallIsAppliedAndAnswered200() throws Exception {
        // when
        ServerResponse response = post("/Store/s1/Webhooks/Receipts/test", "o1:R1;p1;FISCALISED");

        // then
        assertThat(response.statusCode().value()).isEqualTo(200);
        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(updates).applyPushed(eq(STORE_ID), eq(FakeReceiptProviderDescriptor.NAME), captor.capture());
        assertThat(captor.getValue().receiptKey()).isEqualTo("o1:R1");
        assertThat(captor.getValue().providerReceiptId()).isEqualTo("p1");
        assertThat(captor.getValue().state()).isEqualTo(ReceiptState.FISCALISED);
    }

    @Test
    void wrongTokenIsRejectedWith401AndNeverApplied() throws Exception {
        // given
        when(providerFactory.loadConfiguration(store, FakeReceiptProviderDescriptor.NAME))
                .thenReturn(Map.of("token", "wrong-token"));

        // when
        ServerResponse response = post("/Store/s1/Webhooks/Receipts/test", "o1:R1;p1;FISCALISED");

        // then
        assertThat(response.statusCode().value()).isEqualTo(401);
        verifyNoInteractions(updates);
    }

    @Test
    void blankBodyIsAcceptedAndAnswered200() throws Exception {
        // when: the fake descriptor answers a blank body with WebhookOutcome.empty()
        ServerResponse response = post("/Store/s1/Webhooks/Receipts/test", "");

        // then
        assertThat(response.statusCode().value()).isEqualTo(200);
        verifyNoInteractions(updates);
    }

    @Test
    void whenApplyPushedThrowsTheWebhookStillAnswers200() throws Exception {
        // given: defense in depth — applyPushed itself never throws in production, but the webhook handler must
        // survive it regardless, or Fakturownia retries an authentic call 25 times and disables the account
        doThrow(new RuntimeException("dynamo down")).when(updates).applyPushed(any(), any(), any());

        // when
        ServerResponse response = post("/Store/s1/Webhooks/Receipts/test", "o1:R1;p1;FISCALISED");

        // then
        assertThat(response.statusCode().value()).isEqualTo(200);
    }

    @Test
    void storeWithoutProviderConfigurationIsAnswered200() throws Exception {
        // given: the loader (config manager) has nothing for this store/provider pair
        when(providerFactory.loadConfiguration(store, FakeReceiptProviderDescriptor.NAME)).thenReturn(null);

        // when
        ServerResponse response = post("/Store/s1/Webhooks/Receipts/test", "o1:R1;p1;FISCALISED");

        // then
        assertThat(response.statusCode().value()).isEqualTo(200);
        verifyNoInteractions(updates);
    }
}
