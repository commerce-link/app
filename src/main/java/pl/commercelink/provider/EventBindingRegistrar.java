package pl.commercelink.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.SqsContainerOptions;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.QueueBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookOutcome;
import pl.commercelink.provider.api.WebhookStatusResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class EventBindingRegistrar {

    public static <D extends ProviderDescriptor<?>> Registration<D> forDescriptors(Collection<D> descriptors) {
        return new Registration<>(descriptors);
    }

    @FunctionalInterface
    public interface ResultHandler<D, R> {
        void handle(D descriptor, String storeId, R result);
    }

    public static final class Registration<D extends ProviderDescriptor<?>> {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        private final Collection<D> descriptors;
        private SqsAsyncClient sqsAsyncClient;
        private List<SqsMessageListenerContainer<?>> containers;
        private Consumer<Object> queueDispatcher;
        private String webhookPathPrefix;
        private BiFunction<D, String, Map<String, String>> webhookConfigLoader;
        private ResultHandler<D, Object> webhookResultHandler;

        private Registration(Collection<D> descriptors) {
            this.descriptors = Objects.requireNonNull(descriptors);
        }

        public Registration<D> withQueues(SqsAsyncClient sqsAsyncClient,
                                          List<SqsMessageListenerContainer<?>> containers,
                                          Consumer<Object> queueDispatcher) {
            this.sqsAsyncClient = Objects.requireNonNull(sqsAsyncClient);
            this.containers = Objects.requireNonNull(containers);
            this.queueDispatcher = Objects.requireNonNull(queueDispatcher);
            return this;
        }

        @SuppressWarnings("unchecked")
        public <R> Registration<D> withWebhooks(String pathPrefix,
                                                BiFunction<D, String, Map<String, String>> configLoader,
                                                ResultHandler<D, R> resultHandler) {
            this.webhookPathPrefix = Objects.requireNonNull(pathPrefix);
            this.webhookConfigLoader = Objects.requireNonNull(configLoader);
            this.webhookResultHandler = (ResultHandler<D, Object>) (ResultHandler<D, ?>) Objects.requireNonNull(resultHandler);
            return this;
        }

        public RouterFunction<ServerResponse> register() {
            RouterFunctions.Builder routes = RouterFunctions.route();
            for (D descriptor : descriptors) {
                for (EventBinding<?> binding : descriptor.bindings()) {
                    dispatch(binding, descriptor, routes);
                }
            }
            return buildOrEmpty(routes);
        }

        private void dispatch(EventBinding<?> binding, D descriptor, RouterFunctions.Builder routes) {
            switch (binding) {
                case QueueBinding<?> q -> {
                    if (sqsAsyncClient == null) {
                        throw new IllegalStateException(
                                "QueueBinding " + q.queueName() + " requires withQueues(...) to be called");
                    }
                    containers.add(createSqsContainer(q));
                }
                case WebhookBinding<?> w -> {
                    if (webhookConfigLoader == null || webhookResultHandler == null) {
                        throw new IllegalStateException(
                                "WebhookBinding " + w.path() + " requires withWebhooks(...) to be called");
                    }
                    addHttpRoute(routes, w, descriptor);
                }
            }
        }

        private <T> SqsMessageListenerContainer<Object> createSqsContainer(QueueBinding<T> binding) {
            SqsContainerOptions options = SqsContainerOptions.builder()
                    .maxConcurrentMessages(1)
                    .maxMessagesPerPoll(1)
                    .pollTimeout(Duration.ofSeconds(20))
                    .build();
            SqsMessageListenerContainer<Object> container =
                    new SqsMessageListenerContainer<>(sqsAsyncClient, options);
            container.setQueueNames(binding.queueName());
            container.setMessageListener(message -> {
                try {
                    Object payload = message.getPayload();
                    T event = binding.eventType().isInstance(payload)
                            ? binding.eventType().cast(payload)
                            : OBJECT_MAPPER.readValue((String) payload, binding.eventType());
                    queueDispatcher.accept(event);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process event from " + binding.queueName(), e);
                }
            });
            return container;
        }

        private <R> void addHttpRoute(RouterFunctions.Builder builder, WebhookBinding<R> binding, D descriptor) {
            builder.POST(webhookPathPrefix + binding.path(),
                    request -> handleWebhookRequest(request, binding, descriptor));
        }

        private <R> ServerResponse handleWebhookRequest(ServerRequest request, WebhookBinding<R> binding, D descriptor) {
            try {
                return processWebhook(request, binding, descriptor);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to process webhook from " + webhookPathPrefix + binding.path(), e);
            }
        }

        private static final WebhookStatusResponse STATUS_OK = new WebhookStatusResponse("OK");

        private <R> ServerResponse processWebhook(ServerRequest request, WebhookBinding<R> binding, D descriptor)
                throws Exception {
            String body = request.body(String.class);
            String storeId = tryPathVariable(request, "storeId");
            Map<String, String> providerConfig = loadProviderConfig(descriptor, storeId);
            if (providerConfig == null) {
                return ServerResponse.ok().body(STATUS_OK);
            }
            WebhookContext ctx = new WebhookContext(extractHeaders(request), providerConfig);
            WebhookOutcome<R> outcome = binding.executor().execute(body, ctx);
            if (outcome.result() != null) {
                dispatchResult(descriptor, storeId, outcome.result());
            }
            if (outcome.responseBody() != null) {
                return ServerResponse.ok().body(outcome.responseBody());
            }
            return ServerResponse.ok().build();
        }

        private Map<String, String> loadProviderConfig(D descriptor, String storeId) {
            try {
                Map<String, String> config = webhookConfigLoader.apply(descriptor, storeId);
                if (config == null) {
                    System.err.println("No provider configuration for webhook: provider="
                            + descriptor.name() + " store=" + storeId);
                }
                return config;
            } catch (Exception e) {
                System.err.println("Failed to load provider configuration for webhook: provider="
                        + descriptor.name() + " store=" + storeId + " error=" + e.getMessage());
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private <R> void dispatchResult(D descriptor, String storeId, R result) {
            ((ResultHandler<D, R>) (ResultHandler<D, ?>) webhookResultHandler).handle(descriptor, storeId, result);
        }

        private static Map<String, String> extractHeaders(ServerRequest request) {
            return request.headers().asHttpHeaders().toSingleValueMap();
        }

        private static String tryPathVariable(ServerRequest request, String name) {
            try {
                return request.pathVariable(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static RouterFunction<ServerResponse> buildOrEmpty(RouterFunctions.Builder builder) {
            try {
                return builder.build();
            } catch (IllegalStateException emptyBuilder) {
                return request -> Optional.empty();
            }
        }
    }
}
