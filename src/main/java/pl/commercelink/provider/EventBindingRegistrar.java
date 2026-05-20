package pl.commercelink.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.SqsContainerOptions;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.QueueBinding;
import pl.commercelink.provider.api.EventBinding.WebhookBinding;
import pl.commercelink.provider.api.ProviderDescriptor;
import pl.commercelink.provider.api.WebhookContext;
import pl.commercelink.provider.api.WebhookExecutor;
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

    private static final Logger LOG = LoggerFactory.getLogger(EventBindingRegistrar.class);

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
                    LOG.info("Registered queue listener for provider '{}' on queue: {}", descriptor.name(), q.queueName());
                    containers.add(createSqsContainer(q));
                }
                case WebhookBinding<?, ?> w -> {
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

        @SuppressWarnings("unchecked")
        private <T, R> void addHttpRoute(RouterFunctions.Builder builder, WebhookBinding<T, R> binding, D descriptor) {
            WebhookExecutor<T, R> executor = binding.executor();
            String fullPath = webhookPathPrefix + binding.path();
            LOG.info("Registered webhook for provider '{}' at path: POST {}", descriptor.name(), fullPath);
            builder.POST(fullPath, request -> {
                try {
                    String body = request.body(String.class);
                    String storeId = tryPathVariable(request, "storeId");
                    Map<String, String> headers = request.headers().asHttpHeaders().toSingleValueMap();
                    T event = binding.eventType() == String.class
                            ? (T) body
                            : OBJECT_MAPPER.readValue(body, binding.eventType());
                    Map<String, String> providerConfig = webhookConfigLoader.apply(descriptor, storeId);
                    WebhookContext ctx = new WebhookContext(headers, providerConfig);
                    R result = executor.execute(event, ctx);
                    ((ResultHandler<D, R>) (ResultHandler<D, ?>) webhookResultHandler).handle(descriptor, storeId, result);
                    return ServerResponse.ok().build();
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to process webhook from " + fullPath, e);
                }
            });
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
