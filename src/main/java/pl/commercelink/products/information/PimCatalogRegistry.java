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
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.starter.secrets.SecretsManager;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCategoryEnrichment;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Configuration
public class PimCatalogRegistry {

    private final PimCatalog catalog;
    private final List<SqsMessageListenerContainer<?>> containers = new ArrayList<>();

    // One thread: background refreshes never overlap one another. refreshPending is true while a refresh is queued
    // but not yet started, so a burst of events yields at most one running and one queued refresh. The clone
    // announcement is the exception: it refreshes on the SQS listener thread (StoreEntryPinning.onCloneAnnounced),
    // so that refresh may run alongside a background one.
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pim-index-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final Supplier<List<String>> storeIds;
    private final StoreEntryCodeHistory codeHistory;

    /** Test seam: wires the entry handlers onto an already built catalog, with no stores and no code history. */
    PimCatalogRegistry(PimCatalog catalog, ProductRepository productRepository,
                       ProductCatalogRepository productCatalogRepository) {
        this(catalog, productRepository, productCatalogRepository, List::of, new StoreEntryCodeHistory(Clock.systemUTC()));
    }

    /** Test seam: as above, with the stores whose entries the code history records before each refresh. */
    PimCatalogRegistry(PimCatalog catalog, ProductRepository productRepository,
                       ProductCatalogRepository productCatalogRepository, Supplier<List<String>> storeIds,
                       StoreEntryCodeHistory codeHistory) {
        this.catalog = catalog;
        this.storeIds = storeIds;
        this.codeHistory = codeHistory;
        registerEntryHandlers(productRepository, productCatalogRepository);
    }

    @Autowired
    @SuppressWarnings("unchecked")
    PimCatalogRegistry(SqsAsyncClient sqsAsyncClient, ProductRepository productRepository,
                       ProductCatalogRepository productCatalogRepository,
                       SecretsManager secretsManager, @Lazy TaxonomyCategoryEnrichment taxonomyCategoryEnrichment,
                       StoresRepository storesRepository) {

        PimCatalogDescriptor descriptor = resolveDescriptor(
                ServiceLoader.load(PimCatalogDescriptor.class).stream().map(ServiceLoader.Provider::get).toList());

        Map<String, String> configuration = new HashMap<>();
        if (secretsManager.exists(descriptor.name())) {
            configuration.putAll(secretsManager.getSecret(descriptor.name(), Map.class));
        }

        this.catalog = descriptor.create(configuration, Map.of("sqsAsyncClient", sqsAsyncClient));
        this.storeIds = () -> storesRepository.findAll().stream().map(Store::getStoreId).toList();
        this.codeHistory = new StoreEntryCodeHistory(Clock.systemUTC());

        registerEntryHandlers(productRepository, productCatalogRepository);

        catalog.onCategoryMatched(taxonomyCategoryEnrichment::applyMatch);

        EventBindingRegistrar.forDescriptors(List.of(descriptor))
                .withQueues(sqsAsyncClient, containers, catalog::dispatch)
                .register();
    }

    private void registerEntryHandlers(ProductRepository productRepository,
                                       ProductCatalogRepository productCatalogRepository) {
        StoreEntryPinning storeEntryPinning = new StoreEntryPinning(productRepository, productCatalogRepository, codeHistory);
        catalog.onEntryAdded(event -> onEntryAdded(event, storeEntryPinning, productRepository, productCatalogRepository));
        catalog.onEntryDeleted(event -> onEntryDeleted(event, storeEntryPinning));
    }

    private void onEntryAdded(PIMEntryAddedEvent event, StoreEntryPinning storeEntryPinning,
                              ProductRepository productRepository, ProductCatalogRepository productCatalogRepository) {
        if (event.ownerStoreId() == null) {
            attachEntryToProducts(event, productRepository);
        } else if (event.clonedFromPimId() != null && !event.clonedFromPimId().isBlank()) {
            // the delayed announcement of a new clone refreshes the copy itself, synchronously, so no background
            // refresh follows; the codes of the store entries go to the history first, as before every refresh, so a
            // later code-change event still finds what it removed
            rememberStoreEntryCodes();
            storeEntryPinning.onCloneAnnounced(event, catalog);
            return;
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
                rememberStoreEntryCodes();
                try {
                    catalog.refresh();
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted() || refreshExecutor.isShutdown()) {
                        // shutdownNow() on deploy interrupts a running refresh; that is not a failure
                        log.info("PIM index refresh interrupted by shutdown: {}", e.toString());
                    } else {
                        log.error("PIM index refresh failed", e);
                    }
                }
            });
        } catch (RuntimeException e) {
            // executor already shut down
            refreshPending.set(false);
            log.warn("PIM index refresh not scheduled: {}", e.getMessage());
        }
    }

    // before the copy is replaced: an event handled after this refresh still finds the codes its edit removed
    private void rememberStoreEntryCodes() {
        try {
            for (String storeId : storeIds.get()) {
                if (storeId == null || storeId.isBlank()) {
                    continue;
                }
                codeHistory.remember(catalog.withStoreData(storeId).findAll().stream()
                        .filter(entry -> storeId.equals(entry.ownerStoreId()))
                        .toList());
            }
        } catch (RuntimeException e) {
            // without the history a removed code may leave a product pinned, as before; the refresh itself must run
            log.warn("Codes of store entries not recorded before the PIM index refresh: {}", e.toString());
        }
    }

    /** Global entries only: a store's own entries are pinned by {@link StoreEntryPinning}. */
    static void attachEntryToProducts(PIMEntryAddedEvent event, ProductRepository productRepository) {
        productRepository.findAllWithoutPimIdByEanOrMfn(event.eans(), event.mfnCodes()).forEach(product -> {
            product.setPimId(event.pimId());
            productRepository.save(product);
        });
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

    // every instance refreshes on its own: an SQS event reaches only one of the app instances. A fixed delay with a
    // random first start (1-5 minutes) keeps the instances from refreshing in the same second.
    @Scheduled(fixedDelay = 300_000,
            initialDelayString = "#{T(java.util.concurrent.ThreadLocalRandom).current().nextLong(60000, 300000)}")
    void refresh() {
        requestRefresh();
    }
}
