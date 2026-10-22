package pl.commercelink.products.information;

import io.awspring.cloud.sqs.listener.SqsMessageListenerContainer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCatalogDescriptor;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PIMEntryDeletedEvent;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalogRepository;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Configuration
public class PimCatalogRegistry {

    private final PimCatalog catalog;
    private final List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

    // One thread: refreshes never overlap. refreshPending is true while a refresh is queued but not yet started,
    // so a burst of events yields at most one running and one queued refresh.
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pim-index-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);

    /** Test seam: wires the entry handlers onto an already built catalog. */
    PimCatalogRegistry(PimCatalog catalog, ProductRepository productRepository,
                       ProductCatalogRepository productCatalogRepository) {
        this.catalog = catalog;
        registerEntryHandlers(productRepository, productCatalogRepository);
    }

    @Autowired
    @SuppressWarnings("unchecked")
    PimCatalogRegistry(SqsAsyncClient sqsAsyncClient, ProductRepository productRepository,
                       ProductCatalogRepository productCatalogRepository,
                       SecretsManager secretsManager, @Lazy TaxonomyCategoryEnrichment taxonomyCategoryEnrichment) {

        PimCatalogDescriptor descriptor = resolveDescriptor(
                ServiceLoader.load(PimCatalogDescriptor.class).stream().map(ServiceLoader.Provider::get).toList());

        Map<String, String> configuration = new HashMap<>();
        if (secretsManager.exists(descriptor.name())) {
            configuration.putAll(secretsManager.getSecret(descriptor.name(), Map.class));
        }

        this.catalog = descriptor.create(configuration, Map.of("sqsAsyncClient", sqsAsyncClient));

        registerEntryHandlers(productRepository, productCatalogRepository);

        catalog.onCategoryMatched(taxonomyCategoryEnrichment::applyMatch);

        EventBindingRegistrar.forDescriptors(List.of(descriptor))
                .withQueues(sqsAsyncClient, containers, catalog::dispatch)
                .register();
    }

    private void registerEntryHandlers(ProductRepository productRepository,
                                       ProductCatalogRepository productCatalogRepository) {
        StoreEntryPinning storeEntryPinning = new StoreEntryPinning(productRepository, productCatalogRepository);
        catalog.onEntryAdded(event -> onEntryAdded(event, storeEntryPinning, productRepository, productCatalogRepository));
        catalog.onEntryDeleted(event -> onEntryDeleted(event, storeEntryPinning));
    }

    private void onEntryAdded(PIMEntryAddedEvent event, StoreEntryPinning storeEntryPinning,
                              ProductRepository productRepository, ProductCatalogRepository productCatalogRepository) {
        if (event.ownerStoreId() == null) {
            attachEntryToProducts(event, productRepository, productCatalogRepository);
        } else {
            storeEntryPinning.onStoreEntryAnnounced(event, catalog);
        }
        // the in-memory index must learn about the entry; only after pinning, so the pinning still reads the codes
        // before the change and lookups see a consistent state
        requestRefresh();
    }

    private void onEntryDeleted(PIMEntryDeletedEvent event, StoreEntryPinning storeEntryPinning) {
        storeEntryPinning.onEntryDeleted(event.pimId(), catalog);
        requestRefresh();
    }

    /**
     * Asks for an index refresh in the background. Coalescing: at most one refresh runs and one waits;
     * failures are logged and never reach the caller.
     */
    void requestRefresh() {
        if (!refreshPending.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshExecutor.execute(() -> {
                refreshPending.set(false);
                try {
                    catalog.refresh();
                } catch (Exception e) {
                    log.error("PIM index refresh failed", e);
                }
            });
        } catch (RuntimeException e) {
            // executor already shut down
            refreshPending.set(false);
            log.warn("PIM index refresh not scheduled: {}", e.getMessage());
        }
    }

    static void attachEntryToProducts(PIMEntryAddedEvent event, ProductRepository productRepository,
                                      ProductCatalogRepository productCatalogRepository) {
        List<Product> products = event.ownerStoreId() == null
                ? productRepository.findAllWithoutPimIdByEanOrMfn(event.eans(), event.mfnCodes())
                // a store's own entry only fills products of that store
                : productRepository.findAllWithoutPimIdByEanOrMfnInCategories(
                        categoryIdsOf(event.ownerStoreId(), productCatalogRepository), event.eans(), event.mfnCodes());
        products.forEach(product -> {
            product.setPimId(event.pimId());
            productRepository.save(product);
        });
    }

    static List<String> categoryIdsOf(String storeId, ProductCatalogRepository productCatalogRepository) {
        return productCatalogRepository.findAll(storeId).stream()
                .flatMap(catalog -> catalog.getCategories().stream())
                .map(CategoryDefinition::getCategoryId)
                .distinct()
                .toList();
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
        refreshExecutor.shutdownNow();
    }

    // every instance refreshes on its own: an SQS event reaches only one of the app instances
    @Scheduled(cron = "0 */5 * * * ?")
    void refresh() {
        requestRefresh();
    }
}
