package pl.commercelink.provider;

import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.QueueBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventBindingRegistrarTest {

    private final List<HttpMessageConverter<?>> messageConverters = List.of(new StringHttpMessageConverter());

    @Test
    void webhookBindingRoutesPostAndPassesRawPayloadHeadersAndStoreId() throws Exception {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        RouterFunctions.Builder builder = RouterFunctions.route();

        AtomicReference<Object> capturedEvent = new AtomicReference<>();
        AtomicReference<String> capturedStoreId = new AtomicReference<>();
        AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();

        registrar.register(
                new WebhookBinding<>("stripe", String.class),
                null,
                builder,
                "/Store/{storeId}/Webhooks/Payments/",
                null,
                (event, storeId, headers) -> {
                    capturedEvent.set(event);
                    capturedStoreId.set(storeId);
                    capturedHeaders.set(headers);
                });

        RouterFunction<ServerResponse> routes = builder.build();

        String rawPayload = "{\"id\":\"evt_123\",\"type\":\"payment_intent.succeeded\"}";
        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/store-abc/Webhooks/Payments/stripe");
        http.setContent(rawPayload.getBytes());
        http.addHeader("Stripe-Signature", "t=123,v1=sig");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerResponse response = routes.route(req).orElseThrow().handle(req);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(capturedEvent.get()).isEqualTo(rawPayload);
        assertThat(capturedStoreId.get()).isEqualTo("store-abc");
        assertThat(capturedHeaders.get()).containsEntry("Stripe-Signature", "t=123,v1=sig");
    }

    @Test
    void webhookBindingDoesNotMatchUnregisteredPath() throws Exception {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        RouterFunctions.Builder builder = RouterFunctions.route();
        registrar.register(
                new WebhookBinding<>("stripe", String.class),
                null, builder, "/Store/{storeId}/Webhooks/Payments/", null,
                (event, storeId, headers) -> { });
        RouterFunction<ServerResponse> routes = builder.build();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/x/Webhooks/Payments/unknown");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isEmpty();
    }

    @Test
    void webhookHandlerThrowingExceptionPropagatesAsRuntime() throws Exception {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        RouterFunctions.Builder builder = RouterFunctions.route();
        registrar.register(
                new WebhookBinding<>("paynow", String.class),
                null, builder, "/Store/{storeId}/Webhooks/Payments/", null,
                (event, storeId, headers) -> { throw new RuntimeException("provider boom"); });
        RouterFunction<ServerResponse> routes = builder.build();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Payments/paynow");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerRequest finalReq = req;
        assertThatThrownBy(() -> routes.route(finalReq).orElseThrow().handle(finalReq))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process webhook from /Store/{storeId}/Webhooks/Payments/paynow");
    }

    @Test
    void queueBindingRegistersContainerWithCorrectQueueName() {
        SqsAsyncClient sqsClient = Mockito.mock(SqsAsyncClient.class);
        EventBindingRegistrar registrar = new EventBindingRegistrar(sqsClient);
        List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

        registrar.register(
                new QueueBinding<>("pim-entry-added-queue", String.class),
                containers,
                null,
                "",
                event -> { },
                null);

        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getQueueNames()).containsExactly("pim-entry-added-queue");
    }

    @Test
    void queueBindingWithoutContainerListThrowsIllegalState() {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        EventBinding<?> binding = new QueueBinding<>("orphan-queue", String.class);

        assertThatThrownBy(() -> registrar.register(binding, null, null, "", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan-queue");
    }

    @Test
    void webhookBindingWithoutRoutesBuilderThrowsIllegalState() {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        EventBinding<?> binding = new WebhookBinding<>("orphan", String.class);

        assertThatThrownBy(() -> registrar.register(binding, null, null, "", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan");
    }

    @Test
    void buildOrEmptyReturnsNoopRouterWhenBuilderHasNoRoutes() throws Exception {
        RouterFunction<ServerResponse> empty = EventBindingRegistrar.buildOrEmpty(RouterFunctions.route());

        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/anything");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(empty.route(req)).isEmpty();
    }

    @Test
    void buildOrEmptyReturnsRealRouterWhenBuilderHasRoutes() throws Exception {
        EventBindingRegistrar registrar = new EventBindingRegistrar(null);
        RouterFunctions.Builder builder = RouterFunctions.route();
        registrar.register(
                new WebhookBinding<>("furgonetka", String.class),
                null, builder, "/Store/{storeId}/Webhooks/Shipping/", null,
                (event, storeId, headers) -> { });

        RouterFunction<ServerResponse> routes = EventBindingRegistrar.buildOrEmpty(builder);

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Shipping/furgonetka");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isPresent();
    }
}
