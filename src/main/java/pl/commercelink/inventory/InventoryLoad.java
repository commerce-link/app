package pl.commercelink.inventory;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.CsvProductFeedLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.XmlProductFeedLoader;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.FeedFormat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class InventoryLoad {

    private final Inventory inventory;
    private final SupplierRegistry supplierRegistry;
    private final CsvProductFeedLoader csvProductFeedLoader;
    private final XmlProductFeedLoader xmlProductFeedLoader;

    InventoryLoad(Inventory inventory, SupplierRegistry supplierRegistry,
                  CsvProductFeedLoader csvProductFeedLoader, XmlProductFeedLoader xmlProductFeedLoader) {
        this.inventory = inventory;
        this.supplierRegistry = supplierRegistry;
        this.csvProductFeedLoader = csvProductFeedLoader;
        this.xmlProductFeedLoader = xmlProductFeedLoader;
    }

    @PostConstruct
    void onStartUp() {
        Map<String, Double> sellRates = new ExchangeRates().getCurrentSellRates();
        List<List<InventoryItem>> rawFeeds = supplierRegistry.getAllDescriptors().stream()
                .map(this::fetchItems)
                .map(items -> items.stream()
                        .flatMap(item -> item.toLocalCurrency("PLN", sellRates.get(item.currency())).stream())
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        inventory.init(rawFeeds);
    }

    private List<InventoryItem> fetchItems(SupplierDescriptor supplierDescriptor) {
        return switch (supplierDescriptor.feedFormat()) {
            case FeedFormat.Csv csv -> csvProductFeedLoader.fetch(csv.parser(), csv.separator(), supplierDescriptor.supplierInfo().name());
            case FeedFormat.Xml xml -> xmlProductFeedLoader.load(xml.itemClass(), xml.itemElementName(), supplierDescriptor.supplierInfo());
        };
    }

}
