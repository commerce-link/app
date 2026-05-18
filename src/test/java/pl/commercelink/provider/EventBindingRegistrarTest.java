package pl.commercelink.provider;

import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.QueueBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.ProviderField;

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
        AtomicReference<Object> capturedEvent = new AtomicReference<>();
        AtomicReference<String> capturedStoreId = new AtomicReference<>();
        AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(new TestDescriptor("stripe",
                        List.of(new WebhookBinding<>("stripe", String.class)))))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/", descriptor ->
                        (event, storeId, headers) -> {
                            capturedEvent.set(event);
                            capturedStoreId.set(storeId);
                            capturedHeaders.set(headers);
                        })
                .register();

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
        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(new TestDescriptor("stripe",
                        List.of(new WebhookBinding<>("stripe", String.class)))))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/", d ->
                        (event, storeId, headers) -> { })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/x/Webhooks/Payments/unknown");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isEmpty();
    }

    @Test
    void webhookHandlerThrowingExceptionPropagatesAsRuntime() {
        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(new TestDescriptor("paynow",
                        List.of(new WebhookBinding<>("paynow", String.class)))))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/", d ->
                        (event, storeId, headers) -> { throw new RuntimeException("provider boom"); })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Payments/paynow");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThatThrownBy(() -> routes.route(req).orElseThrow().handle(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process webhook from /Store/{storeId}/Webhooks/Payments/paynow");
    }

    @Test
    void queueBindingRegistersContainerWithCorrectQueueName() {
        SqsAsyncClient sqsClient = Mockito.mock(SqsAsyncClient.class);
        List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

        EventBindingRegistrar
                .forDescriptors(List.of(new TestDescriptor("pim",
                        List.of(new QueueBinding<>("pim-entry-added-queue", String.class)))))
                .withQueues(sqsClient, containers, event -> { })
                .register();

        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getQueueNames()).containsExactly("pim-entry-added-queue");
    }

    @Test
    void queueBindingWithoutWithQueuesThrowsIllegalState() {
        TestDescriptor descriptor = new TestDescriptor("pim",
                List.of(new QueueBinding<>("orphan-queue", String.class)));

        assertThatThrownBy(() -> EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .register())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan-queue");
    }

    @Test
    void webhookBindingWithoutWithWebhooksThrowsIllegalState() {
        TestDescriptor descriptor = new TestDescriptor("stripe",
                List.of(new WebhookBinding<>("orphan", String.class)));

        assertThatThrownBy(() -> EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .register())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan");
    }

    @Test
    void emptyDescriptorsReturnsNoopRouter() throws Exception {
        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.<TestDescriptor>of())
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/anything");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isEmpty();
    }

    @Test
    void registerPassesDescriptorToWebhookHandlerFactory() throws Exception {
        TestDescriptor stripe = new TestDescriptor("stripe", List.of(new WebhookBinding<>("stripe", String.class)));
        TestDescriptor paynow = new TestDescriptor("paynow", List.of(new WebhookBinding<>("paynow", String.class)));

        AtomicReference<String> capturedFromStripe = new AtomicReference<>();
        AtomicReference<String> capturedFromPaynow = new AtomicReference<>();

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(stripe, paynow))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/", descriptor ->
                        (event, storeId, headers) -> {
                            if (descriptor.name().equals("stripe")) capturedFromStripe.set((String) event);
                            else capturedFromPaynow.set((String) event);
                        })
                .register();

        invokePost(routes, "/Store/s/Webhooks/Payments/stripe", "from-stripe");
        invokePost(routes, "/Store/s/Webhooks/Payments/paynow", "from-paynow");

        assertThat(capturedFromStripe.get()).isEqualTo("from-stripe");
        assertThat(capturedFromPaynow.get()).isEqualTo("from-paynow");
    }

    @Test
    void mixedQueueAndWebhookBindingsRegisterBothAndReturnRealRouter() throws Exception {
        TestDescriptor pim = new TestDescriptor("pim", List.of(
                new QueueBinding<>("pim-entry-added-queue", String.class),
                new WebhookBinding<>("pim", String.class)));

        SqsAsyncClient sqsClient = Mockito.mock(SqsAsyncClient.class);
        List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(pim))
                .withQueues(sqsClient, containers, event -> { })
                .withWebhooks("", d -> (event, storeId, headers) -> { })
                .register();

        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getQueueNames()).containsExactly("pim-entry-added-queue");

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/pim");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);
        assertThat(routes.route(req)).isPresent();
    }

    private void invokePost(RouterFunction<ServerResponse> routes, String path, String body) throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest("POST", path);
        http.setContent(body.getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);
        routes.route(req).orElseThrow().handle(req);
    }

    private record TestDescriptor(String name, List<EventBinding<?>> bindings) implements ProviderDescriptor<Object> {
        @Override public String displayName() { return name; }
        @Override public List<ProviderField> configurationFields() { return List.of(); }
        @Override public Object create(Map<String, String> configuration) { return new Object(); }
    }
}
