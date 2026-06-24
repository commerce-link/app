package pl.commercelink.inventory;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.CsvProductFeedLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.XmlProductFeedLoader;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.FeedFormat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class InventoryLoad {

    private final Inventory inventory;
    private final SupplierProviderFactory supplierProviderFactory;
    private final CsvProductFeedLoader csvProductFeedLoader;
    private final XmlProductFeedLoader xmlProductFeedLoader;
    private final ExchangeRates exchangeRates;

    @PostConstruct
    void onStartUp() {
        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        List<List<InventoryItem>> rawFeeds = supplierProviderFactory.availableProviders().stream()
                .map(this::fetchItems)
                .map(items -> items.stream()
                        .flatMap(item -> item.toLocalCurrency(ExchangeRates.LOCAL_CURRENCY, sellRates.get(item.currency())).stream())
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
