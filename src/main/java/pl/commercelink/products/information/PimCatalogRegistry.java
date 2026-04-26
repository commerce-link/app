package pl.commercelink.products.information;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.listener.SqsContainerOptions;
import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.provider.api.EventBinding.QueueBinding;
import pl.commercelink.starter.secrets.SecretsManager;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;
import java.util.*;

@Configuration
public class PimCatalogRegistry {

    private final PimCatalog catalog;
    private final List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    PimCatalogRegistry(SqsAsyncClient sqsAsyncClient, ProductRepository productRepository,
                       SecretsManager secretsManager) {

        Optional<PimCatalogDescriptor> descriptorOpt = ServiceLoader.load(PimCatalogDescriptor.class).findFirst();

        if (descriptorOpt.isEmpty()) {
            System.err.println("No PimCatalogDescriptor found on classpath — using empty PimCatalog");
            this.catalog = new EmptyPimCatalog();
            return;
        }

        PimCatalogDescriptor descriptor = descriptorOpt.get();

        Map<String, String> configuration = new HashMap<>();
        if (secretsManager.exists(descriptor.name())) {
            configuration.putAll(secretsManager.getSecret(descriptor.name(), Map.class));
        }

        this.catalog = descriptor.create(configuration, Map.of("sqsAsyncClient", sqsAsyncClient));

        catalog.onEntryAdded(event ->
                productRepository.findAllWithoutPimIdByEanOrMfn(event.eans(), event.mfnCodes())
                        .forEach(product -> {
                            product.setPimId(event.pimId());
                            productRepository.save(product);
                        }));

        catalog.onEntryDeleted(event ->
                productRepository.detachPimFromProducts(event.pimId()));

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        for (EventBinding<?> binding : descriptor.bindings()) {
            if (binding instanceof QueueBinding<?> queueBinding) {
                containers.add(createContainer(sqsAsyncClient, objectMapper, queueBinding));
            }
        }
    }

    private <T> SqsMessageListenerContainer<Object> createContainer(
            SqsAsyncClient sqsAsyncClient, ObjectMapper objectMapper, QueueBinding<T> binding) {
        SqsContainerOptions options = SqsContainerOptions.builder()
                .maxConcurrentMessages(1)
                .maxMessagesPerPoll(1)
                .pollTimeout(Duration.ofSeconds(20))
                .build();
        SqsMessageListenerContainer<Object> container = new SqsMessageListenerContainer<>(sqsAsyncClient, options);
        container.setQueueNames(binding.queueName());
        container.setMessageListener(message -> {
            try {
                Object payload = message.getPayload();
                T event;
                if (binding.eventType().isInstance(payload)) {
                    event = binding.eventType().cast(payload);
                } else {
                    event = objectMapper.readValue((String) payload, binding.eventType());
                }
                catalog.dispatch(event);
            } catch (Exception e) {
                throw new RuntimeException("Failed to process event from " + binding.queueName(), e);
            }
        });
        return container;
    }

    @Bean
    PimCatalog pimCatalog() {
        return catalog;
    }

    @PostConstruct
    void start() {
        catalog.refresh();
        containers.forEach(SqsMessageListenerContainer::start);
    }

    @PreDestroy
    void stop() {
        containers.forEach(SqsMessageListenerContainer::stop);
    }

    @Scheduled(cron = "0 5 * * * ?")
    void refresh() {
        catalog.refresh();
    }
}
