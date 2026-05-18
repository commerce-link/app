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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class EventBindingRegistrar {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EventBindingRegistrar() {
    }

    public static <D extends ProviderDescriptor<?>> void registerAll(
            Collection<D> descriptors,
            SqsAsyncClient sqsAsyncClient,
            List<SqsMessageListenerContainer<?>> containers,
            RouterFunctions.Builder routes,
            String httpPathPrefix,
            Consumer<Object> queueDispatcher,
            Function<D, WebhookHandler> webhookHandlerFn) {
        for (D descriptor : descriptors) {
            WebhookHandler handler = webhookHandlerFn != null ? webhookHandlerFn.apply(descriptor) : null;
            for (EventBinding<?> binding : descriptor.bindings()) {
                register(binding, sqsAsyncClient, containers, routes, httpPathPrefix, queueDispatcher, handler);
            }
        }
    }

    public static <D extends ProviderDescriptor<?>> RouterFunction<ServerResponse> buildWebhookRoutes(
            Collection<D> descriptors,
            String httpPathPrefix,
            Function<D, WebhookHandler> webhookHandlerFn) {
        RouterFunctions.Builder builder = RouterFunctions.route();
        registerAll(descriptors, null, null, builder, httpPathPrefix, null, webhookHandlerFn);
        return buildOrEmpty(builder);
    }

    public static void register(EventBinding<?> binding,
                                SqsAsyncClient sqsAsyncClient,
                                List<SqsMessageListenerContainer<?>> containers,
                                RouterFunctions.Builder routes,
                                String httpPathPrefix,
                                Consumer<Object> queueDispatcher,
                                WebhookHandler webhookHandler) {
        switch (binding) {
            case QueueBinding<?> q -> {
                if (containers == null || queueDispatcher == null) {
                    throw new IllegalStateException(
                            "QueueBinding " + q.queueName() + " requires container list and dispatcher");
                }
                containers.add(createSqsContainer(q, sqsAsyncClient, queueDispatcher));
            }
            case WebhookBinding<?> w -> {
                if (routes == null || webhookHandler == null) {
                    throw new IllegalStateException(
                            "WebhookBinding " + w.path() + " requires routes builder and handler");
                }
                addHttpRoute(routes, httpPathPrefix, w, webhookHandler);
            }
        }
    }

    private static <T> SqsMessageListenerContainer<Object> createSqsContainer(
            QueueBinding<T> binding, SqsAsyncClient sqsAsyncClient, Consumer<Object> dispatcher) {
        if (sqsAsyncClient == null) {
            throw new IllegalStateException(
                    "QueueBinding " + binding.queueName() + " requires non-null SqsAsyncClient");
        }
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
                dispatcher.accept(event);
            } catch (Exception e) {
                throw new RuntimeException("Failed to process event from " + binding.queueName(), e);
            }
        });
        return container;
    }

    private static <T> void addHttpRoute(RouterFunctions.Builder builder, String prefix,
                                         WebhookBinding<T> binding, WebhookHandler handler) {
        builder.POST(prefix + binding.path(), request -> {
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
                        "Failed to process webhook from " + prefix + binding.path(), e);
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

    public static RouterFunction<ServerResponse> buildOrEmpty(RouterFunctions.Builder builder) {
        try {
            return builder.build();
        } catch (IllegalStateException emptyBuilder) {
            return request -> Optional.empty();
        }
    }
}
