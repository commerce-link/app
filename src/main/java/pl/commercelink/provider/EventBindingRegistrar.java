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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EventBindingRegistrar {

    public static <D extends ProviderDescriptor<?>> Registration<D> forDescriptors(Collection<D> descriptors) {
        return new Registration<>(descriptors);
    }

    public static final class Registration<D extends ProviderDescriptor<?>> {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        private final Collection<D> descriptors;
        private SqsAsyncClient sqsAsyncClient;
        private List<SqsMessageListenerContainer<?>> containers;
        private Consumer<Object> queueDispatcher;
        private String webhookPathPrefix;
        private Function<D, WebhookHandler> webhookHandlerFn;

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

        public Registration<D> withWebhooks(String pathPrefix,
                                            Function<D, WebhookHandler> webhookHandlerFn) {
            this.webhookPathPrefix = Objects.requireNonNull(pathPrefix);
            this.webhookHandlerFn = Objects.requireNonNull(webhookHandlerFn);
            return this;
        }

        public RouterFunction<ServerResponse> register() {
            RouterFunctions.Builder routes = RouterFunctions.route();
            for (D descriptor : descriptors) {
                WebhookHandler handler = webhookHandlerFn != null ? webhookHandlerFn.apply(descriptor) : null;
                for (EventBinding<?> binding : descriptor.bindings()) {
                    dispatch(binding, handler, routes);
                }
            }
            return buildOrEmpty(routes);
        }

        private void dispatch(EventBinding<?> binding, WebhookHandler handler, RouterFunctions.Builder routes) {
            switch (binding) {
                case QueueBinding<?> q -> {
                    if (sqsAsyncClient == null) {
                        throw new IllegalStateException(
                                "QueueBinding " + q.queueName() + " requires withQueues(...) to be called");
                    }
                    containers.add(createSqsContainer(q));
                }
                case WebhookBinding<?> w -> {
                    if (webhookHandlerFn == null) {
                        throw new IllegalStateException(
                                "WebhookBinding " + w.path() + " requires withWebhooks(...) to be called");
                    }
                    addHttpRoute(routes, w, handler);
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

        private <T> void addHttpRoute(RouterFunctions.Builder builder, WebhookBinding<T> binding, WebhookHandler handler) {
            builder.POST(webhookPathPrefix + binding.path(), request -> {
                try {
                    String body = request.body(String.class);
                    String storeId = tryPathVariable(request, "storeId");
                    Map<String, String> headers = request.headers().asHttpHeaders().toSingleValueMap();
                    Object event = binding.eventType() == String.class
                            ? body
                            : OBJECT_MAPPER.readValue(body, binding.eventType());
                    handler.handle(event, storeId, headers);
                    return ServerResponse.ok().build();
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to process webhook from " + webhookPathPrefix + binding.path(), e);
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
