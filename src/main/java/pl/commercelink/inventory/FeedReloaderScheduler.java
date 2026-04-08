package pl.commercelink.inventory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.CsvProductFeedLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.XmlProductFeedLoader;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.FeedFormat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class FeedReloaderScheduler {

    private final Inventory inventory;
    private final InventoryRepository inventoryRepository;
    private final SupplierRegistry supplierRegistry;
    private final CsvProductFeedLoader csvProductFeedLoader;
    private final XmlProductFeedLoader xmlProductFeedLoader;

    public FeedReloaderScheduler(Inventory inventory, InventoryRepository inventoryRepository, SupplierRegistry supplierRegistry,
                                 CsvProductFeedLoader csvProductFeedLoader, XmlProductFeedLoader xmlProductFeedLoader) {
        this.inventory = inventory;
        this.inventoryRepository = inventoryRepository;
        this.supplierRegistry = supplierRegistry;
        this.csvProductFeedLoader = csvProductFeedLoader;
        this.xmlProductFeedLoader = xmlProductFeedLoader;
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void reloadIfNewFeedsAvailable() {
        Map<String, LocalDateTime> latestModifiedPerSupplier = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        latestModifiedPerSupplier.putAll(inventoryRepository.getLatestModifiedPerSupplier());
        Map<String, List<InventoryItem>> updatesBySupplier = new LinkedHashMap<>();
        Map<String, Double> sellRates = new ExchangeRates().getCurrentSellRates();

        for (SupplierDescriptor supplierDescriptor : supplierRegistry.getAllDescriptors()) {
            String supplierName = supplierDescriptor.supplierInfo().name();
            LocalDateTime lastUpdateDate = inventory.getLastUpdateDate(supplierName);
            LocalDateTime lastModified = latestModifiedPerSupplier.get(supplierName);

            if (lastModified != null && lastUpdateDate.isBefore(lastModified)) {
                List<InventoryItem> items = fetchItems(supplierDescriptor).stream()
                        .flatMap(item -> item.toLocalCurrency("PLN", sellRates.get(item.currency())).stream())
                        .collect(Collectors.toList());
                updatesBySupplier.put(supplierName, items);
            }
        }

        if (!updatesBySupplier.isEmpty()) {
            inventory.update(updatesBySupplier);
        }
    }

    private List<InventoryItem> fetchItems(SupplierDescriptor supplierDescriptor) {
        return switch (supplierDescriptor.feedFormat()) {
            case FeedFormat.Csv csv -> csvProductFeedLoader.fetch(csv.parser(), csv.separator(), supplierDescriptor.supplierInfo().name());
            case FeedFormat.Xml xml -> xmlProductFeedLoader.load(xml.itemClass(), xml.itemElementName(), supplierDescriptor.supplierInfo());
        };
    }

}
