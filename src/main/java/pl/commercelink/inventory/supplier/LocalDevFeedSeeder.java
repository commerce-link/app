package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.starter.secrets.SecretsManager;

@Component
@Profile("localdev")
class LocalDevFeedSeeder {

    private static final String QUEUE_NAME = "supplier-feed-import-queue";

    private final SupplierRegistry supplierRegistry;
    private final InventoryRepository inventoryRepository;
    private final SqsTemplate sqsTemplate;
    private final SecretsManager secretsManager;

    LocalDevFeedSeeder(SupplierRegistry supplierRegistry, InventoryRepository inventoryRepository,
                       SqsTemplate sqsTemplate, SecretsManager secretsManager) {
        this.supplierRegistry = supplierRegistry;
        this.inventoryRepository = inventoryRepository;
        this.sqsTemplate = sqsTemplate;
        this.secretsManager = secretsManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedMissingFeeds() {
        for (SupplierDescriptor descriptor : supplierRegistry.getAllDescriptors()) {
            String name = descriptor.supplierInfo().name();
            if (!inventoryRepository.canRead(name)) {
                ensureSecretExists(name);
                System.out.println("Seeding feed for supplier: " + name);
                sqsTemplate.send(to -> to
                        .queue(QUEUE_NAME)
                        .payload(new SqsFeedLoaderEventListener.FeedLoaderEventPayload(name))
                );
            }
        }
    }

    private void ensureSecretExists(String supplierName) {
        if (!secretsManager.exists(supplierName)) {
            secretsManager.createSecret(supplierName, "localdev");
        }
    }
}
