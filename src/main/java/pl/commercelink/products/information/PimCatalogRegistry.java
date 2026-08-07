package pl.commercelink.products.information;

import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
import java.util.ServiceLoader;

@Configuration
public class PimCatalogRegistry {

    private final PimCatalog catalog;
    private final List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    PimCatalogRegistry(SqsAsyncClient sqsAsyncClient, ProductRepository productRepository,
                       SecretsManager secretsManager, @Lazy TaxonomyCategoryEnrichment taxonomyCategoryEnrichment) {

        PimCatalogDescriptor descriptor = resolveDescriptor(
                ServiceLoader.load(PimCatalogDescriptor.class).stream().map(ServiceLoader.Provider::get).toList());

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

    static PimCatalogDescriptor resolveDescriptor(List<PimCatalogDescriptor> descriptors) {
        if (descriptors.isEmpty()) {
            throw new IllegalStateException(
                    "No PimCatalogDescriptor found on classpath — build with -Pdev (pim-dev) or add a PIM adapter to the pom");
        }
        if (descriptors.size() > 1) {
            throw new IllegalStateException("Multiple PimCatalogDescriptors found on classpath: "
                    + descriptors.stream().map(PimCatalogDescriptor::name).toList()
                    + " — exactly one PIM adapter is expected");
        }
        return descriptors.get(0);
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
