package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.MarketplaceOfferExportRequest;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

@Component
class PricelistEventPublisher {

    @Value("${application.env}")
    private String env;

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(String storeId, String catalogId, String pricelistId) {
        if (!env.equals("prod")) {
            return;
        }

        Store store = storesRepository.findById(storeId);
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);

        for (MarketplaceIntegration marketplace : store.getMarketplaces()) {
            if (catalog.isMarketplaceExportEnabled(marketplace.getName())) {
                MarketplaceOfferExportRequest exportRequest = new MarketplaceOfferExportRequest(
                        marketplace.getName(),
                        storeId,
                        catalogId,
                        pricelistId
                );
                sqsTemplate.send("marketplace-offer-export-queue", exportRequest);
            }
        }
    }

}
