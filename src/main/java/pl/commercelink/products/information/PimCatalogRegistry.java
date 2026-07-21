package pl.commercelink.products.information;

import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

@Configuration
public class PimCatalogRegistry {

    private final PimCatalog catalog;
    private final List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    PimCatalogRegistry(SqsAsyncClient sqsAsyncClient, ProductRepository productRepository,
                       SecretsManager secretsManager, TaxonomyCategoryEnrichment taxonomyCategoryEnrichment) {

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

        catalog.onCategoryMatched(taxonomyCategoryEnrichment::applyMatch);

        EventBindingRegistrar.forDescriptors(List.of(descriptor))
                .withQueues(sqsAsyncClient, containers, catalog::dispatch)
                .register();
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
