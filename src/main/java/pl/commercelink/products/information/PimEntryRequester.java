package pl.commercelink.products.information;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntryRequest;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.Product;
import pl.commercelink.products.brand.BrandMapper;

import java.util.Collection;

/**
 * Asks the PIM for a product's entry right after the product is saved in a catalog, instead of
 * waiting for the periodic {@link PIMIndexEventScheduler} sweep, which stays the safety net for
 * anything missed here. Requests go out with the highest priority, as for a managed catalog.
 * <p>
 * A product the PIM already knows by manufacturer code and brand is skipped: a request would change
 * nothing, because {@code PimCatalogRegistry.attachEntryToProducts} pins entries to products by EAN only.
 * Does nothing outside {@code application.env=prod}. A failure never reaches the caller.
 */
@Component
@Slf4j
public class PimEntryRequester {

    private final PimCatalog pimCatalog;
    private final BrandMapper brandMapper;
    private final String env;

    public PimEntryRequester(PimCatalog pimCatalog, BrandMapper brandMapper, @Value("${application.env:}") String env) {
        this.pimCatalog = pimCatalog;
        this.brandMapper = brandMapper;
        this.env = env;
    }

    public void requestMissingEntries(String storeId, Collection<Product> products) {
        products.forEach(product -> requestMissingEntry(storeId, product));
    }

    public void requestMissingEntry(String storeId, Product product) {
        if (!"prod".equals(env)) {
            return;
        }
        if (StringUtils.isNotBlank(product.getPimId()) || StringUtils.isBlank(product.getEan())) {
            return;
        }
        try {
            PimView pim = pimCatalog.withStoreData(storeId);
            if (isKnown(pim, brandMapper, product)) {
                return;
            }
            pimCatalog.submit(new PimEntryRequest(product.getEan(), product.getManufacturerCode(),
                    product.getBrand(), PimQueuePriorityCalculator.MAX_VALUE));
        } catch (RuntimeException e) {
            log.warn("Could not request PIM entry for store {}, category {}, EAN {}",
                    storeId, product.getCategoryId(), product.getEan(), e);
        }
    }

    static boolean isKnown(PimView pim, BrandMapper brandMapper, Product product) {
        if (StringUtils.isBlank(product.getBrand())) {
            return pim.findByGtin(product.getEan()).isPresent();
        }
        // pim-api compares brands without aliases, and PIM stores the unified name
        return pim.findByGtinOrMpnAndBrand(product.getEan(), product.getManufacturerCode(),
                brandMapper.unifyBrand(product.getBrand())).isPresent();
    }
}
