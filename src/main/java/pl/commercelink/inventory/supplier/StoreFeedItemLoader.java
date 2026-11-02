package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.api.FeedFormat;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
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

    // identity is the connection's stored identity (e.g. Kosatec-k7f3a9c2); the descriptor is the
    // adapter type's own and keeps reporting the type name, which never reaches the loaded items.
    public List<InventoryItem> load(String storeId, String identity, SupplierProviderDescriptor descriptor,
                                    Map<String, Double> sellRates) {
        SupplierInfo supplierInfo = descriptor.supplierInfo().withName(identity);
        List<InventoryItem> items = switch (descriptor.feedFormat()) {
            case FeedFormat.Csv csv ->
                    csvProductFeedLoader.fetch(csv.parser(), csv.separator(), storeId, supplierInfo, taxonomyPenalty);
            case FeedFormat.Xml xml ->
                    xmlProductFeedLoader.load(xml.itemClass(), xml.itemElementName(), supplierInfo, storeId, taxonomyPenalty);
        };

        return items.stream()
                .flatMap(item -> item.toLocalCurrency(ExchangeRates.LOCAL_CURRENCY, sellRates.get(item.currency())).stream())
                .collect(Collectors.toList());
    }
}
