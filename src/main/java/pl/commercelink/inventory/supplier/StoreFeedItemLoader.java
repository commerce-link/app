package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StoreFeedItemLoader {

    private final CsvProductFeedLoader csvProductFeedLoader;
    private final XmlProductFeedLoader xmlProductFeedLoader;
    private final int taxonomyPenalty;

    StoreFeedItemLoader(CsvProductFeedLoader csvProductFeedLoader, XmlProductFeedLoader xmlProductFeedLoader,
                        @Value("${inventory.store-feed.taxonomy-penalty}") int taxonomyPenalty) {
        this.csvProductFeedLoader = csvProductFeedLoader;
        this.xmlProductFeedLoader = xmlProductFeedLoader;
        this.taxonomyPenalty = taxonomyPenalty;
    }

    public List<InventoryItem> load(String storeId, SupplierProviderDescriptor descriptor, Map<String, Double> sellRates) {
        String identity = descriptor.supplierInfo().name();
        List<InventoryItem> items = switch (descriptor.feedFormat()) {
            case FeedFormat.Csv csv ->
                    csvProductFeedLoader.fetch(csv.parser(), csv.separator(), storeId, identity, taxonomyPenalty);
            case FeedFormat.Xml xml ->
                    xmlProductFeedLoader.load(xml.itemClass(), xml.itemElementName(), descriptor.supplierInfo(), storeId, taxonomyPenalty);
        };

        return items.stream()
                .map(item -> stampedWith(item, identity))
                .flatMap(item -> item.toLocalCurrency(ExchangeRates.LOCAL_CURRENCY, sellRates.get(item.currency())).stream())
                .collect(Collectors.toList());
    }

    // Adapter CSV parsers stamp rows with the type name they were compiled with; a connection may
    // be one of several instances of that type, so the identity of the connection must replace it.
    private static InventoryItem stampedWith(InventoryItem item, String identity) {
        if (identity.equals(item.supplier())) {
            return item;
        }
        return new InventoryItem(item.ean(), item.mfn(), item.netPrice(), item.currency(), item.qty(),
                item.leadTimeDays(), identity, item.sellable(), item.inStock(), item.inDelivery(), item.sku());
    }
}
