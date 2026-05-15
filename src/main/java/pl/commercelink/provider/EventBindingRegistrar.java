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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class EventBindingRegistrar {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;

    public EventBindingRegistrar(SqsAsyncClient sqsAsyncClient) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void register(EventBinding<?> binding,
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
                containers.add(createSqsContainer(q, queueDispatcher));
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

    private <T> SqsMessageListenerContainer<Object> createSqsContainer(
            QueueBinding<T> binding, Consumer<Object> dispatcher) {
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
                        : objectMapper.readValue((String) payload, binding.eventType());
                dispatcher.accept(event);
            } catch (Exception e) {
                throw new RuntimeException("Failed to process event from " + binding.queueName(), e);
            }
        });
        return container;
    }

    private <T> void addHttpRoute(RouterFunctions.Builder builder, String prefix,
                                  WebhookBinding<T> binding, WebhookHandler handler) {
        builder.POST(prefix + binding.path(), request -> {
            try {
                String body = request.body(String.class);
                String storeId = tryPathVariable(request, "storeId");
                Map<String, String> headers = request.headers().asHttpHeaders().toSingleValueMap();
                Object event = binding.eventType() == String.class
                        ? body
                        : objectMapper.readValue(body, binding.eventType());
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
