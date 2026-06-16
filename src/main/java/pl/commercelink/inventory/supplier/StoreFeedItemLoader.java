package pl.commercelink.inventory.supplier;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierDescriptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StoreFeedItemLoader {

    private final CsvProductFeedLoader csvProductFeedLoader;
    private final XmlProductFeedLoader xmlProductFeedLoader;

    public List<InventoryItem> load(String storeId, SupplierDescriptor descriptor, Map<String, Double> sellRates) {
        List<InventoryItem> items = switch (descriptor.feedFormat()) {
            case FeedFormat.Csv csv ->
                    csvProductFeedLoader.fetch(csv.parser(), csv.separator(), storeId, descriptor.supplierInfo().name());
            case FeedFormat.Xml xml ->
                    xmlProductFeedLoader.load(xml.itemClass(), xml.itemElementName(), descriptor.supplierInfo(), storeId);
        };

        return items.stream()
                .flatMap(item -> item.toLocalCurrency(ExchangeRates.LOCAL_CURRENCY, sellRates.get(item.currency())).stream())
                .collect(Collectors.toList());
    }
}
