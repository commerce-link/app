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
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventBindingRegistrarTest {

    private final List<HttpMessageConverter<?>> messageConverters = List.of(new StringHttpMessageConverter());

    @Test
    void webhookBindingExecutorReceivesRawPayloadHeadersAndProviderConfig() throws Exception {
        AtomicReference<String> capturedEvent = new AtomicReference<>();
        AtomicReference<WebhookContext> capturedContext = new AtomicReference<>();
        AtomicReference<String> capturedResult = new AtomicReference<>();
        AtomicReference<TestDescriptor> capturedDescriptor = new AtomicReference<>();
        AtomicReference<String> capturedStoreId = new AtomicReference<>();

        WebhookExecutor<String> executor = (event, ctx) -> {
            capturedEvent.set(event);
            capturedContext.set(ctx);
            return "executed:" + event;
        };

        TestDescriptor descriptor = new TestDescriptor("paynow",
                List.of(new WebhookBinding<>("paynow", executor)));

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, storeId) -> Map.of("signingSecret", "secret-for-" + storeId),
                        (d, storeId, result) -> {
                            capturedDescriptor.set((TestDescriptor) d);
                            capturedStoreId.set(storeId);
                            capturedResult.set((String) result);
                        })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/store-7/Webhooks/Payments/paynow");
        http.setContent("raw-body".getBytes());
        http.addHeader("Signature", "abc");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerResponse response = routes.route(req).orElseThrow().handle(req);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(capturedEvent.get()).isEqualTo("raw-body");
        assertThat(capturedContext.get().headers()).containsEntry("Signature", "abc");
        assertThat(capturedContext.get().providerConfig()).containsEntry("signingSecret", "secret-for-store-7");
        assertThat(capturedDescriptor.get()).isSameAs(descriptor);
        assertThat(capturedStoreId.get()).isEqualTo("store-7");
        assertThat(capturedResult.get()).isEqualTo("executed:raw-body");
    }

    @Test
    void webhookBindingReturningNullResultStillCompletesWithOk() throws Exception {
        WebhookExecutor<String> executor = (event, ctx) -> null;

        TestDescriptor descriptor = new TestDescriptor("paynow",
                List.of(new WebhookBinding<>("paynow", executor)));

        AtomicReference<Object> capturedResult = new AtomicReference<>("not-called");

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, storeId) -> Map.of(),
                        (d, storeId, result) -> capturedResult.set(result))
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Payments/paynow");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerResponse response = routes.route(req).orElseThrow().handle(req);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(capturedResult.get()).isEqualTo("not-called");
    }

    @Test
    void webhookBindingWithEmptyBodyReturns200WithoutCallingExecutor() throws Exception {
        AtomicReference<Boolean> executorCalled = new AtomicReference<>(false);
        WebhookExecutor<String> executor = (event, ctx) -> {
            executorCalled.set(true);
            throw new RuntimeException("should not be called");
        };

        TestDescriptor descriptor = new TestDescriptor("paynow",
                List.of(new WebhookBinding<>("paynow", executor)));

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, storeId) -> Map.of(),
                        (d, storeId, result) -> { })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Payments/paynow");
        http.setContent(new byte[0]);
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerResponse response = routes.route(req).orElseThrow().handle(req);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(executorCalled.get()).isFalse();
    }

    @Test
    void webhookBindingWithFailingConfigLoaderReturns200() throws Exception {
        AtomicReference<Boolean> executorCalled = new AtomicReference<>(false);
        WebhookExecutor<String> executor = (event, ctx) -> {
            executorCalled.set(true);
            return null;
        };

        TestDescriptor descriptor = new TestDescriptor("paynow",
                List.of(new WebhookBinding<>("paynow", executor)));

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, storeId) -> { throw new NullPointerException("store not found"); },
                        (d, storeId, result) -> { })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/unknown/Webhooks/Payments/paynow");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        ServerResponse response = routes.route(req).orElseThrow().handle(req);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(executorCalled.get()).isFalse();
    }

    @Test
    void webhookBindingExecutorExceptionIsWrappedInRuntime() {
        WebhookExecutor<String> executor = (event, ctx) -> {
            throw new RuntimeException("invalid signature");
        };

        TestDescriptor descriptor = new TestDescriptor("paynow",
                List.of(new WebhookBinding<>("paynow", executor)));

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, storeId) -> Map.of(),
                        (d, storeId, result) -> { })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/s/Webhooks/Payments/paynow");
        http.setContent("{}".getBytes());
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThatThrownBy(() -> routes.route(req).orElseThrow().handle(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process webhook from /Store/{storeId}/Webhooks/Payments/paynow");
    }

    @Test
    void webhookBindingDoesNotMatchUnregisteredPath() {
        WebhookExecutor<String> noop = (event, ctx) -> null;
        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(new TestDescriptor("stripe",
                        List.of(new WebhookBinding<>("stripe", noop)))))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, s) -> Map.of(), (d, s, r) -> { })
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("POST", "/Store/x/Webhooks/Payments/unknown");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isEmpty();
    }

    @Test
    void webhookBindingWithoutWithWebhooksThrowsIllegalState() {
        WebhookExecutor<String> noop = (event, ctx) -> null;
        TestDescriptor descriptor = new TestDescriptor("stripe",
                List.of(new WebhookBinding<>("orphan", noop)));

        assertThatThrownBy(() -> EventBindingRegistrar
                .forDescriptors(List.of(descriptor))
                .register())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orphan");
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
    void emptyDescriptorsReturnsNoopRouter() {
        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.<TestDescriptor>of())
                .register();

        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/anything");
        ServerRequest req = ServerRequest.create(http, messageConverters);

        assertThat(routes.route(req)).isEmpty();
    }

    @Test
    void registerDispatchesEachDescriptorToOwnExecutor() throws Exception {
        AtomicReference<String> capturedFromStripe = new AtomicReference<>();
        AtomicReference<String> capturedFromPaynow = new AtomicReference<>();

        TestDescriptor stripe = new TestDescriptor("stripe", List.of(
                new WebhookBinding<>("stripe",
                        (event, ctx) -> { capturedFromStripe.set(event); return null; })));
        TestDescriptor paynow = new TestDescriptor("paynow", List.of(
                new WebhookBinding<>("paynow",
                        (event, ctx) -> { capturedFromPaynow.set(event); return null; })));

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(stripe, paynow))
                .withWebhooks("/Store/{storeId}/Webhooks/Payments/",
                        (d, s) -> Map.of(), (d, s, r) -> { })
                .register();

        invokePost(routes, "/Store/s/Webhooks/Payments/stripe", "from-stripe");
        invokePost(routes, "/Store/s/Webhooks/Payments/paynow", "from-paynow");

        assertThat(capturedFromStripe.get()).isEqualTo("from-stripe");
        assertThat(capturedFromPaynow.get()).isEqualTo("from-paynow");
    }

    @Test
    void mixedQueueAndWebhookBindingsRegisterBothAndReturnRealRouter() {
        WebhookExecutor<String> noop = (event, ctx) -> null;
        TestDescriptor pim = new TestDescriptor("pim", List.of(
                new QueueBinding<>("pim-entry-added-queue", String.class),
                new WebhookBinding<>("pim", noop)));

        SqsAsyncClient sqsClient = Mockito.mock(SqsAsyncClient.class);
        List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

        RouterFunction<ServerResponse> routes = EventBindingRegistrar
                .forDescriptors(List.of(pim))
                .withQueues(sqsClient, containers, event -> { })
                .withWebhooks("", (d, s) -> Map.of(), (d, s, r) -> { })
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
